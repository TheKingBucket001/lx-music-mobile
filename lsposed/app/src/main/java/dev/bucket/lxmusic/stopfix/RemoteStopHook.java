package dev.bucket.lxmusic.stopfix;

import android.content.Intent;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class RemoteStopHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "cn.toside.music.mobile";
    private static final String EVENT_ACTION = "com.guichaguri.trackplayer.event";
    private static final String LOG_PREFIX = "LXMusicStopFix 0.2.2";

    private final AtomicBoolean rewriteLogged = new AtomicBoolean();
    private boolean installed;

    @Override
    public synchronized void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) {
        if (!TARGET_PACKAGE.equals(packageParam.packageName)
                || !TARGET_PACKAGE.equals(packageParam.processName) || installed) {
            return;
        }

        // Intercept the event before JS handles it. Blocking native destroy is too late:
        // TrackPlayer's JS destroy() has already cleared its own initialization flag.
        XposedHelpers.findAndHookMethod(Intent.class, "getStringExtra", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || !"event".equals(param.args[0])
                                || !"remote-stop".equals(param.getResult())) {
                            return;
                        }
                        Intent intent = (Intent) param.thisObject;
                        if (!EVENT_ACTION.equals(intent.getAction())) {
                            return;
                        }
                        param.setResult("remote-pause");
                        if (rewriteLogged.compareAndSet(false, true)) {
                            log("remote-stop -> remote-pause (first match)");
                        }
                    }
                });
        installed = true;
        log("installed Intent.getStringExtra(String); process=" + packageParam.processName);
    }

    private static void log(String message) {
        try {
            XposedBridge.log(LOG_PREFIX + " " + message);
        } catch (Throwable ignored) {
            // Logging must not interfere with the event conversion.
        }
    }
}
