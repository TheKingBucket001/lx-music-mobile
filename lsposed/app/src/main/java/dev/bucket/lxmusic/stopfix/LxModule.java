package dev.bucket.lxmusic.stopfix;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;

public final class LxModule extends XposedModule {
    private static final String TARGET_PACKAGE = "cn.toside.music.mobile";
    private static final String TARGET_SERVICE = "com.guichaguri.trackplayer.service.MusicService";
    private static final String EVENT_ACTION = "com.guichaguri.trackplayer.event";
    private static final String LOG_PREFIX = "LXMusicStopFix 0.3.0";
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

    private final AtomicBoolean rewriteLogged = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private String processName;
    private boolean systemServer;
    private boolean installationAttempted;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
    }

    @Override
    public synchronized void onPackageReady(PackageReadyParam param) {
        if (systemServer || installationAttempted
                || !TARGET_PACKAGE.equals(param.getPackageName())
                || !TARGET_PACKAGE.equals(processName)) return;
        installationAttempted = true;
        try {
            hook(Intent.class.getMethod("getStringExtra", String.class))
                    .setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        // Read first so the original method's exception is always preserved.
                        Object result = chain.proceed();
                        try {
                            if ("event".equals(chain.getArg(0)) && "remote-stop".equals(result)
                                    && EVENT_ACTION.equals(((Intent) chain.getThisObject()).getAction())) {
                                logRewriteOnce("remote-stop -> remote-pause (first match)");
                                return "remote-pause";
                            }
                        } catch (Throwable failure) {
                            logFailureOnce("event callback failed", failure);
                        }
                        return result;
                    });
            logMessage("installed Intent.getStringExtra(String); process=" + processName);
        } catch (Throwable failure) {
            logFailureOnce("event hook installation failed", failure);
        }
    }

    @Override
    public synchronized void onSystemServerStarting(SystemServerStartingParam param) {
        if (!systemServer || installationAttempted) return;
        installationAttempted = true;
        if (Build.VERSION.SDK_INT != 36) {
            logMessage("unsupported SDK " + Build.VERSION.SDK_INT + "; system hook not installed");
            return;
        }
        try {
            Class<?> recordClass = Class.forName(
                    "com.android.server.am.ServiceRecord", false, param.getClassLoader());
            Constructor<?> target = null;
            for (Constructor<?> constructor : recordClass.getDeclaredConstructors()) {
                if (!matchesConstructor(constructor)) continue;
                if (target != null) {
                    logMessage("unsupported ServiceRecord constructor shape; system hook not installed");
                    return;
                }
                target = constructor;
            }
            if (target == null) {
                logMessage("unsupported ServiceRecord constructor shape; system hook not installed");
                return;
            }
            final Field typeField = ServiceInfo.class.getField("mForegroundServiceType");
            if (typeField.getType() != int.class || Modifier.isStatic(typeField.getModifiers())) {
                logMessage("unsupported ServiceInfo type field; system hook not installed");
                return;
            }
            hook(target).setExceptionMode(ExceptionMode.PASSTHROUGH).intercept(chain -> {
                Object[] replacementArgs = null;
                try {
                    ServiceInfo original = (ServiceInfo) chain.getArg(6);
                    if (original != null
                            && TARGET_PACKAGE.equals(original.packageName)
                            && TARGET_SERVICE.equals(original.name)
                            && matchesComponent(chain.getArg(1))
                            && matchesComponent(chain.getArg(2))
                            && TARGET_PACKAGE.equals(original.processName)
                            && TARGET_PACKAGE.equals(chain.getArg(9))
                            && original.applicationInfo != null
                            && TARGET_PACKAGE.equals(original.applicationInfo.packageName)
                            && original.applicationInfo.targetSdkVersion == 29
                            && typeField.getInt(original) == 0) {
                        // Replace only this record's metadata; shared PackageManager data stays intact.
                        ServiceInfo replacement = new ServiceInfo(original);
                        typeField.setInt(replacement, 2); // mediaPlayback
                        replacementArgs = chain.getArgs().toArray();
                        replacementArgs[6] = replacement;
                    }
                } catch (Throwable failure) {
                    logFailureOnce("system callback failed", failure);
                }
                // Keep the original constructor and its exceptions outside the module's catch.
                if (replacementArgs == null) return chain.proceed();
                Object result = chain.proceed(replacementArgs);
                logRewriteOnce("mediaPlayback 0x0 -> 0x2 (first match); "
                        + TARGET_PACKAGE + "/" + TARGET_SERVICE);
                return result;
            });
            logMessage("installed ServiceRecord(ServiceInfo at argument 6, 13 parameters)");
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

    private void logRewriteOnce(String message) {
        if (rewriteLogged.compareAndSet(false, true)) logMessage(message);
    }

    private void logFailureOnce(String message, Throwable failure) {
        if (failureLogged.compareAndSet(false, true)) {
            logMessage(message + ": " + failure.getClass().getSimpleName());
        }
    }

    private void logMessage(String message) {
        try {
            log(Log.INFO, LOG_PREFIX, message);
        } catch (Throwable ignored) {
            // Logging must never interrupt playback or system service construction.
        }
    }
}
