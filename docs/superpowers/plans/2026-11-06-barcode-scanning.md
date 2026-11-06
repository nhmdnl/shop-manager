# Barcode Scanning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Camera barcode scanning in Sell, Edit product, Stock and Purchases: continuous in Sell and Purchases, single-shot in Edit product and Stock.

**Architecture:** A `ScanSource` interface (a code stream, a camera preview, a torch switch) with a CameraX + ML Kit implementation and a fake for tests; pure logic (`Barcodes`, `ScanSession` debounce, `ScanController`) in JVM-testable classes; one shared `ScanScreen` reached through three route types (`ScanSellRoute`, `ScanPurchaseRoute`, `ScanOnceRoute`); each host screen supplies what to do with a known code. Camera permission is requested on the first Scan tap only.

**Tech Stack:** Kotlin, Compose, Hilt, Room, CameraX (core, camera2, lifecycle, view), ML Kit Barcode Scanning (bundled model).

**Spec:** `docs/superpowers/specs/2026-11-06-barcode-scanning-design.md` (decisions D1 to D4 there are fixed).

## Global Constraints

- Package `com.archsolutions.shopmanager`; minSdk 26; Room via KSP; Hilt; versions only in `gradle/libs.versions.toml`.
- Works fully offline: ML Kit **bundled** model only (`com.google.mlkit:barcode-scanning`), never the Play-services model.
- `CAMERA` permission is requested at runtime on the first Scan tap, never at install; manifest `uses-feature` for `android.hardware.camera` and `android.hardware.camera.autofocus` with `required="false"`; Scan buttons are hidden when the device has no camera; `VIBRATE` is a normal permission and must be declared.
- Camera frames are analysed on the device and never saved or sent anywhere.
- Debounce: the same code within 1,500 ms of its last accepted scan is ignored; a different code is accepted at once.
- Barcode matching: `Barcodes.key` pads a digits-only 12-digit code to 13 digits; text codes are trimmed and unchanged; lookup takes the first (lowest id) ACTIVE product among `Barcodes.candidates`; stored barcodes are never rewritten.
- Sell scanning goes through the same cart function a product tap uses (`SellViewModel.addOne(productId)`), so stock/day rules are unchanged; the Scan button is disabled unless the day gate is `Ready`; any signed-in role may scan.
- Every screen and dialog added must close on sign-out via `OnSignOut` (the scanner screen too), unknown state fails closed, all text in `strings.xml` (English; the translation tool picks it up), design-system components, 48 dp targets, `fontScaled()`/`CappedFontScale` so 200% font does not clip, one green primary action per screen.
- A tap-only sale stays 3 taps (phone) / 2 taps (tablet) (`SaleTapCountTest`).
- The Android x86 VM has NO camera: never claim real-camera results from it; use `export ANDROID_SERIAL=127.0.0.1:5555`, never the physical tablet; never run `connectedDebugAndroidTest`; read the app DB with its `-wal` and `-shm` files.
- Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Nothing is pushed.

## Review Focus

1. Camera permission denied, "never ask again", revoked while the scanner is open, and a device without a camera: no crash, no blank screen, a one-line message and an Open settings button.
2. An unknown code during a continuous scan, a code that matches only an INACTIVE product, and an Add-this-product round trip: the new product is known to the scanner on return and no scan is lost or doubled.
3. The same item held in view (debounce), two different items scanned within 1,500 ms, and UPC-A vs EAN-13 spellings of one product.
4. Rotation or process kill while scanning in Sell: the cart (already in saved state) and the running count survive; sign-out or an idle sign-out while the scanner is open closes it and releases the camera.
5. Torch toggle on a camera with no flash (button hidden), the camera released on Done/back/pause, and the scanner opened twice quickly.

---

### Task 1: Barcodes and product lookup

**Files:**
- Create: `app/src/main/java/com/archsolutions/shopmanager/domain/Barcodes.kt`
- Modify: `data/db/dao/ProductDao.kt` (two queries), `data/repo/ProductRepo.kt`
- Test: `app/src/test/java/com/archsolutions/shopmanager/domain/BarcodesTest.kt`, `.../data/repo/ProductBarcodeLookupTest.kt` (in-memory Room, like `SupplierRepoTest`)

