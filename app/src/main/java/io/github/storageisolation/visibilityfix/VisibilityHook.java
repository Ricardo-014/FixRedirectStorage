package io.github.storageisolation.visibilityfix;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class VisibilityHook extends XposedModule {
    private static final String TAG = "SIVisibilityFix";
    private static final String TARGET_PACKAGE = "moe.shizuku.redirectstorage";
    private static final int TARGET_UID = 10399;

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            Class<?> type = Class.forName("com.android.server.pm.ComputerEngine",
                    false, param.getClassLoader());
            Method method = type.getDeclaredMethod("getInstalledPackagesBody",
                    long.class, int.class, int.class);
            hook(method).intercept(chain -> {
                if (((Number) chain.getArg(2)).intValue() != TARGET_UID) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[2] = Integer.valueOf(1000);
                return chain.proceed(args);
            });
            log(Log.INFO, TAG, "Package enumeration hook installed");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Cannot hook package enumeration", error);
        }
    }

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> type = Class.forName(TARGET_PACKAGE + ".ef1", false, loader);
            Method method = type.getDeclaredMethod("尾巴捏捏", int.class, int.class);
            hook(method).intercept(chain -> {
                Object result = chain.proceed();
                if (result instanceof List && ((List<?>) result).isEmpty()) {
                    return recoverServiceRecords(loader, type,
                            (Integer) chain.getArg(0), result);
                }
                return result;
            });
            log(Log.INFO, TAG, "Empty service list fallback installed");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Cannot hook Storage Isolation service list", error);
        }
    }

    private Object recoverServiceRecords(ClassLoader loader, Class<?> serviceType,
            int flags, Object original) {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null);
            if (!(app instanceof Context)) return original;
            List<PackageInfo> installed = ((Context) app).getPackageManager()
                    .getInstalledPackages(0);
            Class<?> singleton = Class.forName(TARGET_PACKAGE + ".g10", false, loader);
            Method getService = singleton.getDeclaredMethod("嘟嘟噜");
            getService.setAccessible(true);
            Object service = getService.invoke(null);
            Method getOne = serviceType.getDeclaredMethod("没收门",
                    String.class, int.class, int.class);
            getOne.setAccessible(true);
            List<Object> recovered = new ArrayList<>();
            int failures = 0;
            for (PackageInfo info : installed) {
                if (info == null || info.packageName == null) continue;
                int userId = info.applicationInfo == null ? 0
                        : info.applicationInfo.uid / 100000;
                try {
                    // IService takes packageName, record flags, then userId.
                    // Keep the server's existing rule data rather than making default records.
                    Object record = getOne.invoke(service, info.packageName, flags, userId);
                    if (record != null) recovered.add(record);
                } catch (Throwable error) {
                    if (++failures == 1) {
                        log(Log.WARN, TAG, "Single package lookup failed", error);
                    }
                    if (failures >= 5) break;
                }
            }
            return recovered.isEmpty() ? original : recovered;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Cannot recover service records", error);
            return original;
        }
    }
}
