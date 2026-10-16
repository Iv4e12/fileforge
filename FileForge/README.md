# FileForge

Offline Android file reader/editor (Kotlin + Jetpack Compose, minSdk 24, compileSdk/targetSdk 34).
No permissions, no internet access, no accounts, ads or tracking. Files are opened through the
Storage Access Framework only.

## Build
Requirements: Android Studio Koala (2024.1) or newer (or JDK 17+, Android SDK Platform 34, Gradle 8.7).

Android Studio: File > Open > select this folder > let Gradle sync > Build > Build APK(s).

Command line (Gradle 8.7+ installed, ANDROID_HOME set):

    gradle wrapper --gradle-version 8.7
    ./gradlew assembleDebug testDebugUnitTest

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Layout
- `model/` file type detection
- `io/` SAF access (`FileRepository`), text encoding (`TextCodec`), PDF rendering (`PdfDoc`)
- `parse/` CSV parser/serializer
- `editor/` document state, undo/redo, search/replace, CSV edits
- `data/` recent files + settings
- `ui/` Compose screens
- `AppViewModel`, `MainActivity` wiring and navigation