**Interfaces:**
- Produces: `object Barcodes { fun key(code: String): String; fun candidates(code: String): List<String> }` (`candidates` = the trimmed text, its 13-digit form, and the 12-digit form when the 13-digit form starts with `0`, distinct, text first). `ProductDao`: `suspend fun findActiveByBarcodes(codes: List<String>): ProductEntity?` (`WHERE active = 1 AND barcode IN (:codes) ORDER BY id LIMIT 1`) and `suspend fun findOtherByBarcodes(codes: List<String>, exceptId: Long): ProductEntity?` (same, `AND id != :exceptId`). `ProductRepo`: `suspend fun findByBarcode(code: String): ProductEntity?` and `suspend fun productUsingBarcode(code: String, exceptId: Long): ProductEntity?`; blank codes return null without a query.

- [ ] **Step 1: Write the failing tests.** `BarcodesTest`: `12-digit code is padded to 13`, `13-digit code is unchanged`, `text code is trimmed and unchanged`, `candidates of an EAN starting with 0 include the 12-digit UPC form`, `candidates of a 12-digit UPC include the 13-digit form`, `blank gives an empty candidate list`. `ProductBarcodeLookupTest`: `finds an active product by exact code`, `finds it by its UPC spelling when stored as EAN-13 and the reverse`, `inactive product is not found`, `duplicates return the lowest id`, `productUsingBarcode ignores the product itself`, `blank code returns null`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*BarcodesTest" --tests "*ProductBarcodeLookupTest"`; expect compile failure.
- [ ] **Step 3: Implement** `Barcodes`, the two DAO queries and the two repo functions.
- [ ] **Step 4: Run** the same command plus the full `./gradlew testDebugUnitTest`; expect all green.
- [ ] **Step 5: Commit** `"Barcodes: normalisation and product lookup"`.

### Task 2: Scan core logic (debounce, controller, source interface, feedback)

**Files:**
- Create: `core/scan/ScannedCode.kt`, `core/scan/ScanSource.kt`, `core/scan/ScanFeedback.kt`, `feature/scan/ScanSession.kt`, `feature/scan/ScanController.kt`
- Test: `app/src/test/java/com/archsolutions/shopmanager/feature/scan/ScanSessionTest.kt`, `ScanControllerTest.kt`

**Interfaces:**
- Consumes: `Barcodes`, `ProductEntity`, `AppClock`.
- Produces: `data class ScannedCode(val text: String, val format: String = "")`. `interface ScanSource { val codes: Flow<ScannedCode>; val hasTorch: StateFlow<Boolean>; fun setTorch(on: Boolean); @Composable fun Preview(modifier: Modifier) }`. `interface ScanFeedback { fun success(); fun failure() }`. `class ScanSession(clock: AppClock, debounceMillis: Long = 1_500L) { fun accept(code: String): Boolean }` (compares `Barcodes.key`). `sealed interface ScanOutcome { data class Known(val product: ProductEntity) ; data class NotFound(val code: String); data object Ignored }`. `class ScanController(session: ScanSession, lookup: suspend (String) -> ProductEntity?) { suspend fun handle(code: ScannedCode): ScanOutcome }` and a state holder `data class ScanUiState(val count: Int = 0, val last: String? = null, val notFound: String? = null)` updated by `fun ScanUiState.reduce(outcome: ScanOutcome, line: (ProductEntity) -> String): ScanUiState` (Known: count + 1, last = line, notFound cleared; NotFound: notFound = code; Ignored: unchanged).

- [ ] **Step 1: Write the failing tests** (fake `AppClock`, no sleeps): `ScanSessionTest`: `the same code within 1500 ms is ignored`, `the same code at exactly 1500 ms is accepted`, `a different code is accepted at once`, `UPC and EAN spellings count as the same code`, `an ignored repeat does not extend the window`. `ScanControllerTest`: `a known code gives Known`, `an unknown code gives NotFound with the scanned text`, `an inactive-only product gives NotFound`, `a repeat inside the window gives Ignored and does not query`, `reduce counts known scans and keeps the last line`, `reduce sets and clears notFound`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ScanSessionTest" --tests "*ScanControllerTest"`; expect compile failure.
- [ ] **Step 3: Implement** the files exactly as the signatures above; the controller asks the session first and only then the lookup.
- [ ] **Step 4: Run** the same command; expect PASS.
- [ ] **Step 5: Commit** `"Scan: debounce, controller and source interfaces"`.

