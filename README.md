# Storage Isolation package visibility fix — experimental

This uses modern libxposed API 102. It leaves
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

After reboot, run `logcat -b all -d | grep SIVisibilityFix` and check for
`API 102 module loaded` and a positive hook count. Also check LSPosed's
module log if Android logcat shows nothing. Open Application Rule Management
and check that apps appear.
If it does not work, disable this module in LSPosed and reboot. A wrong hook
in System Framework can affect system stability, so use LSPosed's recovery
procedure if the system cannot start.

The GitHub Actions build verifies compilation and packaging. Device behavior
has not been verified against the device's Xiaomi framework classes.
