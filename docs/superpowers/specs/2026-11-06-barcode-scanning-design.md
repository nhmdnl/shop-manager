# Barcode scanning: design

Date 2026-11-06. Status: design approved in chat by the user; written spec awaiting their review.
Branch `barcode-scanning` (from `develop`).

## Goal and success

Point the tablet or phone camera at a product to find or add it, without typing digits.
Success:

1. In Sell, scanning N products costs 2 taps in total (open the scanner, tap Done), and each scan adds one to the cart.
2. A product's barcode can be filled in by scanning it (Edit product).
3. Stock and Purchases can find or add a product by scanning.
4. It works fully offline, asks for the camera only when scanning, and never stores or sends camera frames.

The existing tap rule (a tap-only sale is 3 taps on a phone, 2 on a tablet) is not affected.

## Decisions taken with the user

| # | Decision |
|---|---|
| D1 | Scanning works in four places: Sell (adds to the cart), Edit product (fills the barcode field), Stock (finds a product), Purchases (adds a purchase line). |
| D2 | Sell and Purchases use a **continuous** scanner: it stays open, each recognised code is handled, a Done button returns. Edit product and Stock use a **single-shot** scan that returns after one code. |
| D3 | An unknown code in a continuous scanner shows a warning (error beep, red "Not found: CODE" line) and an "Add this product" button. That opens the new-product form with the barcode filled in, then returns to scanning. |
| D4 | Scanner library: ML Kit Barcode Scanning with the **bundled** model (offline, no Play services) on CameraX. Not the Play-services-downloaded model (needs network); ZXing is the fallback if the size or licence matters. |

## Barcodes

- `domain/Barcodes.kt`:
  - `key(code: String): String` trims; a digits-only code of 12 or 13 digits is left-padded with zeros to 13 (UPC-A equals EAN-13 with a leading 0); anything else (Code 128 and other text codes) is returned as scanned, trimmed.
  - `candidates(code: String): List<String>` returns the spellings to look for in the database: the scanned text, its 13-digit form, and the 12-digit form (when the 13-digit form starts with 0).
- `ProductRepo.findByBarcode(code: String): ProductEntity?` returns the first **active** product whose stored barcode equals one of the candidates (a single query with `IN`). Stored barcodes are not rewritten.
- Duplicate barcodes: lookup takes the first match (lowest id). Edit product warns "This barcode is already used by NAME" when saving a barcode another product has; it does not block saving.
- Existing barcode search (typed text) is unchanged. USB or Bluetooth scanners that type like a keyboard are out of scope.

## Scanner component

- `core/scan/BarcodeScanner`: an interface exposing the camera preview and a `Flow<ScannedCode>` (`ScannedCode(text: String, format: String)`); the real implementation uses CameraX (`PreviewView`, `ImageAnalysis`) and ML Kit. A fake implementation in tests emits scripted codes.
- `feature/scan/ScanScreen` (full screen, design-system components): camera preview, an aim box, a torch toggle (shown only when the camera has a flash), a running count and the last item with its price ("3 items · Sugar 1kg 4,000"), a red status line for unknown codes, and a Done button (48 dp, the screen's one primary action). In single-shot mode there is a Cancel button instead of a running count.
- Debounce (continuous): the same code seen again within 1,500 ms of its last accepted scan is ignored; a different code is accepted immediately.
- Feedback: a short beep and a vibration on success, a different lower tone on "not found". Uses the device volume; no setting. Vibration is skipped on a device without a vibrator.
- Accessibility: each result is announced through a live region; no information is carried by colour alone; at 200% font the overlay text uses `fontScaled()`/`CappedFontScale`.
- Permission: `CAMERA` is requested at runtime on the first Scan tap, never at install. Denied: the scanner screen is replaced by a one-line message and an "Open settings" button. Never granted: the Scan buttons stay visible and show that message. Manifest: `uses-feature android.hardware.camera` and `autofocus` with `required="false"` so devices without them still install; the Scan buttons are hidden when there is no camera.
- Frames are analysed on the device and are never saved or sent anywhere.

## Behavior by screen

- **Sell:** a Scan button (design-system secondary) near the search field. It opens the continuous scanner. Each accepted known code adds one unit through the same cart function a tap on the product uses (same stock and day rules). It is disabled when the day is not open (the existing gate). Any signed-in role can scan (scanning is selling). The scanner is a destination inside the Sell navigation graph so it shares the one `SellViewModel` and the saved cart.
- **Edit product:** a scan icon in the barcode field starts a single-shot scan and writes the code into the field (the code as scanned, trimmed). On save, the duplicate warning above applies.
- **Stock:** a scan icon next to the search field starts a single-shot scan. Exactly one match opens that product; none shows "Not found: CODE" with the Add this product button; the search field is not changed.
- **Purchases:** the purchase form's Scan button opens the continuous scanner; each known code adds the product to the purchase with quantity 1 (an already added product gets quantity + 1). Unknown codes behave as in D3.
- **Add this product (all screens):** opens the existing new-product form with the barcode prefilled; on save the user returns to the scanner, and the new product is then known to it.

## Dependencies and build

- Add CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) and `com.google.mlkit:barcode-scanning` (bundled model) to the version catalog; keep versions in `gradle/libs.versions.toml`.
- Add the library's R8/ProGuard consumer rules if the release build needs them; verify with `assembleRelease`.
- The APK grows by about 3 MB per ABI; check and record the actual size.
- minSdk stays 26.

## Out of scope

Printing or generating barcode labels, QR payments, linking a scan to an existing product that has no barcode ("which product is this?"), special handling for USB or Bluetooth keyboard scanners, a sound on/off setting, scanning in Reports or Suppliers.

## Testing

- JVM: `Barcodes.key/candidates` (12-digit UPC-A, 13-digit EAN, leading zeros, text codes, whitespace); `ProductRepo.findByBarcode` (active only, inactive ignored, UPC/EAN spellings match, first match with duplicates); the debounce logic (same code inside 1,500 ms ignored, after it accepted, different code accepted) on a fake clock; the continuous-scan reducer (known code adds, unknown code produces the warning state, count and last item).
- Compose tests with the fake scanner: Sell scan adds to the cart and shows the count; unknown code shows "Not found" and the Add button; permission denied shows the message and Open settings; Edit product scan fills the field; the scanner screen does not clip at 200% font.
- `SaleTapCountTest` still passes (tap sale unchanged).
- The VM has no camera: the real camera path needs a physical tablet. A short manual test script (steps and expected results) is part of the plan; the user runs it on the Lenovo tablet.

## Risks to watch

1. Autofocus and low light on cheap cameras: the torch toggle and the aim box help; verify on the physical tablet.
2. Permission denial paths must never crash or leave a blank screen.
3. Two products with the same barcode: documented, warned in Edit product.
4. R8 stripping ML Kit classes in release: verify with a release build.
5. Navigation: the scanner inside the Sell graph must not lose the cart when the process is killed while scanning (the cart already lives in saved state).