### Task 3: Camera implementation, permission and the ScanScreen

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (CameraX core/camera2/lifecycle/view and `com.google.mlkit:barcode-scanning`), `app/src/main/AndroidManifest.xml` (`CAMERA`, `VIBRATE`, the two optional `uses-feature`s)
- Create: `core/scan/CameraXScanSource.kt`, `core/scan/AndroidScanFeedback.kt`, `core/scan/CameraPermission.kt`, `feature/scan/ScanScreen.kt`, `feature/scan/ScanViewModel.kt`
- Modify: `app/AppNavHost.kt` (add three route types, since a NavHost allows one destination per route type: `@Serializable object ScanSellRoute` (continuous, lives inside `navigation<SellGraph>`), `@Serializable object ScanPurchaseRoute` (continuous), `@Serializable object ScanOnceRoute` (single-shot, used by Edit product and Stock); the destinations are wired per host in Tasks 4 to 7)
- Test: `app/src/test/.../feature/scan/ScanViewModelTest.kt`; `app/src/androidTest/.../ScanScreenTest.kt` (fake source)

**Interfaces:**
- Consumes: `ScanSource`, `ScanFeedback`, `ScanController`, `ScanUiState`, `ProductRepo.findByBarcode`.
- Produces: `class CameraXScanSource(context, lifecycleOwner): ScanSource` (ML Kit `BarcodeScanning` for all common retail formats: EAN-13/8, UPC-A/E, Code 128, Code 39, QR; closes the analyzer and unbinds the camera on dispose; torch via `CameraControl.enableTorch`, `hasTorch` from `CameraInfo.hasFlashUnit()`); `class AndroidScanFeedback(context): ScanFeedback` (short beep + vibration on success, a lower tone on failure, vibration skipped when the device has no vibrator); `@Composable fun rememberCameraPermission(): CameraPermission` with `state: Granted | Denied | NotAsked`, `request()` and `openSettings()`; `@HiltViewModel class ScanViewModel` with `val state: StateFlow<ScanUiState>` and `fun onCode(code: ScannedCode, onKnown: suspend (ProductEntity) -> Unit)`; `@Composable fun ScanScreen(source: ScanSource, feedback: ScanFeedback, continuous: Boolean, state: ScanUiState, onCode: (ScannedCode) -> Unit, onAddProduct: (String) -> Unit, onDone: () -> Unit)` (camera preview, aim box, torch toggle shown only when `hasTorch`, live-region announcement of each result, count and last item for continuous, red "Not found: CODE" with an "Add this product" button, Done (continuous) or Cancel (single-shot), 48 dp, `fontScaled()`); permission-denied shows a one-line message and "Open settings" instead of the preview; the screen closes itself on sign-out (`OnSignOut { onDone() }`).

- [ ] **Step 1: Write the failing tests.** `ScanViewModelTest` (JVM, fake lookup + fake clock): `a known code calls onKnown and increments the count`, `an unknown code sets notFound and does not call onKnown`, `a repeat inside 1500 ms is ignored`, `a second known scan of another code is counted`. `ScanScreenTest` (androidTest, fake `ScanSource`, compile-check and run on the VM): `shows the running count and the last item`, `shows Not found with an Add this product button for an unknown code`, `permission denied shows the message and Open settings, no preview`, `Done calls onDone`, `torch button is hidden when the source has no torch`, `does not clip at 200% font`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ScanViewModelTest"` (expect compile failure), then add the dependencies.
- [ ] **Step 3: Implement** the files above. `ScanViewModel` keeps `ScanUiState` and the `ScanSession`; the real camera classes are thin and untested here (a physical tablet proves them).
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `./gradlew compileDebugAndroidTestKotlin`, then on the VM `./gradlew installDebug installDebugAndroidTest` and `adb shell am instrument -w -e class com.archsolutions.shopmanager.ScanScreenTest com.archsolutions.shopmanager.test/androidx.test.runner.AndroidJUnitRunner`; expect PASS.
- [ ] **Step 5: Commit** `"Scan: CameraX + ML Kit source, permission and ScanScreen"`.

### Task 4: Sell scanning and the Add-this-product round trip

