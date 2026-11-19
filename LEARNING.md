# Learnings

## Toolchain on this machine

- System Java is 27 (runtime only); Gradle 8.11 needs JDK 21. Use
  `JAVA_HOME=$(mise where java@temurin-21.0.13+9.0.LTS)`.
- Android SDK lives in `~/Android/Sdk` (cmdline-tools, platform 35, build
  tools 34/35, emulator, `system-images;android-35;google_apis;x86_64`).
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
  breaks resource linking with AGP 8.7 despite minSdk 26.
