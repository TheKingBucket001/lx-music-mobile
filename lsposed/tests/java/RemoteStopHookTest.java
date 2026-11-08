import android.content.Intent;
import dev.bucket.lxmusic.stopfix.RemoteStopHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class RemoteStopHookTest {
    private static final String TARGET = "cn.toside.music.mobile";
    private static final String ACTION = "com.guichaguri.trackplayer.event";
    private static int checks;

    private static XC_LoadPackage.LoadPackageParam loadParam(String pkg, String process) {
        XC_LoadPackage.LoadPackageParam param = new XC_LoadPackage.LoadPackageParam();
        param.packageName = pkg;
        param.processName = process;
        return param;
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    private static Intent event(String action, String key, String value) {
        return new Intent(action).putExtra(key, value);
    }

    public static void main(String[] args) throws Throwable {
        RemoteStopHook hook = new RemoteStopHook();
        hook.handleLoadPackage(loadParam("com.example.other", "com.example.other"));
        hook.handleLoadPackage(loadParam(TARGET + ".other", TARGET));
        hook.handleLoadPackage(loadParam(TARGET, TARGET + ":worker"));
        hook.handleLoadPackage(loadParam(TARGET, "com.example.other"));
        check(XposedHelpers.registrations.isEmpty() && XposedBridge.logs.isEmpty(),
                "other packages and non-main processes register no hook or log");

        hook.handleLoadPackage(loadParam(TARGET, TARGET));
        hook.handleLoadPackage(loadParam(TARGET, TARGET));
        check(XposedHelpers.registrations.size() == 1, "main process installs exactly once");
        XposedHelpers.Registration registration = XposedHelpers.registrations.get(0);
        check(registration.method.getDeclaringClass() == Intent.class
                        && registration.method.getName().equals("getStringExtra")
                        && registration.method.getReturnType() == String.class
                        && registration.method.getParameterTypes().length == 1
                        && registration.method.getParameterTypes()[0] == String.class,
                "only exact framework Intent.getStringExtra(String) is hooked");
        check(XposedBridge.logs.size() == 1 && XposedBridge.logs.get(0).contains("0.2.2")
                        && XposedBridge.logs.get(0).contains("installed"),
                "one installation log includes module version");

        Intent stop = event(ACTION, "event", "remote-stop").putExtra("other", "unchanged");
        check("remote-pause".equals(registration.invoke(stop, "event")), "matching Stop becomes Pause");
        check("remote-stop".equals(stop.getStringExtra("event"))
                        && "unchanged".equals(stop.getStringExtra("other")) && ACTION.equals(stop.getAction()),
                "Intent data and action remain unchanged");
        check("remote-pause".equals(registration.invoke(stop, "event")), "repeated Stop read still becomes Pause");
        check(XposedBridge.logs.size() == 2 && XposedBridge.logs.get(1).contains("first match"),
                "matching reads log only the first conversion");
        boolean repeatedReadsConverted = true;
        for (int i = 0; i < 1000; i++) {
            repeatedReadsConverted &= "remote-pause".equals(registration.invoke(stop, "event"));
        }
        check(repeatedReadsConverted && XposedBridge.logs.size() == 2,
                "1000 later Stop reads still convert without additional logs");

        check("remote-stop".equals(registration.invoke(event("unrelated.action", "event", "remote-stop"), "event")),
                "unrelated action is unchanged");
        check("remote-stop".equals(registration.invoke(event(null, "event", "remote-stop"), "event")),
                "null action is unchanged");
        check("remote-stop".equals(registration.invoke(event(ACTION, "other", "remote-stop"), "other")),
                "unrelated extra key is unchanged");
        check(registration.invoke(event(ACTION, "event", "remote-stop"), "missing") == null,
                "missing extra remains null");
        check(registration.invoke(event(ACTION, "event", null), "event") == null,
                "null event remains null");
        check("remote-play".equals(registration.invoke(event(ACTION, "event", "remote-play"), "event")),
                "Play event is unchanged");
        check("remote-pause".equals(registration.invoke(event(ACTION, "event", "remote-pause"), "event")),
                "Pause event is unchanged");
        check("playback-state".equals(registration.invoke(event(ACTION, "event", "playback-state"), "event")),
                "playback state event is unchanged");
        check("REMOTE-STOP".equals(registration.invoke(event(ACTION, "event", "REMOTE-STOP"), "event")),
                "event matching is case-sensitive");

        Intent failing = event(ACTION, "event", "remote-stop");
        RuntimeException originalFailure = new IllegalStateException("Original read failure");
        failing.readFailure = originalFailure;
        boolean sameFailure = false;
        try {
            registration.invoke(failing, "event");
        } catch (Throwable failure) {
            sameFailure = failure == originalFailure;
        }
        check(sameFailure, "original Intent exception is preserved without replacement");
        check(XposedBridge.logs.size() == 2, "nonmatches and failed reads do not log a rewrite");

        // Structural check only: real Android native teardown is outside this JVM test.
        // User Exit does not use this Intent event route, and no destroy/exit hook is installed.
        check(XposedHelpers.registrations.size() == 1
                        && !registration.method.getName().contains("destroy")
                        && !registration.method.getName().contains("exit"),
                "no destroy or exit hook is registered");
        System.out.println("Passed " + checks + " JVM checks using the production RemoteStopHook.java.");
        System.out.println("Android/ART Hook dispatch, Bluetooth behavior and original APK lifecycle require device verification.");
    }
}
