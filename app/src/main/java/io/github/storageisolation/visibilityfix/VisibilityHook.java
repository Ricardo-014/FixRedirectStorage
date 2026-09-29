package io.github.storageisolation.visibilityfix;

import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class VisibilityHook extends XposedModule {
    private static final String TAG = "SIVisibilityFix";
    // Current installation. Recheck if the original app is reinstalled.
    private static final int TARGET_UID = 10399;

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        Log.i(TAG, "API 102 module loaded");
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        Log.i(TAG, "system_server starting; installing package visibility hooks");
        int count = 0;
        for (String name : new String[]{
                "com.android.server.pm.AppsFilterBase",
                "com.android.server.pm.AppsFilterImpl",
                "com.android.server.pm.AppsFilterSnapshotImpl"}) {
            try {
                Class<?> type = Class.forName(name, false, param.getClassLoader());
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals("shouldFilterApplication")
                            || method.getReturnType() != boolean.class
                            || Modifier.isAbstract(method.getModifiers())) continue;
                    Class<?>[] args = method.getParameterTypes();
                    int index = args.length >= 5 && args[1] == int.class ? 1
                            : args.length >= 4 && args[0] == int.class ? 0 : -1;
                    if (index < 0) continue;
                    hook(method).intercept(new VisibilityFilter(index));
                    count++;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable e) {
                Log.e(TAG, "Cannot hook " + name, e);
            }
        }
        Log.i(TAG, "installed " + count + " hooks for UID " + TARGET_UID);
    }

    private static final class VisibilityFilter implements XposedInterface.Hooker {
        private final int uidIndex;
        VisibilityFilter(int uidIndex) { this.uidIndex = uidIndex; }

        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object result = chain.proceed();
            if (Boolean.TRUE.equals(result) && Integer.valueOf(TARGET_UID).equals(chain.getArg(uidIndex))) {
                return false;
            }
            return result;
        }
    }
}
