# Shop Manager

<!-- hub:begin — managed by `hub adopt`; edit outside these markers -->
## Agent hub

Run `hub brief` before planning or editing anything. It prints the shared state every AI harness
on this machine works from: active projects, what other agents did recently, standing decisions.

**This project's note:** `Hub/Projects/shop-manager.md` — read it before working here. It holds the goal,
the current state, and the constraints you would otherwise get wrong.

Record what you learn, or the next agent rediscovers it:

    hub log "one line, past tense, what you did"
    hub decide "<title>"      # a choice that rules out alternatives

Full rules: `Hub/Protocol.md`
<!-- hub:end -->

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Constraints, goal and current state live in the hub note above (`Hub/Projects/shop-manager.md`); don't duplicate them here. Specs: v1.0 payload `~/Downloads/ShopManager_Android_Build_Payload.md` (milestones M1–M9 in §14), v1.1 addendum `~/Projects/shpmgmt/ShopManager_Build_Payload_v1.1_Addendum.md` (wins where they differ), design PNGs zipped in `~/Projects/shpmgmt/`.

## Commands

    ./gradlew assembleDebug
    ./gradlew testDebugUnitTest                                   # JVM tests (Room DAO/transaction tests included)
    ./gradlew testDebugUnitTest --tests "*.SaleTransactionTest"   # single class
    ./gradlew connectedDebugAndroidTest                           # needs device/emulator (ShopDesignSystemTest)
    adb connect 127.0.0.1:5555 && ./gradlew installDebug          # test device: vm-curator "Android x86" VM (API 28, 1280x800), must be running

Kotlin + Compose + Hilt + Room via KSP (no kapt). compileSdk 36, minSdk 26. Package `com.archsolutions.shopmanager`.

## Architecture

Single `app` module, layered by package:

- `core/` — `money/Money.kt` (UGX as `Long`), `designsystem/` (theme + reusable components; screens compose these, not raw Material), `locale/LanguageManager` (en/lg/pt/sw/ti via `values-*`), `util/` (`Clock`, `Result`).
- `data/db/` — `AppDatabase` (single Room DB), `entities/`, `dao/`, `SeedDataLoader`. `data/repo/*Repo` is the only layer features touch; any write spanning tables belongs in a repo method backed by a DAO `@Transaction`. `data/settings/SettingsStore` is DataStore.
- `domain/` — pure business logic shared by features (`NoteCounter`, `Cart.kt`: sale total rounding must stay identical to `SaleRepo.createSale`, quick cash amounts, `isLowStock`; `BalanceCalculator`/`DaySummary` for closing the day; `Debtors`; `Reorder`: 5.6 suggested quantities, the WhatsApp order text and per-supplier low counts). Message texts come in as templates from strings.xml so they stay translatable and testable.
- `feature/<name>/` — one Compose screen (+ ViewModel) per area: home, sell, sales (Today's sales, Sale detail), stock, suppliers, day, reports, settings. `SellViewModel` is scoped to the `SellGraph` nav graph so Sell and phone Checkout share one cart, and it persists the cart in `SavedStateHandle`.
- `app/` — `ShopApp`, `AppNavHost`, `MainActivity`, and `DayGate`: a singleton `Flow<DayGateState>` for today's business date (spec 6.3 cases). `DayGateViewModel` loads the seed first, then exposes the state; `AppNavHost` passes it to screens and re-reads the date on resume.

Day locking runs on two layers. The UI shows `feature/day/DayGateLockCard` and greys out actions while the state isn't `Ready`. Every sale, purchase and money-entry write calls `requireOpenDay(dayId)` (`data/repo/DayLock.kt`), which throws `DayNotOpenException`. No day is ever opened implicitly (hub decision 0005).

Gate prompts (Open the day, Yesterday is still open) show once per business date, and `AppNavHost` holds them while Open/Close the day or Day closed is on screen. A prompt pushed over Close the day gets popped with it.

PIN (addendum C4): screens wrap protected actions in `rememberPinGate().run(purpose) { … }` (`feature/pin/PinDialog.kt`). It runs at once when no PIN is set, else after `PinGuard` accepts the PIN; 5 wrong tries lock it for 30 s. The 6-second Undo after a sale skips it on purpose.

Backup/restore (section 11, `data/backup/BackupManager.kt`): a zip of manifest + database (after a WAL checkpoint) + settings file. A restore never replaces files under a live Room/DataStore. It is checked, staged in `files/pending_restore`, the app restarts, and `ShopApp.onCreate` applies it before `super.onCreate()`. `AutoBackupWorker` keeps the last 7 in app storage after each closed day.

Onboarding (7.8): `DayGateViewModel.needsOnboarding`. Installs with days skip it, except a debug install the seed just filled (the seed opens a day). Its last step opens the first day.

Close-day reminder (`app/CloseReminder.kt`, spec 6.7): one unique one-time WorkManager job. `MainActivity` re-plans it with REPLACE on launch and whenever `closeReminderTime` changes; each run re-plans the next with APPEND_OR_REPLACE so it doesn't cancel itself. The worker reaches Hilt objects through `CloseReminderEntryPoint`. A tap arrives as `ACTION_CLOSE_DAY`, and `AppNavHost` opens Close the day only when the gate is `Ready`.

Adaptive shell: tablet landscape is primary, phone portrait secondary; the same feature screens serve both.

Tests live in `app/src/test` (JVM, in-memory Room) except design-system UI tests in `androidTest`.
