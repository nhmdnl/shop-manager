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
