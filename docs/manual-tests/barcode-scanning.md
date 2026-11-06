# Barcode scanning: manual tests

(Task 8 fills in the test steps.)

## Privacy: ML Kit telemetry

The app's own code makes no network call when the cloud URL is blank. The bundled ML Kit barcode library
(`com.google.mlkit:barcode-scanning`) brings Google's data transport components
(`com.google.android.datatransport`, `TransportBackendDiscovery`, `JobInfoSchedulerService`) into the merged
release manifest. When the device is online, ML Kit may send anonymous usage and performance metrics to Google.
Camera frames and decoded barcode text are not sent.

Decision (controller ruling R4): the components are left in for now, because removing them cannot be verified without
a real camera and a crash while scanning is worse than the metrics. Follow-up: try removing them with
`tools:node="remove"` in the manifest and confirm on a physical tablet that scanning still works.
