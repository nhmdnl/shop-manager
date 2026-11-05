# Staff PINs and roles: design

Date 2026-11-05. Status: design approved in chat by the user; written spec awaiting their review.
Branch `staff-roles` (from `develop` 6fffbb7).

## Goal and success

The owner can see who made each sale and void, and a cashier cannot do owner-only actions.
Success:

1. Every sale and every void records the signed-in staff member.
2. Protected actions need an Owner.
3. A sale stays at 3 taps on a phone and 2 on a tablet after sign-in (checklist 15).

## Decisions taken with the user

| # | Decision |
|---|---|
| D1 | A cashier signs in once per shift with their own PIN. Sales are attributed to them until sign-out. |
| D2 | Sign-out after an idle timeout (default 10 minutes, a Setting) or when someone taps Switch user. |
| D3 | First version ships **Owner** and **Cashier**. Manager, then finance and other departments, come later. Roles are rows of a code map, so adding one needs no migration. |
| D4 | Existing shops are unchanged on update. Staff stays off until the owner taps **Set up staff** in Settings. The current shared PIN then becomes the first Owner PIN. |
| D5 | App only. Staff records and who-did-what are not synced in this version. No PIN hash ever leaves the tablet. Cloud sync of staff (contract v1.2, server mirror, owner "staff activity" page WEB-16) is a follow-up. |
| D6 | A forgotten Owner PIN is recovered with a one-time 8-character recovery code, shown once at setup. If both are lost, the only way back is wiping the app data and restoring a backup. |

## Data (database v3)

- New table `staff`: `id`, `uid` (sync-ready, filled as the other tables fill theirs), `name`, `role` (text key: `OWNER`, `CASHIER`), `pinHash`, `active`, `createdAt`.
- `sale` gains `staffId` (nullable; null for sales made before staff was on or with staff off) and `voidedByStaffId` (nullable).
- Staff are never deleted, only deactivated (project rule: nothing deleted). A deactivated person keeps their name on old sales.
- Settings (DataStore): `staffEnabled`, `recoveryHash`, `idleTimeoutMinutes`.
- Migration 2 to 3 adds the table and columns only. It writes no staff rows. The sync triggers and outbox treat `staff` as a table that does not sync yet (no trigger), so `sale` rows that now carry `staffId` still upload without it: the wire shape is unchanged.

## Roles and permissions

`enum Role { OWNER, CASHIER }` stored by key, and `enum Permission`, with a `Role.permissions` map in code.

| Permission | Owner | Cashier |
|---|---|---|
| Sell, take customer payments, view today's sales | yes | yes |
| Void a sale | yes | no |
| Close or reopen the day | yes | no |
| Correct stock | yes | no |
| Settings, backup restore, Cloud | yes | no |
| Manage staff | yes | no |

An unknown role key read from the database gets no permissions.

The existing protected actions (`pin_for_reopen`, `_cloud`, `_stock`, `_close`, `_void`, `_settings`, restore) each map to one permission.

## Behavior

- **Setup:** Settings, Set up staff. The owner confirms the current shared PIN (or sets one if none). It becomes the Owner account; the app asks for a name. The recovery code is shown once with a "I wrote it down" confirmation.
- **Sign-in screen:** list of active staff names, then a 4-digit PIN pad. Five wrong tries lock that person for 30 s (reuse `PinGuard`'s rule, per person).
- **`StaffSession`:** a singleton holding the current staff member and the last-activity time. Activity (any touch) resets the idle timer. It uses `Clock` so tests can drive time. When staff is off it reports no one and every permission check passes (today's behavior).
- **`PinGate` with staff on:**
  - Signed-in Owner: the action runs without a prompt.
  - Signed-in Cashier on an owner action: the app asks for any Owner's PIN, runs that one action, and the session stays the Cashier.
  - Staff off: unchanged (shared PIN if set).
- **Attribution:** `SaleRepo.createSale` and the void path read the signed-in id from `StaffSession` inside their existing transactions. The 6-second Undo after a sale still skips the PIN, as today.
- **Staff screen (Owner only):** add a person, rename, deactivate or reactivate, reset a cashier's PIN. An Owner's PIN is changed by that Owner or through the recovery code. The last active Owner cannot be deactivated.
- **Recovery:** on the sign-in screen, "Forgot the Owner PIN" asks for the recovery code, then a new PIN, then a new recovery code is shown. The code is stored as a PBKDF2 hash, and five wrong tries lock it for 30 s.
- **Turning staff off:** Settings, Owner only. Existing `staffId` values stay on old sales.
- **PIN hashing:** new staff PINs and the recovery code use PBKDF2 with a per-record salt. The old shared PIN converts when staff is turned on (verified with the old SHA-256 rule, then stored as PBKDF2).
- **Backups:** the staff table and settings are in the backup zip, so a restore keeps staff.
- **Strings:** all user-facing text from `strings.xml`; English now, translation sheets pick the new strings up through the existing export tool.

## Out of scope

Cloud sync of staff; a Manager role; attribution for stock corrections, money entries and purchases; per-role custom permissions; biometric sign-in.

## Testing

- JVM, in-memory Room: migration 2 to 3 keeps all data and adds no staff; `StaffRepo` (add, rename, deactivate, last-Owner guard); `createSale` and void record `staffId` and `voidedByStaffId`; old sales read with null.
- Permission map: every protected purpose maps to a permission; unknown role has none.
- `StaffSession` on a fake clock: idle timeout, activity reset, manual switch.
- PIN: lockout per person, PBKDF2 round trip, conversion of the old shared PIN, recovery code flow.
- `PinGate` with staff on and off.
- Sync: a sale with `staffId` still produces the same wire JSON as before.
- androidTest: `SaleTapCountTest` still passes at 2 taps (tablet) and 3 (phone) with a signed-in cashier; sign-in screen at 200% font.

## Risks to watch

1. The tap count: sign-in must happen before the Sell screen, never inside the sale flow.
2. The sync triggers: the migration must not make `staff` or the new `sale` columns break `SyncRepo`'s wire reads (covered by the sync test above).
3. Lock-out of an owner who lost both PIN and code: documented, not solved.
