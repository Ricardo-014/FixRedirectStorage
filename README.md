# Storage Isolation package visibility fix — experimental

This is a **source project**, not an installed or device-tested APK. It leaves
the original Storage Isolation APK and its signature untouched. It hooks the
Android package visibility filter in `system_server`, changing only decisions
whose calling UID is `10399` from filtered to visible.

## Build

On GitHub, use **Actions → Build LSPosed module → Run workflow**, then download
the `storage-isolation-visibility-fix-debug` artifact. Alternatively, open
this folder in Android Studio, install Android SDK 35 if prompted, and build
the `app` debug variant. Install `app/build/outputs/apk/debug/app-debug.apk`.
In LSPosed, enable the module with scope **System Framework** only, then reboot.
Keep the original Storage Isolation APK installed.

Before installing, verify the UID with `dumpsys package moe.shizuku.redirectstorage | grep userId=`.
If it differs from 10399, edit `TARGET_UID` in `VisibilityHook.java` and rebuild.

After reboot, run `logcat -d | grep SIVisibilityFix` and check for at least one
installed hook. Open Application Rule Management and check that apps appear.
If it does not work, disable this module in LSPosed and reboot. A wrong hook
in System Framework can affect system stability, so use LSPosed's recovery
procedure if the system cannot start.

This project has not been built in the current environment: Android SDK and
JDK compiler are unavailable here. It has not been verified against the
device's Xiaomi framework classes.
