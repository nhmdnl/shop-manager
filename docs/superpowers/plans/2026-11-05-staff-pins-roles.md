# Staff PINs and Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Per-person PINs with Owner and Cashier roles, so every sale and void records who did it and owner-only actions need an Owner.

**Architecture:** A `staff` table (database v3) plus `sale.staffId` / `sale.voidedByStaffId`. Roles are a code map (`Role` to `Permission` set). A singleton `StaffSession` holds the signed-in person with an idle timeout; `PinGate` keeps its `run(purpose, action)` signature and decides by role, so the seven existing call sites do not change. App only: nothing about staff syncs.

**Tech Stack:** Kotlin, Compose, Hilt, Room via KSP, DataStore, JUnit + Robolectric (JVM), androidTest on the VM.

**Spec:** `docs/superpowers/specs/2026-11-05-staff-pins-roles-design.md` (read it first; the decisions D1 to D6 there are fixed).

## Global Constraints

- Package `com.archsolutions.shopmanager`; minSdk 26; Room through KSP (no kapt); Hilt for injection.
- Money is `Long` UGX. A write that touches more than one table runs in one Room `@Transaction` (`db.withTransaction`).
- All user-facing text comes from `strings.xml` (no hard-coded text in composables); every new string gets an English entry only, the translation export tool picks it up.
- Touch targets at least 48 dp; one green primary action per screen; 200% font must not clip (use the design system's `fontScaled()` / `CappedFontScale`).
- Nothing is deleted: staff are deactivated, never removed.
- A sale takes 3 taps on a phone and 2 on a tablet (`SaleTapCountTest`); sign-in happens before the Sell screen, never inside the sale flow.
- PIN length stays `Pin.LENGTH` = 4 digits, held as a `String` (leading zeros are valid). Lockout stays 5 tries then 30 s (`Pin.MAX_TRIES`, `Pin.LOCKOUT_MILLIS`).
- Staff data and PIN hashes never enter the sync wire format: `staff` gets no sync trigger and `SaleWire` is unchanged.
- Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Nothing is pushed.

## Review Focus

1. Idle timeout fires while a cart has items: the cart survives (it lives in `SavedStateHandle`) and the sale is credited to whoever signs in next.
2. App restart with staff on: nobody is signed in; the sign-in screen shows first.
3. The last active Owner cannot be deactivated; a blank name is rejected; two people may share a name.
4. Restoring a v2 backup (no `staff` table, `staffEnabled` unset) into a v3 app migrates and leaves staff off.
5. A cashier who gets an Owner's PIN approval to void: `voidedByStaffId` records the approving Owner, not the cashier. A person deactivated while signed in is signed out at once.

---

### Task 1: Staff table, sale columns, database v3

**Files:**
- Create: `app/src/main/java/com/archsolutions/shopmanager/domain/staff/Permissions.kt`
- Create: `app/src/main/java/com/archsolutions/shopmanager/data/db/entities/StaffEntity.kt`
- Create: `app/src/main/java/com/archsolutions/shopmanager/data/db/dao/StaffDao.kt`
- Modify: `data/db/entities/Entities.kt` (`SaleEntity`: add `staffId: Long? = null`, `voidedByStaffId: Long? = null` after `uid`)
- Modify: `data/db/AppDatabase.kt` (add `StaffEntity`, `staffDao()`, `VERSION = 3`, `addMigrations(MIGRATION_1_2, MIGRATION_2_3)`, a Hilt `provideStaffDao`)
- Modify: `data/db/Migrations.kt` (add `MIGRATION_2_3`)
- Test: `app/src/test/java/com/archsolutions/shopmanager/data/db/Migration2To3Test.kt` (copy the harness of `Migration1To2Test`)

**Interfaces:**
- Produces: `enum class Role(val key: String) { OWNER("OWNER"), CASHIER("CASHIER") }` with `companion fun fromKey(key: String): Role?`; `enum class Permission { SELL, VOID_SALE, CLOSE_OR_REOPEN_DAY, CORRECT_STOCK, ADMIN_SETTINGS, MANAGE_STAFF }`; `fun Role?.can(p: Permission): Boolean` (null and unknown roles can nothing; OWNER can all; CASHIER only `SELL`). `StaffEntity(id: Long = 0, uid: String = UUID, name: String, role: String, pinHash: String, active: Boolean = true, createdAt: Long)`, table `staff`, unique index on `uid`. `StaffDao`: `fun getAll(): Flow<List<StaffEntity>>`, `suspend fun getByIdDirect(id: Long): StaffEntity?`, `suspend fun insert(s: StaffEntity): Long`, `suspend fun update(s: StaffEntity)`, `suspend fun activeOwnerCount(): Int`, `suspend fun activeDirect(): List<StaffEntity>`.

- [ ] **Step 1: Write the failing tests.** In `Migration2To3Test`: `migrating v2 to v3 keeps every row, adds an empty staff table and null staff columns` (create v2 with `helper.createDatabase`, insert a sale, run `MIGRATION_2_3`, assert the sale row survives, `SELECT COUNT(*) FROM staff` = 0, `staffId IS NULL` and `voidedByStaffId IS NULL`, and the 24 `sync_%` triggers still exist). In a new `PermissionsTest` (JVM): `owner can do everything`, `cashier can only sell`, `unknown or null role can do nothing`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*Migration2To3Test" --tests "*PermissionsTest"`; expect compile failure.
- [ ] **Step 3: Implement** the files above. `MIGRATION_2_3` runs `CREATE TABLE staff (...)` matching the entity exactly, `CREATE UNIQUE INDEX index_staff_uid`, and two `ALTER TABLE sale ADD COLUMN ... INTEGER` statements. Build once so Room exports `app/schemas/.../3.json`.
- [ ] **Step 4: Run** the two test classes plus `./gradlew testDebugUnitTest`; expect all green (276 existing plus the new ones).
- [ ] **Step 5: Commit** `git add -A app docs && git commit -m "Database v3: staff table and sale staff columns"`.

### Task 2: PIN hashing, recovery code

**Files:**
- Create: `core/security/PinHasher.kt`, `core/security/RecoveryCode.kt`
- Test: `app/src/test/java/com/archsolutions/shopmanager/core/security/PinHasherTest.kt`, `RecoveryCodeTest.kt`

**Interfaces:**
- Produces: `object PinHasher { fun hash(pin: String, iterations: Int = ITERATIONS): String; fun matches(pin: String, stored: String): Boolean; const val ITERATIONS = 120_000 }` storing `pbkdf2$<iterations>$<saltHex>$<hashHex>` (PBKDF2WithHmacSHA256, 16-byte salt, 256-bit key, constant-time compare; an unparsable `stored` returns false). `object RecoveryCode { fun generate(random: SecureRandom = SecureRandom()): String; fun normalize(input: String): String }`: 8 characters from the alphabet `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, shown as `XXXX-XXXX`; `normalize` upper-cases and drops spaces and dashes.
- Consumes: the legacy `Pin.matches(pin, stored)` stays as is for the old shared PIN.

- [ ] **Step 1: Write the failing tests:** `hash then matches round trips` (use `iterations = 1_000`), `wrong pin does not match`, `two hashes of one pin differ (salt)`, `garbage stored value is rejected`, `pin with leading zeros 0042 round trips`, `generate gives 8 chars from the alphabet, none ambiguous (0 O 1 I)`, `normalize accepts lower case and dashes`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*PinHasherTest" --tests "*RecoveryCodeTest"`; expect compile failure.
- [ ] **Step 3: Implement** both objects with `javax.crypto.SecretKeyFactory` (available on API 26).
- [ ] **Step 4: Run** the same command; expect PASS.
- [ ] **Step 5: Commit** `"Staff: PBKDF2 PIN hashing and recovery codes"`.

### Task 3: Settings fields and `StaffRepo`

**Files:**
- Modify: `data/settings/SettingsStore.kt` (`ShopSettings` gains `staffEnabled: Boolean = false`, `recoveryHash: String = ""`, `idleTimeoutMinutes: Int = 10`; setters `setStaffEnabled(Boolean)`, `setRecoveryHash(String)`, `setIdleTimeoutMinutes(Int)`; keys `staff_enabled`, `recovery_hash`, `idle_timeout_minutes`)
- Create: `data/repo/StaffRepo.kt`
- Test: `app/src/test/java/com/archsolutions/shopmanager/data/repo/StaffRepoTest.kt` (in-memory Room, like `SupplierRepoTest`)

**Interfaces:**
- Consumes: `StaffDao`, `SettingsStore`, `AppClock`, `PinHasher`, `RecoveryCode`, `Role` (Task 1 and 2).
- Produces `@Singleton class StaffRepo @Inject constructor(db: AppDatabase, staffDao: StaffDao, settings: SettingsStore, clock: AppClock)`:
  - `val staff: Flow<List<StaffEntity>>`
  - `suspend fun enableStaff(ownerName: String, ownerPin: String): String` returns the plain recovery code; creates the Owner row, stores `PinHasher.hash(ownerPin)`, stores the recovery hash, sets `staffEnabled`; one transaction; does not touch the legacy shared PIN setting.
  - `suspend fun disableStaff()`
  - `suspend fun addStaff(name: String, role: Role, pin: String): Long`
  - `suspend fun rename(id: Long, name: String)`
  - `suspend fun setActive(id: Long, active: Boolean): Boolean` returns false (no change) when it would deactivate the last active Owner
  - `suspend fun resetPin(id: Long, newPin: String)`
  - `suspend fun verify(id: Long, pin: String): Boolean` (false for an inactive person)
  - `suspend fun recoverOwner(ownerId: Long, code: String, newPin: String): String?` returns a new recovery code, or null when the code is wrong.
  - Blank names throw `IllegalArgumentException`; names are trimmed.

- [ ] **Step 1: Write the failing tests** in `StaffRepoTest`: `enableStaff creates one active owner and returns an 8-char code`, `enableStaff stores a pbkdf2 hash, never the plain pin`, `last active owner cannot be deactivated`, `a second owner can be deactivated`, `blank name is rejected`, `duplicate names are allowed`, `inactive person fails verify`, `recoverOwner with the right code sets the new pin and returns a fresh code (old code stops working)`, `recoverOwner with a wrong code returns null and changes nothing`, `disableStaff clears the flag and keeps the staff rows`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*StaffRepoTest"`; expect compile failure.
- [ ] **Step 3: Implement** the settings fields and `StaffRepo`. Rows are inserted with `role = role.key`.
- [ ] **Step 4: Run** the same command; expect PASS.
- [ ] **Step 5: Commit** `"Staff: settings fields and StaffRepo"`.

### Task 4: `StaffSession` and per-person lockout

**Files:**
- Create: `data/staff/StaffSession.kt`, `data/staff/StaffGuard.kt`
- Test: `app/src/test/java/com/archsolutions/shopmanager/data/staff/StaffSessionTest.kt`, `StaffGuardTest.kt`

**Interfaces:**
- Consumes: `StaffRepo`, `AppClock`, `Pin`, `PinResult` (from `data/settings/PinGuard.kt`).
- Produces `@Singleton class StaffSession @Inject constructor(clock: AppClock)`: `val current: StateFlow<StaffEntity?>`; `val currentId: Long?`; `fun signIn(staff: StaffEntity)`; `fun signOut()`; `fun touch()`; `fun checkIdle(timeoutMinutes: Int)` signs out when `now - lastTouch >= timeout`; `fun onDeactivated(staffId: Long)` signs out that person; `var approvedBy: Long?` and `val actingId: Long?` = `approvedBy ?: currentId` (read synchronously by the action that needs it).
- Produces `@Singleton class StaffGuard @Inject constructor(repo: StaffRepo, session: StaffSession, clock: AppClock)`: `suspend fun signIn(staffId: Long, pin: String): PinResult` (Ok signs the session in); `suspend fun approveAsOwner(pin: String): PinResult` (matches any active Owner; on Ok sets nothing, returns the approver through `lastApproverId: Long?`); `suspend fun recover(ownerId: Long, code: String, newPin: String): RecoveryOutcome` where `sealed interface RecoveryOutcome { data class Ok(val newCode: String); data object WrongCode; data class Locked(val waitMillis: Long) }`. Each person, the owner approval and recovery have separate counters: 5 failures lock that key for 30 s.

- [ ] **Step 1: Write the failing tests** (fake `AppClock`): `signIn with the right pin signs the session in`, `5 wrong pins lock that person for 30 s and another person is unaffected`, `lock expires after 30 s`, `idle for the timeout signs out; a touch before it resets the timer`, `onDeactivated signs out only that person`, `approveAsOwner accepts any active owner's pin and rejects a cashier's pin`, `actingId is approvedBy when set, else the current person`, `recover has its own counter`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*StaffSessionTest" --tests "*StaffGuardTest"`; expect compile failure.
- [ ] **Step 3: Implement** both classes. Failure state is in memory only (matches `PinGuard`).
- [ ] **Step 4: Run** the same command; expect PASS.
- [ ] **Step 5: Commit** `"Staff: session with idle timeout and per-person lockout"`.

### Task 5: `PinGate` decides by role

**Files:**
- Create: `domain/staff/GateDecision.kt`, `domain/staff/PinPurposes.kt`
- Modify: `feature/pin/PinDialog.kt` (`PinViewModel` gets `StaffSession`, `StaffGuard`, `SettingsStore`; `PinGate.run(@StringRes purpose, action)` keeps its signature; the dialog shows "Owner PIN needed" when asking for an Owner)
- Test: `app/src/test/java/com/archsolutions/shopmanager/domain/staff/GateDecisionTest.kt`, `PinPurposesTest.kt`

**Interfaces:**
- Produces: `enum class GateDecision { RUN, ASK_OWNER_PIN, ASK_SHARED_PIN }`; `fun decideGate(staffEnabled: Boolean, current: Role?, permission: Permission, sharedPinRequired: Boolean): GateDecision`. Rules: staff off gives `ASK_SHARED_PIN` when `sharedPinRequired` else `RUN`; staff on gives `RUN` when `current` can the permission, else `ASK_OWNER_PIN`. `fun permissionFor(@StringRes purpose: Int): Permission` for the seven `R.string.pin_for_*` purposes (reopen and close: `CLOSE_OR_REOPEN_DAY`; stock: `CORRECT_STOCK`; void: `VOID_SALE`; settings, restore and cloud: `ADMIN_SETTINGS`).
- Consumes: `StaffSession.approvedBy` is set to the approver's id just before the action runs and cleared right after it returns.

- [ ] **Step 1: Write the failing tests:** `decideGate` table: staff off with no shared PIN is `RUN`, staff off with one is `ASK_SHARED_PIN`, owner signed in is `RUN` for every permission, cashier is `RUN` for `SELL` and `ASK_OWNER_PIN` for the others, nobody signed in is `ASK_OWNER_PIN`. `PinPurposesTest`: every `R.string` field whose name starts with `pin_for_` (reflection over `R.string::class.java.fields`) has a mapping, and none maps to `SELL`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*GateDecisionTest" --tests "*PinPurposesTest"`; expect compile failure.
- [ ] **Step 3: Implement** the two domain files, then wire `PinViewModel` and `rememberPinGate`. The existing dialog flow for `ASK_SHARED_PIN` stays byte-for-byte; `ASK_OWNER_PIN` calls `StaffGuard.approveAsOwner`.
- [ ] **Step 4: Run** the two classes plus `./gradlew testDebugUnitTest`; expect all green.
- [ ] **Step 5: Commit** `"Staff: PinGate decides by role"`.

### Task 6: Attribution on sales and voids

**Files:**
- Modify: `data/repo/SaleRepo.kt` (`createSale(..., staffId: Long? = null)`, `voidSale(saleId, reason, staffId: Long? = null)`), `data/db/dao/SaleDao.kt` (`voidSale` also sets `voidedByStaffId`), `feature/sell/SellViewModel.kt:170,201`, `feature/sales/SaleDetailViewModel.kt:101` (inject `StaffSession`; read `staffSession.actingId` synchronously before launching), `feature/sales/SaleDetailScreen.kt` (a "Sold by NAME" line and, when voided, "Voided by NAME"), `res/values/strings.xml` (`sale_sold_by`, `sale_voided_by`)
- Test: extend `SaleTransactionTest`; create `app/src/test/java/com/archsolutions/shopmanager/data/cloud/sync/SaleWireUnchangedTest.kt`

**Interfaces:**
- Consumes: `StaffSession.actingId`, `StaffDao.getByIdDirect`.
- Produces: `SaleEntity.staffId` and `voidedByStaffId` filled; `SaleDetail` state exposes `soldBy: String?` and `voidedBy: String?` (null when the id is null or the person is missing).

- [ ] **Step 1: Write the failing tests:** `createSale records the staff id`, `createSale without staff leaves null`, `voidSale records voidedByStaffId and leaves staffId`, `old sale rows read back with null staff`, and `SaleWireUnchangedTest`: `a sale carrying staffId uploads the same wire JSON as one without` (call `SyncRepo.nextBatch` for both and compare the sale row's JSON keys: no `staff*` key).
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*SaleTransactionTest" --tests "*SaleWireUnchangedTest"`; expect failures.
- [ ] **Step 3: Implement** the repo, DAO, view-model and screen changes; the void actor is `actingId` so an Owner approval is credited to the Owner (Review Focus 5).
- [ ] **Step 4: Run** the two classes plus `./gradlew testDebugUnitTest`; expect all green.
- [ ] **Step 5: Commit** `"Staff: sales and voids record who did them"`.

### Task 7: Sign-in gate, idle sign-out, Switch user

**Files:**
- Create: `feature/staff/StaffSignInScreen.kt`, `feature/staff/StaffSignInViewModel.kt`
- Modify: `app/MainActivity.kt` (`dispatchTouchEvent` calls `staffSession.touch()`), `app/AppNavHost.kt` (show the sign-in screen over everything while `staffEnabled` and no one is signed in, except during onboarding; a 15 s ticker calls `checkIdle(settings.idleTimeoutMinutes)`; a deactivated person is signed out via `onDeactivated`), `feature/home` header (a "Signed in as NAME, Switch user" chip, 48 dp), strings
- Test: `app/src/androidTest/java/com/archsolutions/shopmanager/StaffSignInTest.kt`; extend `SaleTapCountTest`

**Interfaces:**
- Consumes: `StaffRepo.staff`, `StaffGuard.signIn`, `StaffSession`.
- Produces: `StaffSignInViewModel` with `val people: StateFlow<List<StaffEntity>>` (active only), `fun pick(id)`, `suspend fun enter(pin: String): PinResult`, state for lockout messages reusing `plurals_pin_tries_left` / `plurals_pin_wait`.

- [ ] **Step 1: Write the failing tests** (androidTest, in-memory DB, real `StaffSignInViewModel`): `sign in screen lists active staff only`, `right pin leaves the screen and the session holds the person`, `wrong pin shows tries left`, `signed-in cashier still sells in 2 taps on tablet and 3 on phone` (extend `SaleTapCountTest`), `sign in screen does not clip at 200% font`.
- [ ] **Step 2: Run** `./gradlew installDebug installDebugAndroidTest` then `adb shell am instrument -w -e class com.archsolutions.shopmanager.StaffSignInTest com.archsolutions.shopmanager.test/androidx.test.runner.AndroidJUnitRunner` with `ANDROID_SERIAL=127.0.0.1:5555`; expect failures. Never run `connectedDebugAndroidTest` (it uninstalls the app).
- [ ] **Step 3: Implement** the screen, view-model and shell wiring. Review Focus 1 and 2: the cart stays in `SavedStateHandle`; nothing persists the signed-in person across a process restart.
- [ ] **Step 4: Run** the instrument command for both classes; expect PASS.
- [ ] **Step 5: Commit** `"Staff: sign-in screen, idle sign-out, Switch user"`.

### Task 8: Setup, recovery and staff management screens

**Files:**
- Create: `feature/staff/StaffScreen.kt`, `feature/staff/StaffViewModel.kt`, `feature/staff/StaffSetupDialogs.kt`
- Modify: `feature/settings/SettingsScreen.kt` (a Staff section: Set up staff, Manage staff, Idle sign-out minutes, Turn off staff; all Owner-gated through `pinGate.run(R.string.pin_for_settings)`), `app/AppNavHost.kt` (a `StaffRoute`), `feature/staff/StaffSignInScreen.kt` ("Forgot the Owner PIN" opens the recovery flow), strings
- Test: `app/src/test/java/com/archsolutions/shopmanager/feature/staff/StaffViewModelTest.kt`; extend `StaffSignInTest`

**Interfaces:**
- Consumes: `StaffRepo`, `StaffGuard.recover`, `SetPinDialog` (existing), `PinGuard`, `StaffSession.onDeactivated`.
- Produces: `StaffViewModel` with `val people: StateFlow<List<StaffEntity>>`, `suspend fun setUp(ownerName: String, ownerPin: String): String` (returns the recovery code to show), `fun add(name, role, pin)`, `fun rename(id, name)`, `fun toggleActive(id): Boolean`, `fun resetPin(id, pin)`, `fun turnOff()`, `fun setIdleMinutes(m: Int)`.

- [ ] **Step 1: Write the failing tests:** `setUp with an existing shared PIN verifies it first (wrong PIN stops setup)`, `setUp shows the recovery code exactly once (a second read is not possible)`, `toggleActive refuses the last owner and reports it`, `deactivating the signed-in person signs them out at once`, `idle minutes accepts 1 to 60 and ignores others`, androidTest `forgot owner pin accepts the code, sets a new pin and shows a new code`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*StaffViewModelTest"`; expect compile failure.
- [ ] **Step 3: Implement** the view-model and screens. The setup flow: verify the shared PIN if one is set (else `SetPinDialog`), ask the Owner's name, call `setUp`, show the recovery code with an "I wrote it down" button that is the only way out.
- [ ] **Step 4: Run** the unit test, then the `StaffSignInTest` instrument command; expect PASS.
- [ ] **Step 5: Commit** `"Staff: setup, recovery and management screens"`.

### Task 9: Whole-branch verification

**Files:** none created; `docs/superpowers/plans/2026-11-05-staff-pins-roles.md` (tick the boxes)

- [ ] **Step 1:** `./gradlew testDebugUnitTest`; expect 0 failures.
- [ ] **Step 2:** `./gradlew assembleDebug assembleRelease`; expect both build (release signing as in CLAUDE.md).
- [ ] **Step 3:** On the VM (`ANDROID_SERIAL=127.0.0.1:5555`, keep data, always read the database with its `-wal` and `-shm` files): enable staff, add a cashier, sell as the cashier, try a void (Owner approval), wait past the idle timeout, restore a v2 backup (Review Focus 4). Record PASS or FAIL with evidence in `.superpowers/sdd/2026-11-05-staff-pins-roles/walkthrough.md`.
- [ ] **Step 4:** `hub log` the result; leave `docs/FEATURES.md` unchanged until a release ships it.
- [ ] **Step 5: Commit** any fixes; do not merge or push without the user's OK.
