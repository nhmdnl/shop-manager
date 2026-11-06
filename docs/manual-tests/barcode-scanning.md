# Barcode scanning: manual tests

(Task 8 fills in the test steps.)

## Manual steps

- (placeholder, Task 8) TalkBack on the Torch button: it must read "Torch" together with its state (On/Off); the `stateDescription` may be merged away or read twice on some devices.
- (placeholder, Task 8) Camera in use by another app: the Scan screen waits for CameraX to retry, then after about 8 s shows the message and Try again; Try again must reopen the preview once the other app lets go.

## Privacy: ML Kit telemetry

The app's own code makes no network call when the cloud URL is blank. The bundled ML Kit barcode library
(`com.google.mlkit:barcode-scanning`) brings Google's data transport components
(`com.google.android.datatransport`, `TransportBackendDiscovery`, `JobInfoSchedulerService`) into the merged
release manifest. When the device is online, ML Kit may send anonymous usage and performance metrics to Google.
Camera frames and decoded barcode text are not sent.

Decision (controller ruling R4): the components are left in for now, because removing them cannot be verified without
a real camera and a crash while scanning is worse than the metrics. Follow-up: try removing them with
`tools:node="remove"` in the manifest and confirm on a physical tablet that scanning still works.
