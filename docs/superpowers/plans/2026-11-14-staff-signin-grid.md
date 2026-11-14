# Staff sign-in on one screen: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the staff name tiles and the PIN pad on one screen, with the last person to sign in already picked.

**Architecture:** `SettingsStore` remembers the id of the last person who signed in. `StaffSignInViewModel.picked` becomes
"the person tapped, else the remembered person if still active". `StaffSignInScreen` is rewritten as one screen: a grid
of `StaffTile`s plus the existing `PinPad` (two new optional parameters), side by side when the screen is at least
600 dp wide, stacked below that.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Hilt, DataStore Preferences, Robolectric JVM tests, Compose UI
device tests.

**Spec:** `docs/superpowers/specs/2026-11-14-staff-signin-grid-design.md`

## Global Constraints

- All user-facing text from `strings.xml`; no hard-coded text in composables. New strings in `values/strings.xml` only (English); translators fill the others later.
- Touch targets at least 48 dp. Names at least 20 sp. PIN keys at least 64 dp high on this screen.
- Fixed-size boxes that hold text use `fontScaled()`; nothing may cut or ellipsise text at 200% font size.
- One green primary action per screen: the picked tile uses `colors.greenTint` and a `colors.green` border, no green button.
- Colours only from `LocalShopColors.current`; rust (`colors.rust`) only for the wrong-PIN message.
- Build: `./gradlew assembleDebug`. JVM tests: `./gradlew testDebugUnitTest`. Device tests need the VM: `adb connect 127.0.0.1:5555`, then `./gradlew installDebugAndroidTest && adb shell am instrument -w -e class com.archsolutions.shopmanager.StaffSignInTest com.archsolutions.shopmanager.test/androidx.test.runner.AndroidJUnitRunner`. If the VM is locked, unlock it first (`adb shell input keyevent 224`, swipe up, `input text 00202000`, `input keyevent 66`).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. The remembered person was deactivated since: nobody is picked, the pad says "Tap your name", no crash. (Task 1 test)
2. Sign-out (idle or Switch user) while someone else was tapped: the pick goes back to the remembered person and typed digits clear. (Task 1 test)
3. Keys tapped before anyone is picked: nothing is typed, nothing is sent. (Task 2 test)
4. A phone with many staff (8 people): the PIN keys stay on screen; the tiles scroll. (Task 2 test)
5. 200% font on a 390 dp phone: no text cut, on the tiles or the pad. (Task 2 test, existing one updated)

---

### Task 1: Remember the last person and pick them on open

**Files:**
- Modify: `app/src/main/java/com/archsolutions/shopmanager/data/settings/SettingsStore.kt`
- Modify: `app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModel.kt`
- Modify: `app/src/androidTest/java/com/archsolutions/shopmanager/StaffSignInTest.kt` (constructor call only, line ~126)
- Test: `app/src/test/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModelTest.kt` (new)

**Interfaces:**
- Produces: `ShopSettings.lastStaffId: Long? = null`; `SettingsStore.setLastStaffId(id: Long?)` (null removes the key);
  key `longPreferencesKey("last_staff_id")`.
- Produces: `StaffSignInViewModel(staffRepo: StaffRepo, staffGuard: StaffGuard, session: StaffSession, settings: SettingsStore)`.
- Produces: `StaffSignInViewModel.picked: StateFlow<Long?>` = the tapped person if active, else `lastStaffId` if that
  person is in `people` (active), else null. Started `SharingStarted.Eagerly` so `enter()` can read it.
- Keeps: `pick(id: Long)`, `back()` (Task 2 removes it), `suspend fun enter(pin: String): PinResult`. On `PinResult.Ok`,
  `enter` saves the signed-in id with `setLastStaffId` and clears the tapped choice. A wrong PIN changes nothing saved.
- Keeps: the `session.current.drop(1)` collector, which now clears the tapped choice (so `picked` falls back to the
  remembered person) and `_shownCode`.

