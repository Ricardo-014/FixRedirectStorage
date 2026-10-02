# Storage Isolation visibility fix (API 102)

Compatibility fix for the original Storage Isolation APK on the tested Android 17 Xiaomi device. Version 0.14 restored the application list on that device; version 0.15 removed visibility hooks too aggressively and showed fewer apps. Version 0.16 restores target-only application visibility while retaining the other cleanup.

## Minimal hooks

Three operations are required on the tested framework:

1. `ComputerEngine.getInstalledPackagesBody(long, int, int)`: for the Storage Isolation UID only, use UID 1000 for package enumeration.
2. `AppsFilter.shouldFilterApplication`: bypass filtering only for the Storage Isolation UID so its UI can resolve application resources and ApplicationInfo by package name.
3. `ef1.尾巴捏捏(int, int)` in Storage Isolation: keep normal results; if the service returns an empty list, enumerate package names and request each original record through `ef1.没收门(packageName, flags, userId)`.

Existing rule records come from the original service. The module does not fabricate rules or write the rule database. Root service process detection and verbose diagnostic tracing remain removed. A short fallback count is logged to distinguish package enumeration from server record retrieval.

## Install and build

Keep the original Storage Isolation APK. Install the module APK, enable it in LSPosed, and select the Android system / `system` scope plus `moe.shizuku.redirectstorage`. Reboot after updating. This uses modern libxposed API 102.

The tested Storage Isolation UID is **10399**. If reinstalling it changes the UID, update `TARGET_UID` in `VisibilityHook.java` and rebuild. App-side class and method names are tied to the analyzed APK version.

GitHub Actions builds the APK on pushes to main, or through **Actions → Build LSPosed module → Run workflow**. Download the `storage-isolation-visibility-fix-debug` artifact.

The module app retains **Save logs to Download**. It needs root only when exporting logs. Runtime logs contain hook setup and failures.

GitHub Actions validates compilation and packaging. Version 0.16 still needs a device check after installation to confirm the application count regression is resolved.
