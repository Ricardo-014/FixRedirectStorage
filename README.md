# Storage Isolation package visibility fix — experimental

This uses modern libxposed API 102. It leaves the original Storage Isolation APK and its signature untouched. It hooks the Android package visibility filter in `system_server`, changing only decisions whose calling UID is `10399` from filtered to visible.

## Build

On GitHub, use **Actions → Build LSPosed module → Run workflow**, then download the `storage-isolation-visibility-fix-debug` artifact. Install the debug APK. In LSPosed, enable the module with scope **System Framework (系统框架 / `system`)**, then reboot. Keep the original Storage Isolation APK installed.

Modern LSPosed treats `android` as Android System's dialog process; `system` is the `system_server` scope. Version 0.4 mistakenly declared `android`. Version 0.5 corrects it. If the manager does not allow System Framework to be checked after updating the APK, disable and re-enable the module, then inspect the scope again.

Before installing, verify the UID with `dumpsys package moe.shizuku.redirectstorage | grep userId=`. If it differs from 10399, edit `TARGET_UID` in `VisibilityHook.java` and rebuild.

After reboot, tap **Save logs to Download** in the module app. Check for `API 102 module loaded` and a positive hook count. Open Application Rule Management and check that apps appear. If it does not work, disable this module in LSPosed and reboot.

The GitHub Actions build verifies compilation and packaging. Device behavior has not been verified against the device's Xiaomi framework classes.