- [ ] **Step 1: Write the failing tests** in `StaffSignInViewModelTest.kt`, built like `StaffViewModelTest`
  (Robolectric, `UnconfinedTestDispatcher` as Main, in-memory `AppDatabase`, a temp-file `SettingsStore`,
  `StaffRepo(..., pinIterations = 1_000)`, `StaffSession(clock)`, `StaffGuard(repo, session, clock)`).
  Shop: `repo.enableStaff("Grace", "1111")` (Owner), `amina = repo.addStaff("Amina", Role.CASHIER, "2222")`.
  Collect `vm.picked` in `backgroundScope` so the flows run.
  - `nobody is picked before anyone has signed in on this device`: `picked.value == null`.
  - `the last person to sign in is picked when the screen opens`: `vm.pick(amina)`, `vm.enter("2222") == PinResult.Ok`;
    `settings.settings.first().lastStaffId == amina`; a second `StaffSignInViewModel` on the same repo and settings has
    `picked.value == amina`.
  - `a wrong PIN does not change the remembered person`: Amina signs in once, `session.signOut()`, then
    `vm.pick(graceId)`, `vm.enter("9999")` is `PinResult.Wrong`; `lastStaffId == amina`.
  - `a deactivated last person is not picked`: Amina signs in, `session.signOut()`, `repo.setActive(amina, false)`;
    a new view model has `picked.value == null`.
  - `a sign-out returns the pick to the last person`: Amina signs in, then `vm.pick(graceId)`, then `session.signOut()`;
    `picked.value == amina`.

- [ ] **Step 2: Run them and see them fail**

  Run: `./gradlew testDebugUnitTest --tests "*.StaffSignInViewModelTest"`
  Expected: compile error (no `settings` parameter, no `lastStaffId`).

- [ ] **Step 3: Implement** `lastStaffId` in `ShopSettings` and `SettingsStore` (read `prefs[KEY_LAST_STAFF_ID]`,
  setter writes or removes), then the view model changes listed under Interfaces: a private `MutableStateFlow<Long?>`
  for the tapped choice, and `picked` = `combine(choice, people, settings.settings.map { it.lastStaffId })`.
  Update the one constructor call in `StaffSignInTest.staffShop()` to pass its `settings`.

- [ ] **Step 4: Run the tests and see them pass**

  Run: `./gradlew testDebugUnitTest --tests "*.StaffSignInViewModelTest" --tests "*.StaffViewModelTest"`
  Expected: all pass. Then `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` builds and all JVM tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/archsolutions/shopmanager/data/settings/SettingsStore.kt \
  app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModel.kt \
  app/src/test/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModelTest.kt \
  app/src/androidTest/java/com/archsolutions/shopmanager/StaffSignInTest.kt
