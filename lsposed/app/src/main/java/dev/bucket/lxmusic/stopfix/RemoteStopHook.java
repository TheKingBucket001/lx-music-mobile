package dev.bucket.lxmusic.stopfix;

import android.content.Intent;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class RemoteStopHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "cn.toside.music.mobile";
    private static final String EVENT_ACTION = "com.guichaguri.trackplayer.event";
    private static final String LOG_PREFIX = "LXMusicStopFix 0.2.1";

    private final AtomicInteger rewrittenReads = new AtomicInteger();
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
                        XposedBridge.log(LOG_PREFIX + " remote-stop -> remote-pause; reads="
                                + rewrittenReads.incrementAndGet());
                    }
                });
        installed = true;
        XposedBridge.log(LOG_PREFIX + " installed Intent.getStringExtra(String); process="
                + packageParam.processName);
    }
}
