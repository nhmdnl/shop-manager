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

## Purchases: scan items into a purchase

Phone (Stock, tap a product or open Add purchase) and tablet (Stock, Add purchase panel on the right):

1. With the day open, open Add purchase. Expected: a Scan button under "Add product" (absent on a device with no camera; absent when the day is not open, the lock card shows instead).
2. Tap Scan (twice quickly). Expected: one scanner screen. Scan a known product. Expected: one beep, count 1, the product is not yet visible (the scanner is on top).
3. Hold the same barcode in view. Expected: it counts once. Move away and scan it again. Expected: count 2.
4. Scan two different products, tap Done. Expected: back on the same Add purchase form (same supplier, same Paid from) with one line per product; the repeated product has quantity 2, others 1; cost is each product's current cost; the total updates.
5. Scan, Done, then Save purchase. Expected: stock rises by the scanned quantities. Repeat on the tablet panel (not the phone screen): the lines appear in the panel.
6. Scan an unknown code, tap Add this product. Expected: New product with the barcode filled in. Save it, go back. Expected: the scanner again, with the purchase lines kept; scan the new code: it adds a line.
7. Scan a code of an archived product. Expected: Not found.
8. Tablet: with lines already typed in the panel (quantity edited by hand), scan one of them. Expected: its quantity rises by 1, nothing else changes.
9. Close the day (or let the date roll over) while the scanner is open. Expected: the scanner closes by itself and the form shows the lock card.
10. With staff sign-in on, sign out while the scanner is open. Expected: sign-in screen, no scanner left after signing in.
11. Rotate while scanning, then Done. Expected: lines are kept.
12. Tap Done on the purchase scanner on phone and on tablet. Expected: back on the same form with its lines, no extra screen, no flicker to another purchase form.