git commit -m "Sign-in: remember the last person and pick them when the screen opens"
```

### Task 2: Names and PIN pad on one screen

**Files:**
- Modify: `app/src/main/java/com/archsolutions/shopmanager/feature/pin/PinDialog.kt` (`PinPad`, `KeypadKey`)
- Modify: `app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInScreen.kt` (rewrite the picked/unpicked branches)
- Create: `app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffTile.kt`
- Modify: `app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModel.kt` (remove `back()`)
- Modify: `app/src/main/res/values/strings.xml` (add `staff_signin_pick_first`, remove `staff_signin_someone_else`)
- Test: `app/src/androidTest/java/com/archsolutions/shopmanager/StaffSignInTest.kt`

**Interfaces:**
- Consumes: Task 1's `picked`, `pick(id)`, `enter(pin)`, `people`.
- Produces: `PinPad(..., enabled: Boolean = true, keyHeight: Dp = 56.dp)`. With `enabled = false` the keys are disabled
  (`Surface(enabled = false)`) and show at reduced emphasis. `PinDialog` keeps calling it without the new arguments,
  so the dialog does not change.
- Produces: `@Composable fun StaffTile(name: String, roleLabel: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`:
  an initial in a 44 dp circle, the name (DisplayFontFamily, SemiBold, 20 sp), the role (bodyMedium, `colors.muted`);
  rounded 16 dp, `colors.surface` with a `colors.line` 1 dp border, or `colors.greenTint` with a 2 dp `colors.green`
  border when selected; at least `fontScaled(112.dp)` high; text wraps, never ellipsised. Role label from
  `staff_role_owner` / `staff_role_cashier` by the `role` key; an unknown key shows no role line.
- New string: `<string name="staff_signin_pick_first">Tap your name</string>`.

Layout, from the spec (`BoxWithConstraints`, wide when `maxWidth >= 600.dp`):
- **Wide:** a `Row`. Left (weight 1): the title (28 sp, as today), a `LazyVerticalGrid(GridCells.Adaptive(160.dp))`
  of tiles taking the remaining height, then "Forgot the Owner PIN" as a `SecondaryButton` (only when an Owner exists).
  Right (width 360 to 400 dp, centred, its own `verticalScroll`): the `PinPad` with `keyHeight = 64.dp`.
- **Narrow:** one `Column` with `verticalScroll`: the title, a `LazyVerticalGrid(GridCells.Fixed(2))` with
  `heightIn(max = fontScaled(232.dp))` (about two rows; it scrolls inside for more people), the forgot button, then
  the `PinPad` with `keyHeight = 64.dp`.
- Pad title: the picked person's name, subtitle `staff_signin_pin_hint`. With nobody picked: title
  `staff_signin_pick_first`, subtitle empty, `enabled = false`.
- Keep as they are: `remember(pickedId)` for the `PinEntry` and the message (a new pick clears digits and the error),
  the recovery flow, the shown-code dialog and its lifecycle handling.

- [ ] **Step 1: Write the failing device tests** in `StaffSignInTest` (shop: Grace 1111 Owner, Amina 2222, Peter off):
  - Update `rightPinLeavesTheScreenAndTheSessionHoldsThePerson`, `wrongPinShowsTriesLeft` and
    `signInScreenDoesNotClipAt200PercentFont` to the one-screen flow: tap the tile by name, then `tapPin`. In the 200%
    test, assert the hint and the "0" key are displayed straight after the tile tap, with no other navigation.
  - `namesAndKeysShowTogetherBeforeAnyPick`: both "Grace" and the key "5" are displayed, and
    `staff_signin_pick_first` is displayed.
  - `keysDoNothingBeforeAPick`: tap "2" four times with nobody picked; `session.current.value == null` and
    `staff_signin_pick_first` still shows.
  - `lastPersonIsPickedWhenTheScreenOpens`: sign Amina in through the view model, `session.signOut()`, set the content;
    the hint `staff_signin_pin_hint` shows without tapping a tile, and `tapPin("2222")` signs Amina in.
  - `manyStaffOnAPhoneKeepTheKeysOnScreen`: add 6 more cashiers (8 active), content in a `requiredWidth(390.dp)` box;
    tap "Amina"; the "0" key `assertIsDisplayed()`.

- [ ] **Step 2: Run them on the VM and see the new ones fail**

  Run the `StaffSignInTest` command from Global Constraints.
  Expected: the new tests fail (the key "5" is not shown before a pick; no `staff_signin_pick_first`).

- [ ] **Step 3: Implement** the `PinPad`/`KeypadKey` parameters, `StaffTile`, the strings, the screen layout above,
  and remove `back()` (and its only caller).

- [ ] **Step 4: Run the tests and see them pass**

  Run: the `StaffSignInTest` command (expected `OK (11 tests)` or more), then the full device suite
  `adb shell am instrument -w com.archsolutions.shopmanager.test/androidx.test.runner.AndroidJUnitRunner`
  (expected OK; `ShopDesignSystemTest` covers `PinDialog`), then `./gradlew testDebugUnitTest` (all pass).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/archsolutions/shopmanager/feature/pin/PinDialog.kt \
  app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInScreen.kt \
  app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffTile.kt \
  app/src/main/java/com/archsolutions/shopmanager/feature/staff/StaffSignInViewModel.kt \
  app/src/main/res/values/strings.xml \
  app/src/androidTest/java/com/archsolutions/shopmanager/StaffSignInTest.kt
git commit -m "Sign-in: names and PIN pad on one screen"
```
