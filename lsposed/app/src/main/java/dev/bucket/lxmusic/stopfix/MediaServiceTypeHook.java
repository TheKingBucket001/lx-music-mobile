package dev.bucket.lxmusic.stopfix;

import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MediaServiceTypeHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "cn.toside.music.mobile";
    private static final String TARGET_SERVICE =
            "com.guichaguri.trackplayer.service.MusicService";
    private static final String LOG_PREFIX = "LXMusicStopFix 0.2.1";
    private static final int MEDIA_PLAYBACK = 2;
    private static final String[] CONSTRUCTOR_TYPES = {
            "com.android.server.am.ActivityManagerService",
            "android.content.ComponentName",
            "android.content.ComponentName",
            "java.lang.String",
            "int",
            "android.content.Intent$FilterComparison",
            "android.content.pm.ServiceInfo",
            "boolean",
            "java.lang.Runnable",
            "java.lang.String",
            "int",
            "java.lang.String",
            "boolean"
    };
    private static final AtomicBoolean failureLogged = new AtomicBoolean();
    private static boolean installationAttempted;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) {
        if (!"android".equals(packageParam.packageName)
                || !("android".equals(packageParam.processName)
                || "system_server".equals(packageParam.processName))) {
            return;
        }
        install(packageParam.classLoader);
    }

    private static synchronized void install(ClassLoader classLoader) {
        if (installationAttempted) return;
        installationAttempted = true;
        if (Build.VERSION.SDK_INT != 36) {
            log("unsupported SDK " + Build.VERSION.SDK_INT + "; system hook not installed");
            return;
        }
        try {
            Class<?> recordClass = Class.forName(
                    "com.android.server.am.ServiceRecord", false, classLoader);
            Constructor<?> target = null;
            for (Constructor<?> constructor : recordClass.getDeclaredConstructors()) {
                if (!matchesConstructor(constructor)) continue;
                if (target != null) {
                    log("unsupported ServiceRecord constructor shape; system hook not installed");
                    return;
                }
                target = constructor;
            }
            if (target == null) {
                log("unsupported ServiceRecord constructor shape; system hook not installed");
                return;
            }
            final Field typeField = ServiceInfo.class.getField("mForegroundServiceType");
            if (typeField.getType() != int.class || Modifier.isStatic(typeField.getModifiers())) {
                log("unsupported ServiceInfo type field; system hook not installed");
                return;
            }
            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        ServiceInfo original = (ServiceInfo) param.args[6];
                        if (original == null
                                || !TARGET_PACKAGE.equals(original.packageName)
                                || !TARGET_SERVICE.equals(original.name)
                                || !matchesComponent(param.args[1])
                                || !matchesComponent(param.args[2])
                                || !TARGET_PACKAGE.equals(original.processName)
                                || !TARGET_PACKAGE.equals(param.args[9])
                                || original.applicationInfo == null
                                || !TARGET_PACKAGE.equals(original.applicationInfo.packageName)
                                || original.applicationInfo.targetSdkVersion != 29
                                || typeField.getInt(original) != 0) {
                            return;
                        }
                        // Copy only the record's metadata: PackageManager's original stays intact.
                        // The constructor must continue so all system checks and exceptions apply.
                        ServiceInfo replacement = new ServiceInfo(original);
                        typeField.setInt(replacement, MEDIA_PLAYBACK);
                        param.args[6] = replacement;
                        log("mediaPlayback 0x0 -> 0x2; " + TARGET_PACKAGE + "/" + TARGET_SERVICE);
                    } catch (Throwable failure) {
                        logFailureOnce("system callback failed", failure);
                    }
                }
            });
            log("installed ServiceRecord(ServiceInfo at argument 6, 13 parameters)");
        } catch (Throwable failure) {
            logFailureOnce("system hook installation failed", failure);
        }
    }

    private static boolean matchesConstructor(Constructor<?> constructor) {
        Class<?>[] actual = constructor.getParameterTypes();
        if (actual.length != CONSTRUCTOR_TYPES.length) return false;
        for (int index = 0; index < actual.length; index++) {
            if (!CONSTRUCTOR_TYPES[index].equals(actual[index].getName())) return false;
        }
        return true;
    }

    private static boolean matchesComponent(Object value) {
        if (!(value instanceof ComponentName)) return false;
        ComponentName component = (ComponentName) value;
        return TARGET_PACKAGE.equals(component.getPackageName())
                && TARGET_SERVICE.equals(component.getClassName());
    }

    private static void logFailureOnce(String message, Throwable failure) {
        if (failureLogged.compareAndSet(false, true)) {
            log(message + ": " + failure.getClass().getSimpleName());
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(LOG_PREFIX + " " + message);
        } catch (Throwable ignored) {
            // Diagnostic logging must never interrupt system service construction.
        }
    }
}
