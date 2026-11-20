# Learnings

## Toolchain on this machine

- System Java is 27 (runtime only); run Gradle 9.9 on JDK 21. Use
  `JAVA_HOME=$(mise where java@temurin-21.0.13+9.0.LTS)`.
- Android SDK lives in `~/Android/Sdk` (cmdline-tools, platforms 35 and
  37.2, build tools 36, emulator,
  `system-images;android-35;google_apis;x86_64`). Install packages with
  `yes | sdkmanager --install 'platforms;android-37.2'`; AGP fetches its
  default build tools on its own.
- AVDs are created under `~/.config/.android/avd`, but the emulator looks
  in `~/.android/avd`. Start it with
  `ANDROID_AVD_HOME=~/.config/.android/avd emulator -avd monthly -no-window
  -gpu swiftshader_indirect`.
- `/dev/kvm` is usable without sudo, so the x86_64 emulator runs headless.

## Emulator testing

- `adb root` then `setprop persist.sys.timezone Europe/Berlin` changes
  the zone for apps; SystemUI keeps showing the old clock.
- `file://` URIs cannot be read by the app (no storage permission). Test
  imports through the document picker, which hands out `content://`.
- `uiautomator dump` + bounds gives tap coordinates; grid labels and list
  rows share text, so pick the last match for list rows.
- Snackbars time out after about 4 s; tap Undo right away in scripts.
- `dumpsys notification --noredact` shows posted titles and texts;
  `dumpsys alarm` shows whether an exact alarm was allowed.

## Android lint

- The POST_NOTIFICATIONS check must sit in the same function as
  `notify()`, or lint reports MissingPermission.
- Moving the adaptive icon from `mipmap-anydpi-v26` to `mipmap-anydpi`
  breaks resource linking (AGP 8.7 and 9.5) despite minSdk 26.
  `app/lint.xml` silences the ObsoleteSdkInt warning for that folder.

## Build setup

- AGP 9 compiles Kotlin itself; applying the Kotlin Android plugin to the
  app module is an error. The root lists it with `apply false` only to
  pin the Kotlin version (AGP alone pulls an older KGP).
- A library's required compileSdk sits in its AAR at
  `META-INF/com/android/build/gradle/aar-metadata.properties`. Compose
  from BOM 2026.11 needs 37.1 or later:
  `compileSdk { version = release(37) { minorApiLevel = 2 } }`.
- Material 3 1.5 deprecates `ListItem(headlineContent = ...)`; the new
  overloads take the headline as trailing `content` and an `onClick`
  instead of `Modifier.clickable`.

## Robolectric

- Runs inside `testDebugUnitTest`; the first run downloads the
  android-all jar for the SDK in `robolectric.properties`.
- Opening SQLite the first time takes seconds. Open it in `@Before` when
  a test times work on another thread.
