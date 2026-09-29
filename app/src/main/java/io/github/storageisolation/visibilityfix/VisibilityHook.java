package io.github.storageisolation.visibilityfix;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class VisibilityHook implements IXposedHookLoadPackage {
    // Current device's app UID. Recheck with: dumpsys package moe.shizuku.redirectstorage | grep userId=
    private static final int TARGET_UID = 10399;
    private static final String TAG = "SIVisibilityFix: ";

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"android".equals(p.packageName) || !"android".equals(p.processName)) return;
        int hooks = 0;
        for (String name : new String[]{
                "com.android.server.pm.AppsFilterBase",
                "com.android.server.pm.AppsFilterImpl",
                "com.android.server.pm.AppsFilterSnapshotImpl"}) {
            try {
                Class<?> type = Class.forName(name, false, p.classLoader);
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals("shouldFilterApplication")
                            || method.getReturnType() != boolean.class
                            || Modifier.isAbstract(method.getModifiers())) continue;
                    Class<?>[] args = method.getParameterTypes();
                    // Android 17: (PackageDataSnapshot, int callingUid, Object,
                    // PackageStateInternal, int userId). Older versions have no snapshot.
                    int uidIndex = args.length >= 5 && args[1] == int.class ? 1
                            : args.length >= 4 && args[0] == int.class ? 0 : -1;
                    if (uidIndex < 0) continue;
                    final int index = uidIndex;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            if ((int) param.args[index] == TARGET_UID
                                    && Boolean.TRUE.equals(param.getResult())) {
                                param.setResult(false);
                            }
                        }
                    });
                    hooks++;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook failed in " + name + ": " + t);
            }
        }
        XposedBridge.log(TAG + "installed " + hooks + " filter hooks for UID " + TARGET_UID);
    }
}