**Files:**
- Modify: `app/AppNavHost.kt` (`composable<ScanSellRoute>` inside `navigation<SellGraph>`, sharing the one `SellViewModel` through `sellGraphViewModel(navController, entry)`; `EditProductRoute(val id: Long = 0L, val barcode: String = "")`), `feature/sell/SellScreen.kt` (Scan button, hidden without a camera, disabled unless the day gate is `Ready`), `feature/stock/EditProductViewModel.kt` (prefill the new product's barcode from the route), `res/values/strings.xml`
- Test: `app/src/test/.../feature/sell/SellScanTest.kt`, `app/src/test/.../feature/stock/EditProductViewModelTest.kt` (extend), `app/src/androidTest/.../SellScanFlowTest.kt`

**Interfaces:**
- Consumes: `ScanSellRoute`, `ScanScreen`, `ScanViewModel`, `SellViewModel.addOne(productId: Long)`, `ProductRepo.findByBarcode`.
- Produces: the `composable<ScanSellRoute>` destination whose `onKnown` is `sellViewModel.addOne(product.id)`; `EditProductRoute(barcode)` prefills a NEW product (`id = 0`) only; saving returns to the scanner (back stack: Sell, Scan, EditProduct); new strings `scan_action`, `scan_items_count` (plural), `scan_not_found` (`%1$s`), `scan_add_product`, `scan_done`, `scan_cancel`, `scan_permission_needed`, `scan_open_settings`, `scan_torch`.

- [ ] **Step 1: Write the failing tests.** `SellScanTest` (JVM): `a known code adds one unit through SellViewModel.addOne (cart qty 1)`, `scanning the same product twice outside the debounce window gives qty 2`, `an unknown code leaves the cart unchanged`, `an inactive product leaves the cart unchanged`. `EditProductViewModelTest`: `a new product opened with a barcode starts with it filled in`, `an existing product ignores the route barcode`. `SellScanFlowTest` (androidTest, fake source): `scan button opens the scanner and Done returns to Sell with the cart filled`, `scan button is disabled when the day is not open`, `unknown code then Add this product opens the new-product form with the barcode filled in`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*SellScanTest" --tests "*EditProductViewModelTest"`; expect failures.
- [ ] **Step 3: Implement** the route, the Scan button and the prefill. The scan route reads the host's `SellViewModel` from the existing Sell graph entry; the cart lives in saved state so a process kill while scanning keeps it.
- [ ] **Step 4: Run** the two classes, the full `./gradlew testDebugUnitTest`, and on the VM `SellScanFlowTest` and `SaleTapCountTest`; expect PASS (tap counts unchanged).
- [ ] **Step 5: Commit** `"Sell: scan products into the cart"`.

### Task 5: Edit product scan and the duplicate-barcode warning

**Files:**
- Modify: `feature/stock/EditProductScreen.kt` (scan icon in the barcode field), `feature/stock/EditProductViewModel.kt` (duplicate check), `app/AppNavHost.kt` (the single `composable<ScanOnceRoute>` destination: it returns the first accepted code through the previous back stack entry's `SavedStateHandle` under the constant `SCANNED_BARCODE_KEY` and pops itself; Stock reuses it in Task 6), strings
- Test: `app/src/test/.../feature/stock/EditProductViewModelTest.kt` (extend), `app/src/androidTest/.../EditProductScanTest.kt`

**Interfaces:**
- Consumes: `ProductRepo.productUsingBarcode(code, exceptId)`, `ScanOnceRoute`.
- Produces: `const val SCANNED_BARCODE_KEY = "scanned_barcode"`; `EditProductViewModel.setScannedBarcode(code: String)` (trims, fills the form's barcode); `EditProductState.duplicateOf: String?` (the other product's name, set while the typed or scanned barcode belongs to another product; saving is NOT blocked); strings `barcode_scan`, `barcode_in_use` (`%1$s`).

- [ ] **Step 1: Write the failing tests.** `a scanned code fills the barcode field trimmed`, `duplicateOf names the other active product using the barcode`, `duplicateOf is null for the product's own barcode`, `saving with a duplicate barcode still saves`. androidTest `EditProductScanTest` (fake source): `scan icon fills the field`, `duplicate warning shows the other product's name`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*EditProductViewModelTest"`; expect failures.
- [ ] **Step 3: Implement.** Single-shot: the first accepted code is written to the previous back stack entry's `SavedStateHandle` and the scanner pops itself.
- [ ] **Step 4: Run** the class, the full unit suite, and on the VM `EditProductScanTest`; expect PASS.
- [ ] **Step 5: Commit** `"Edit product: scan the barcode, warn on a duplicate"`.

### Task 6: Stock scan

**Files:**
- Modify: `feature/stock/StockScreen.kt` (scan icon next to the search field), `feature/stock/StockViewModel.kt`, `app/AppNavHost.kt` (navigates to the existing `ScanOnceRoute` from Stock; Stock's entry reads `SCANNED_BARCODE_KEY`), strings
- Test: `app/src/test/.../feature/stock/StockScanTest.kt`, `app/src/androidTest/.../StockScanTest.kt`

**Interfaces:**
- Consumes: `ProductRepo.findByBarcode`, `SCANNED_BARCODE_KEY`.
- Produces: `StockViewModel.onScanned(code: String): ScanLookup` where `sealed interface ScanLookup { data class Found(val productId: Long); data class NotFound(val code: String) }`; Found opens `EditProductRoute(productId)`; NotFound shows "Not found: CODE" with Add this product (opens `EditProductRoute(barcode = code)`); the search field is not changed.

- [ ] **Step 1: Write the failing tests.** `a known code gives Found with the product id`, `an unknown code gives NotFound`, `an inactive product gives NotFound`, `the search query is unchanged by a scan`. androidTest: `scan icon with a known code opens that product`, `unknown code shows Not found and the Add button`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*StockScanTest"`; expect failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the class, the full unit suite, and on the VM the androidTest; expect PASS.
- [ ] **Step 5: Commit** `"Stock: scan to find a product"`.

### Task 7: Purchases scan

**Files:**
- Modify: `feature/stock/PurchaseForm.kt` (Scan button), `app/AppNavHost.kt` (`composable<ScanPurchaseRoute>`; the scanner resolves the host's `PurchaseViewModel` from `navController.previousBackStackEntry`: the purchase form is hosted by the `AddPurchaseRoute` entry on phones AND by the `StockRoute` entry on tablets (`StockScreen` has its own `hiltViewModel()` `PurchaseViewModel` and shows `PurchaseForm` in its second pane), so resolve the ViewModel from whichever entry opened the scanner), strings
- Test: `app/src/test/.../feature/stock/PurchaseScanTest.kt`, `app/src/androidTest/.../PurchaseScanFlowTest.kt`

**Interfaces:**
- Consumes: `PurchaseViewModel.addProduct(productId: Long)` and `contains`, `setQty`, the continuous `ScanScreen`.
- Produces: scanning a known code adds the product with quantity 1; an already added product gets quantity + 1 (`PurchaseViewModel.addOrIncrement(productId: Long)`, new, built on the existing functions); unknown codes behave as in Sell (Add this product returns to the scanner); the Scan button follows the existing day gate.

- [ ] **Step 1: Write the failing tests.** (Also test the scanner from BOTH hosts: phone `AddPurchaseScreen` and tablet `StockScreen`'s purchase pane; the Scan button lives in `PurchaseForm`, so both get it.) `a known code adds a purchase line with quantity 1`, `the same product again increments the quantity`, `an unknown code adds nothing`, `the day-locked gate disables the Scan button`. androidTest: `purchase scan adds lines and Done returns to the form with them`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*PurchaseScanTest"`; expect failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the class, the full unit suite, and on the VM the androidTest; expect PASS.
- [ ] **Step 5: Commit** `"Purchases: scan items into a purchase"`.

### Task 8: Whole-branch verification and the manual camera script

**Files:**
- Create: `docs/manual-tests/barcode-scanning.md`
- Modify: `docs/FEATURES.md` only after a release ships it (leave unchanged now)

- [ ] **Step 1:** `./gradlew testDebugUnitTest`; expect 0 failures.
- [ ] **Step 2:** `./gradlew assembleDebug assembleRelease compileDebugAndroidTestKotlin`; expect all build (R8 must not strip ML Kit: if the release build or its smoke test fails add the library's consumer rules). Record the release APK size before (about 17 MB on `develop`) and after.
- [ ] **Step 3:** On the VM (`ANDROID_SERIAL=127.0.0.1:5555`, data kept, DB read with its `-wal` and `-shm`) run `ScanScreenTest`, `SellScanFlowTest`, `EditProductScanTest`, `StockScanTest`, `PurchaseScanFlowTest`, `SaleTapCountTest`, `StaffSignInTest`, `ShopDesignSystemTest`; record the counts. State plainly that no real camera ran.
- [ ] **Step 4:** Write `docs/manual-tests/barcode-scanning.md`: numbered steps and expected results for the user's physical tablet: grant and deny the camera permission, scan a real EAN-13 product into the cart, scan 5 items continuously and tap Done, the same item held in view adds once, an unknown code then Add this product, scan in Edit product, Stock and a purchase, the torch in dim light, rotate while scanning, kill the app while scanning and reopen, sign out while the scanner is open, deny the permission permanently and use Open settings.
- [ ] **Step 5: Commit** any fixes and the doc; `hub log` the result; do not merge or push without the user's OK.
