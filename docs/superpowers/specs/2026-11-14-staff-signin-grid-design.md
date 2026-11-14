# Staff sign-in on one screen: design

Date 2026-11-14. Status: approved by the user 2026-11-14 (approach A).
Branch `signin-grid` (from `develop` 2d92120).

## Why

The "Who is working?" screen looks too small and too bare on a tablet: names sit in a 360 dp column in the middle of
a 1280 dp screen, and the PIN pad is a second screen. The user wants it clear and quick: pick your name, type your PIN.

## Goals

1. Names and the PIN pad on one screen: tap a name, type 4 digits, signed in. No page change.
2. Easy to read at a glance on the shop tablet: big name tiles, big keys.
3. A returning person types only their PIN.

Non-goals: new roles (Manager, Finance: a separate project after this), the "Owner PIN needed" dialog a Cashier sees
on owner actions, the recovery flow's own dialogs, how PINs are stored or checked.

Anti-goals: a sign-in that takes more taps than today (5), or a layout that cuts text at 200% font size.

## Approaches

| | Approach | Trade-off |
|---|---|---|
| **A (chosen)** | Grid of name tiles and the PIN pad side by side on one screen | 5 taps, or 4 when the last person is already picked. Per-person lockout and non-unique PINs stay as they are |
| B | PIN only; the app finds who it belongs to | 4 taps, but every PIN must be unique, a guess can hit any of N people, and a lockout would lock out everyone |
| C | Keep two screens, just make them bigger | Least work, but still a page change between name and PIN |

## Design

**Tablet (landscape, the main case):** left, the title "Who is working?" over a grid of person tiles; right, the PIN
pad for the picked person (their name above the dots). "Forgot the Owner PIN" is a text button under the grid.

```
 Who is working?
 ┌──────┐ ┌──────┐ ┌──────┐      Boss
 │  B   │ │  C   │ │  S   │      ● ● ○ ○
 │ Boss │ │ Cash │ │ Sam  │     [1][2][3]
 │Owner │ │Cashr │ │Cashr │     [4][5][6]
 └──────┘ └──────┘ └──────┘     [7][8][9]
 Forgot the Owner PIN             [0][⌫]
```

- A tile shows an initial in a circle, the name, and the role (`staff_role_owner` / `staff_role_cashier`).
  The picked tile has the green tint and border; the others are plain.
- Before anyone is picked, the pad area says "Tap your name" and its keys are disabled.
- Picking another person clears any typed digits and any error message.
- The 4th digit signs in at once, as today. A wrong PIN shows the existing message (tries left, or locked for 30 s)
  under the dots in rust, and the dots clear.
- Many people: the grid scrolls, the pad stays put.

**Phone (portrait):** the same screen stacked: tiles in a 2-column grid on top, the pad below. The grid area scrolls
when there are many people, so the pad stays on screen.

**Remember the last person:** the person who last signed in successfully on this device is picked when the screen
opens, if they are still active. Stored as their id in DataStore (not sensitive: no PIN, no name).

**Text and sizes:** names at least 20 sp, keys at least 64 dp, every target at least 48 dp. Tiles hold text with the
design system's `fontScaled()` rules, so 200% font size wraps instead of cutting. All new text in `strings.xml`.

## My calls (the user can override)

- Remember the last person (goal 3) and store it in DataStore so it survives a restart.
- Phone layout stacked on one screen rather than two steps.
- Tiles follow the look of the Sell product tiles, so the app feels like one thing.

## Left to the builder

- Grid column count per width (about 3 on a tablet's left half, 2 on a phone).
- Whether the pad reuses `PinPad` as is or gets a larger-key variant.
- Updating `StaffSignInTest` (device) for the one-screen flow, plus a test that the last person is picked on open.
