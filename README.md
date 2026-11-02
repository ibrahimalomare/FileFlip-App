# FileFlip (basic version)

Offline Android app that converts a PDF into images (PNG or JPG), one image per page.

## Status
Source code only. **Not yet compiled or tested** — it was written without access to an Android build environment.
Treat the feature as unverified until you've built it and tried it on your phone.

## Supported
- PDF -> PNG, PDF -> JPG (uses Android's built-in `PdfRenderer`, no external libraries, no network)

## Not supported (yet)
Everything else: Word/Excel/PowerPoint, OCR, image-to-PDF, batch, history, Arabic/RTL.
Password-protected PDFs show an error message.

## Architecture
- `MainActivity.kt` – Compose UI (Material 3, follows system dark mode)
- `MainViewModel.kt` – state (StateFlow), file validation, error messages
- `PdfConverter.kt` – conversion engine; saves to `Pictures/FileFlip/<name>_<time>/` via MediaStore (no permissions needed, minSdk 29)

## Get the APK (GitHub Actions)
1. Create a new GitHub repository and push this folder to it.
2. Open the **Actions** tab -> "Build debug APK" -> wait for the green check.
3. Open the run, download the **FileFlip-debug-apk** artifact, unzip it.
4. Copy `app-debug.apk` to your phone and install it (allow "install unknown apps").

## Build locally
Open the folder in Android Studio and press Run (it will create the Gradle wrapper), or run `gradle assembleDebug` with JDK 17.
Output: `app/build/outputs/apk/debug/app-debug.apk`.
