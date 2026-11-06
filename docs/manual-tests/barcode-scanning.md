# Barcode scanning: manual tests

(Task 8 fills in the test steps.)

## Manual steps

- (placeholder, Task 8) TalkBack on the Torch button: it must read "Torch" together with its state (On/Off); the `stateDescription` may be merged away or read twice on some devices.
- (placeholder, Task 8) Camera in use by another app: the Scan screen waits for CameraX to retry, then after about 8 s shows the message and Try again; Try again must reopen the preview once the other app lets go.

## Sell -> Scan -> Edit product -> back (physical tablet)

1. Open the day, go to Sell, tap Scan. Expected: the scanner opens.
2. Tap Scan twice quickly (or tap it again after returning). Expected: only one scanner screen; one Done/back returns to Sell (launchSingleTop).
3. Scan a barcode that is not in stock. Expected: "Not found: CODE" and Add this product.
4. Tap Add this product. Expected: New product opens with the barcode filled in.
5. Enter a name and sell price, save. Expected: back on the scanner and the "Not found" banner and Add this product button are gone.
6. Scan the same barcode again. Expected: the new product is added to the cart (count 1).
7. Repeat steps 3-4, then sign out (staff) while New product is on top. Expected: the app returns to the sign-in screen; after signing in again nothing stale (no scanner or form) is shown over Sell.
8. Tap Done. Expected: Sell with the scanned items in the cart.

## Privacy: ML Kit telemetry

The app's own code makes no network call when the cloud URL is blank. The bundled ML Kit barcode library
(`com.google.mlkit:barcode-scanning`) brings Google's data transport components
(`com.google.android.datatransport`, `TransportBackendDiscovery`, `JobInfoSchedulerService`) into the merged
release manifest. When the device is online, ML Kit may send anonymous usage and performance metrics to Google.
Camera frames and decoded barcode text are not sent.

Decision (controller ruling R4): the components are left in for now, because removing them cannot be verified without
a real camera and a crash while scanning is worse than the metrics. Follow-up: try removing them with
`tools:node="remove"` in the manifest and confirm on a physical tablet that scanning still works.

## Edit product: scan the barcode and the duplicate warning

1. Open Stock, then New product (or Edit product). Expected: a Scan button beside the Barcode field (absent on a device with no camera).
2. Tap Scan, aim at a barcode. Expected: one beep, the scanner closes by itself, the Barcode field holds the code. Hold the barcode in view as it closes: the field still holds just that one code, no second scanner opens.
3. Tap Scan, then Cancel. Expected: back on Edit product, the field unchanged.
4. Scan (or type) a barcode that another product already uses. Expected: a line "Already used by NAME. You can still save." under the field; Save still works.
5. Open an existing product that has a barcode. Expected: no warning for its own barcode.
6. Rotate the screen after scanning. Expected: the code stays in the field.
7. With staff sign-in on, sign out while the scanner is open. Expected: the app returns to sign-in; no scanner left over after signing in.

## Stock: scan to find a product

1. Open Stock (phone and tablet). Expected: a Scan button beside the search field (absent on a device with no camera).
2. Tap Scan (twice quickly). Expected: one scanner screen. Scan a barcode that belongs to a product. Expected: one beep, the scanner closes, Edit product opens for that product; the search field is unchanged. Go back: Stock does not reopen the product.
3. Scan a barcode no active product has. Expected: back on Stock with "Not found: CODE" and Add this product; the search field and list are unchanged.
4. Tap Add this product. Expected: New product with the barcode filled in. Save it, return to Stock, scan the same code: that product opens.
5. Scan the barcode of an archived (inactive) product. Expected: Not found.
6. With the Not found banner showing, sign out (staff). Expected: no banner after signing in again.
7. Rotate or open Edit product then go back after a found scan. Expected: no second Edit product, no repeated banner.
