package io.github.storageisolation.visibilityfix;

import android.util.Log;
import android.os.Binder;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class VisibilityHook extends XposedModule {
    private static final String TAG = "SIVisibilityFix";
    private static final int TARGET_UID = 10399;
    private static final AtomicInteger observed = new AtomicInteger();
    private static final AtomicInteger target = new AtomicInteger();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        Log.i(TAG, "API 102 module loaded");
        log(Log.INFO, TAG, "API 102 module loaded");
    }


    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (!"moe.shizuku.redirectstorage".equals(param.getPackageName())) return;
        try {
            Class<?> type = Class.forName("moe.shizuku.redirectstorage.ef1", false,
                    param.getDefaultClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (args.length != 2 || args[0] != int.class || args[1] != int.class
                        || method.getReturnType() != java.util.List.class) continue;
                final String methodName = method.getName();
                hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof List && ((List<?>) result).isEmpty()) {
                        result = recoverServiceRecords(param.getDefaultClassLoader(),
                                type, (Integer) chain.getArg(0), result);
                    }
                    String size = result instanceof java.util.List
                            ? String.valueOf(((java.util.List<?>) result).size())
                            : String.valueOf(result);
                    String message = "app service list " + methodName + "("
                            + chain.getArg(0) + "," + chain.getArg(1) + ") size=" + size;
                    Log.i(TAG, message);
                    log(Log.INFO, TAG, message);
                    return result;
                });
                log(Log.INFO, TAG, "app hook installed: " + method.toGenericString());
            }
        } catch (Throwable e) {
            log(Log.ERROR, TAG, "Cannot hook Storage Isolation service list", e);
        }
    }

    private Object recoverServiceRecords(ClassLoader loader, Class<?> serviceType,
            int flags, Object original) {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getDeclaredMethod("currentApplication").invoke(null);
            if (!(app instanceof Context)) return original;
            PackageManager pm = ((Context) app).getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(0);
            Class<?> singleton = Class.forName("moe.shizuku.redirectstorage.g10", false, loader);
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
                    Object record = getOne.invoke(service, info.packageName, userId, flags);
                    if (record != null) recovered.add(record);
                } catch (Throwable error) {
                    if (++failures == 1) log(Log.WARN, TAG, "single package lookup failed", error);
                    if (failures >= 5) break;
                }
            }
            String report = "fallback installed=" + installed.size()
                    + " nativeRecords=" + recovered.size() + " failures=" + failures
                    + " flags=" + flags;
            log(Log.INFO, TAG, report);
            Log.i(TAG, report);
            return recovered.isEmpty() ? original : recovered;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Cannot recover service records", error);
            return original;
        }
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        Log.i(TAG, "system_server starting; installing package visibility hooks");
        log(Log.INFO, TAG, "system_server starting; installing package visibility hooks");
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
                    Log.i(TAG, "candidate " + method.toGenericString() + " uidIndex=" + index);
                    if (index < 0) continue;
                    hook(method).intercept(new VisibilityFilter(index));
                    count++;
                }
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable e) {
                Log.e(TAG, "Cannot hook " + name, e);
                log(Log.ERROR, TAG, "Cannot hook " + name, e);
            }
        }
        count += hookInstalledPackagesBody(param.getClassLoader());
        Log.i(TAG, "installed " + count + " hooks for UID " + TARGET_UID);
        log(Log.INFO, TAG, "installed " + count + " hooks for UID " + TARGET_UID);
    }

    private int hookInstalledPackagesBody(ClassLoader loader) {
        try {
            Class<?> type = Class.forName("com.android.server.pm.ComputerEngine", false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("getInstalledPackagesBody")
                        || Modifier.isAbstract(method.getModifiers())) continue;
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 3 || p[0] != long.class
                        || p[1] != int.class || p[2] != int.class) continue;
                hook(method).intercept(new PackageListHook());
                Log.i(TAG, "hooked " + method.toGenericString());
                log(Log.INFO, TAG, "hooked " + method.toGenericString());
                return 1;
            }
            Log.w(TAG, "getInstalledPackagesBody(long,int,int) not found");
        } catch (Throwable e) {
            Log.e(TAG, "Cannot hook ComputerEngine.getInstalledPackagesBody", e);
            log(Log.ERROR, TAG, "Cannot hook ComputerEngine.getInstalledPackagesBody", e);
        }
        return 0;
    }

    private static final class PackageListHook implements XposedInterface.Hooker {
        private static final AtomicInteger calls = new AtomicInteger();
        private static final AtomicInteger rootCalls = new AtomicInteger();
        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
            int callingUid = ((Number) chain.getArg(2)).intValue();
            boolean app = callingUid == TARGET_UID;
            long flags = ((Number) chain.getArg(0)).longValue();
            boolean rootQuery = callingUid == 0
                    && (flags == 4096L || flags == 12288L);
            boolean service = rootQuery && isStorageIsolationCaller();
            if (rootQuery && rootCalls.getAndIncrement() < 50) {
                Log.i(TAG, "root package query pid=" + Binder.getCallingPid()
                        + " flags=" + flags + " service=" + service);
            }
            if (!app && !service) return chain.proceed();
            Object[] args = chain.getArgs().toArray();
            args[2] = Integer.valueOf(1000);
            Object result = chain.proceed(args);
            int n = calls.getAndIncrement();
            if (n < 12) {
                String size = "unknown";
                try {
                    Object list = result.getClass().getMethod("getList").invoke(result);
                    if (list instanceof java.util.List) size = String.valueOf(((java.util.List<?>) list).size());
                } catch (Throwable ignored) { }
                Log.i(TAG, "package list #" + n + " uid=" + callingUid
                        + " callerPid=" + Binder.getCallingPid() + " effectiveUid=1000 size=" + size);
            }
            return result;
        }
    }

    private static boolean isStorageIsolationCaller() {
        int pid = Binder.getCallingPid();
        if (pid <= 0) return false;
        try (FileInputStream in = new FileInputStream("/proc/" + pid + "/cmdline")) {
            byte[] buf = new byte[128];
            int n = in.read(buf);
            if (n <= 0) return false;
            int end = 0;
            while (end < n && buf[end] != 0) end++;
            return "storage_isolation".equals(new String(buf, 0, end,
                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class VisibilityFilter implements XposedInterface.Hooker {
        private final int uidIndex;
        VisibilityFilter(int uidIndex) { this.uidIndex = uidIndex; }

        @Override public Object intercept(XposedInterface.Chain chain) throws Throwable {
            Object uid = chain.getArg(uidIndex);
            if (Integer.valueOf(TARGET_UID).equals(uid)) {
                int n = target.getAndIncrement();
                if (n < 20) Log.i(TAG, "target call #" + n + " bypassed before original filter");
                return false;
            }
            Object result = chain.proceed();
            if (observed.getAndIncrement() < 8) {
                Log.i(TAG, "observed uidArg=" + uid + " result=" + result);
            }
            return result;
        }
    }
}
