# RENZO ID Scanner

A minimal native Android app (Java) for displaying launcher apps, classifying each as GAME / APP / UNKNOWN, detecting known attribution SDK code in installed APKs, performing an explicit local Root scan of attribution preference files for the launcher apps shown in the grid, and reading the device advertising ID (GAID) through the official Google Play services API.

## What the scanner does

- Four-column grid of launcher application icons; application names are not visibly shown.
- Reads and displays this Android device/profile's GAID through `AdvertisingIdClient` on a background thread. Tap the GAID value to copy it or use **REFRESH GAID** to read it again.
- A GAME / APP / UNKNOWN label appears below each icon.
- Detects known AppsFlyer, Adjust, and Singular class signatures by inspecting the public DEX files inside each launcher's installed APK and split APKs. Multiple provider labels can appear under one app.
- Provider labels detected from APK code or Root preference-file scan are shown under the app icon. A label with an extracted local value is shown in white; tap that label to copy the exact stored value.
- Tap **SCAN WITH ROOT** to explicitly request a Superuser decision and scan the visible launcher apps' `shared_prefs` folders. Root is not requested on app launch.
- AppsFlyer: reads `AF_INSTALLATION` and `androidIdCached` from `appsflyer-data.xml`, looks up the app-scoped SSAID in Android's `settings_ssaid.xml`, and reads or creates a device-model User-Agent string in the same four-field record format used by the reference scanner.
- Singular: reads the `singular-id` value from `pref-singular-id.xml` when present.
- Adjust: detects `adjust_preferences.xml` and displays the Adjust label, but the supplied reference APK does not contain a confirmed Adjust-ID extraction rule. RENZO does not invent an Adjust ID.
- Extracted values are stored locally in app-private SharedPreferences. A white provider label has a value to copy; a blue label means the provider was detected but no value was extracted.
- No ads, analytics, or external UI dependencies beyond the official Google Play services identifier client. RENZO reads the GAID locally and does not send the value to a RENZO server.

## Important detection and Android limitations

The GAID shown at the top is the device/profile advertising identifier returned to RENZO by Google Play services; it is not a separate GAID belonging to each listed game. A device without Google Play services, or one where the advertising ID is unavailable/zeroed, may not return a usable value. The GAID API is called off the main thread.

The SDK signature scan looks for known class descriptors in installed APK DEX bytecode and does not need Root. The separate **SCAN WITH ROOT** action uses `su` to read only the known preference-file paths described above for apps visible in the launcher grid. The scan is local and explicit; it does not read app databases or runtime memory and does not transmit results over the network.

A detected label means a matching SDK class is bundled in the APK; it does not prove that the SDK is initialized, active, or responsible for a particular network request. Detection can miss SDKs if code is heavily obfuscated, dynamically delivered, repackaged, or stripped by the build. It may also find an SDK that was included but is not used. Apps without a launcher icon are not included in the grid.

A blue provider label is not an extracted identifier. AppsFlyer and Singular values are copied only when their named preference keys exist. The reference APK only checks for an Adjust preferences file; it does not establish which value is Adjust's ad ID, so an Adjust label can remain blue. Results are limited to the launcher apps shown in the grid. System apps or installed packages without launcher activities are not scanned.

## Export contract for an authorized source app

Send an explicit `ACTION_SEND` intent with the original record in `Intent.EXTRA_TEXT`, the provider in the extra `com.renzo.idscanner.extra.PROVIDER`, and launch RENZO with `startActivityForResult` so Android provides the calling package to the receiver. Supported provider values are `appsflyer`, `adjust`, and `singular`.

```java
Intent i = new Intent(Intent.ACTION_SEND)
    .setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT, exactDiagnosticRecord)
    .putExtra("com.renzo.idscanner.extra.PROVIDER", "appsflyer")
    .setPackage("com.renzo.idscanner");
startActivityForResult(i, 1001);
```

The receiver rejects exports when Android does not provide a verified calling package. It does not trust a package name typed into the payload or an unverified referrer value.

## Build

Requirements: JDK 17, Android SDK Platform 34, Android Build Tools 34.0.0, Gradle 8.7, and access to Google Maven to resolve `play-services-ads-identifier:18.3.0`.

```bash
gradle --no-daemon :app:assembleDebug :app:assembleRelease :sample:assembleDebug
```

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `sample/build/outputs/apk/debug/sample-debug.apk`

Release uses the personal key configured in `keystore.properties` when supplied. Otherwise it is a debug-signed test build, not a production release.

## Test the non-Android validation logic

```bash
mkdir -p /tmp/renzo-tests
javac -d /tmp/renzo-tests \
  app/src/main/java/com/renzo/idscanner/Provider.java \
  app/src/main/java/com/renzo/idscanner/RecordFormat.java \
  app/src/main/java/com/renzo/idscanner/ProviderSignatureDetector.java \
  app/src/main/java/com/renzo/idscanner/SdkScanner.java \
  app/src/main/java/com/renzo/idscanner/RootAttributionScanner.java \
  tools/test/RecordFormatTest.java \
  tools/test/ProviderSignatureDetectorTest.java \
  tools/test/SdkScannerTest.java \
  tools/test/RootAttributionScannerTest.java
java -cp /tmp/renzo-tests com.renzo.idscanner.RecordFormatTest
java -cp /tmp/renzo-tests com.renzo.idscanner.ProviderSignatureDetectorTest
java -cp /tmp/renzo-tests com.renzo.idscanner.SdkScannerTest
java -cp /tmp/renzo-tests com.renzo.idscanner.RootAttributionScannerTest
```

## End-to-end test

1. Install the scanner and sample exporter APKs.
2. Open RENZO ID Scanner. Apps whose installed APK code contains a recognized AppsFlyer, Adjust, or Singular class signature should show the corresponding blue provider label under the icon.
3. Apps with more than one recognized SDK should show all matching labels.
4. Open **RENZO Sample Exporter** and export each clearly labeled test fixture. The sample exporter's icon should then show exported-record labels in white.
5. Tap a white provider label and verify the exact corresponding fixture text appears on the clipboard.
6. Check that GAME / APP classification appears under launcher icons.
7. Check that DEVICE GAID appears at the top on a device with Google Play services; tap it to copy. Use **REFRESH GAID** to retry.
8. Tap **SCAN WITH ROOT** and respond to the Superuser prompt if the device has a root manager. Verify the status and any white copyable AppsFlyer / Singular labels.

The Android app must still be compiled and tested on an emulator or physical Android device before calling the APK complete.
