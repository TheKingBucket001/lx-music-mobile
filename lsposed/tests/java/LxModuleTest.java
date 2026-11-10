import android.app.AppComponentFactory;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import com.android.server.am.ServiceRecord;
import dev.bucket.lxmusic.stopfix.LxModule;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModule.Registration;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class LxModuleTest {
    private static final String TARGET = "cn.toside.music.mobile";
    private static final String SERVICE = "com.guichaguri.trackplayer.service.MusicService";
    private static final String ACTION = "com.guichaguri.trackplayer.event";
    private static int checks;

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    private static LxModule process(final boolean system, final String name) {
        XposedModule.reset();
        Build.VERSION.SDK_INT = 36;
        ServiceInfo.copyFailure = null;
        ServiceRecord.calls = 0;
        ServiceRecord.originalFailure = null;
        LxModule module = new LxModule();
        module.onModuleLoaded(new ModuleLoadedParam() {
            public boolean isSystemServer() { return system; }
            public String getProcessName() { return name; }
        });
        return module;
    }

    private static PackageReadyParam ready(final String pkg) {
        return new PackageReadyParam() {
            public String getPackageName() { return pkg; }
            public ApplicationInfo getApplicationInfo() { return new ApplicationInfo(); }
            public boolean isFirstPackage() { return true; }
            public ClassLoader getDefaultClassLoader() { return LxModuleTest.class.getClassLoader(); }
            public ClassLoader getClassLoader() { return getDefaultClassLoader(); }
            public AppComponentFactory getAppComponentFactory() { return null; }
        };
    }

    private static SystemServerStartingParam system(final ClassLoader loader) {
        return new SystemServerStartingParam() {
            public ClassLoader getClassLoader() { return loader; }
        };
    }

    private static void startSystem(LxModule module) {
        module.onSystemServerStarting(system(LxModuleTest.class.getClassLoader()));
    }

    private static Intent event(String action, String key, String value) {
        return new Intent(action).putExtra(key, value);
    }

    private static ServiceInfo targetInfo() {
        ServiceInfo info = new ServiceInfo();
        info.packageName = TARGET; info.name = SERVICE; info.processName = TARGET;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = TARGET;
        info.applicationInfo.targetSdkVersion = 29; info.applicationInfo.uid = 10322;
        info.flags = 0x400; info.permission = "test.original.permission";
        info.exported = true; info.enabled = true;
        return info;
    }

    private static Object[] arguments(ServiceInfo info) {
        ComponentName component = new ComponentName(TARGET, SERVICE);
        return new Object[] {null, component, component, TARGET, 10322, null,
                info, true, null, TARGET, -1, null, false};
    }

    private static void unchanged(Registration hook, Object[] args, String label) throws Throwable {
        Object original = args[6];
        ServiceRecord record = (ServiceRecord) hook.invoke(null, args);
        check(record.serviceInfo == original && args[6] == original && hook.originalCalls == 1, label);
    }

    private static void lifecycleAndApp() throws Throwable {
        LxModule other = process(false, "com.example.other");
        other.onPackageReady(ready("com.example.other"));
        other.onPackageReady(ready(TARGET));
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.isEmpty(),
                "unrelated process installs nothing even when LX package is ready");
        LxModule worker = process(false, TARGET + ":worker");
        worker.onPackageReady(ready(TARGET));
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.isEmpty(),
                "LX secondary process installs no hook or log");
        LxModule app = process(false, TARGET);
        app.onPackageLoaded(ready(TARGET));
        startSystem(app);
        app.onPackageReady(ready(TARGET + ".other"));
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.isEmpty(),
                "app waits for exact package-ready and rejects system-start callback");
        app.onPackageReady(ready(TARGET));
        app.onPackageReady(ready(TARGET));
        check(XposedModule.registrations.size() == 1 && XposedModule.hookAttempts == 1,
                "LX main role attempts installation exactly once");
        Registration hook = XposedModule.registrations.get(0);
        Method method = (Method) hook.executable;
        check(method.getDeclaringClass() == Intent.class && method.getName().equals("getStringExtra")
                        && method.getReturnType() == String.class && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0] == String.class,
                "only exact framework Intent.getStringExtra(String) is hooked");
        check(hook.exceptionMode == ExceptionMode.PASSTHROUGH,
                "app interceptor explicitly passes original exceptions through framework");
        check(XposedModule.logs.size() == 1 && XposedModule.logs.get(0).contains("LXMusicStopFix 0.3.0")
                        && XposedModule.logs.get(0).contains("installed"),
                "one app installation log identifies version 0.3.0");
        Intent stop = event(ACTION, "event", "remote-stop").putExtra("other", "unchanged");
        check("remote-pause".equals(hook.invoke(stop, "event")) && hook.originalCalls == 1,
                "matching Stop becomes Pause after exactly one original getter call");
        check("remote-stop".equals(stop.getStringExtra("event"))
                        && "unchanged".equals(stop.getStringExtra("other")) && ACTION.equals(stop.getAction()),
                "Intent data and action remain unchanged");
        check("remote-pause".equals(hook.invoke(stop, "event")), "repeated Stop read still becomes Pause");
        check(XposedModule.logs.size() == 2 && XposedModule.logs.get(1).contains("first match"),
                "matching reads log only their first conversion");
        boolean converted = true;
        for (int i = 0; i < 1000; i++) converted &= "remote-pause".equals(hook.invoke(stop, "event"));
        check(converted && XposedModule.logs.size() == 2,
                "1000 later Stop reads convert within the two-log app budget");
        check("remote-stop".equals(hook.invoke(event("unrelated.action", "event", "remote-stop"), "event")),
                "unrelated action is unchanged");
        check("remote-stop".equals(hook.invoke(event(null, "event", "remote-stop"), "event")),
                "null action is unchanged");
        check("remote-stop".equals(hook.invoke(event(ACTION, "other", "remote-stop"), "other")),
                "unrelated extra key is unchanged");
        check(hook.invoke(stop, "missing") == null, "missing extra remains null");
        check(hook.invoke(event(ACTION, "event", null), "event") == null, "null event remains null");
        check("remote-play".equals(hook.invoke(event(ACTION, "event", "remote-play"), "event")),
                "Play event is unchanged");
        check("remote-pause".equals(hook.invoke(event(ACTION, "event", "remote-pause"), "event")),
                "Pause event is unchanged");
        check("playback-state".equals(hook.invoke(event(ACTION, "event", "playback-state"), "event")),
                "playback state event is unchanged");
        check("REMOTE-STOP".equals(hook.invoke(event(ACTION, "event", "REMOTE-STOP"), "event")),
                "event matching is case-sensitive");
        Intent failing = event(ACTION, "event", "remote-stop");
        RuntimeException failure = new IllegalStateException("Original getter failure");
        failing.readFailure = failure;
        boolean sameFailure = false;
        try { hook.invoke(failing, "event"); } catch (Throwable actual) { sameFailure = actual == failure; }
        check(sameFailure && hook.originalCalls == 1, "original getter exception propagates once without replacement");
        check(XposedModule.logs.size() == 2, "nonmatches and original exceptions add no rewrite logs");
        check(XposedModule.registrations.size() == 1 && !method.getName().contains("destroy")
                        && !method.getName().contains("exit"), "no destroy or exit hook is installed");
        failing = event(ACTION, "event", "remote-stop");
        failing.actionFailure = new IllegalStateException("Hook-side metadata failure");
        boolean preserved = true;
        for (int i = 0; i < 1000; i++) preserved &= "remote-stop".equals(hook.invoke(failing, "event"));
        check(preserved && hook.originalCalls == 1 && XposedModule.logs.size() == 3,
                "app callback failure retains original result and logs only one extra failure");
        check(XposedModule.apiVersionReads == 0, "lifecycle dispatch does not hard-code a runtime API-version gate");

        app = process(false, TARGET);
        XposedModule.logFailure = new IllegalStateException("Logger unavailable");
        app.onPackageReady(ready(TARGET));
        hook = XposedModule.registrations.get(0);
        check("remote-pause".equals(hook.invoke(stop, "event")) && XposedModule.logs.isEmpty(),
                "unavailable logger cannot block installation or first event conversion");

        app = process(false, TARGET);
        XposedModule.hookFailure = new IllegalStateException("Framework install failure");
        app.onPackageReady(ready(TARGET)); app.onPackageReady(ready(TARGET));
        check(XposedModule.hookAttempts == 1 && XposedModule.registrations.isEmpty()
                        && XposedModule.logs.size() == 1 && XposedModule.logs.get(0).contains("failed"),
                "failed app installation is attempted once and logged once");
    }

    private static void systemMetadata() throws Throwable {
        LxModule module = process(true, "system_server");
        module.onPackageReady(ready(TARGET)); module.onPackageReady(ready("android"));
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.isEmpty(),
                "system role never installs app getter from package lifecycle callbacks");
        startSystem(module); startSystem(module);
        check(XposedModule.registrations.size() == 1 && XposedModule.hookAttempts == 1,
                "system-start role installs exactly once");
        Registration hook = XposedModule.registrations.get(0);
        check(hook.executable instanceof Constructor && hook.executable.getDeclaringClass() == ServiceRecord.class
                        && hook.executable.getParameterTypes().length == 13,
                "only verified complete 13-argument ServiceRecord constructor is hooked");
        check(hook.exceptionMode == ExceptionMode.PASSTHROUGH,
                "system interceptor explicitly preserves original constructor exceptions");
        check(XposedModule.registrations.size() == 1,
                "system role installs no getter, destroy, exit or permission bypass hook");
        ServiceInfo original = targetInfo();
        Object[] args = arguments(original); Object[] previous = args.clone();
        ServiceRecord record = (ServiceRecord) hook.invoke(null, args);
        ServiceInfo copy = record.serviceInfo;
        check(ServiceRecord.calls == 1 && hook.originalCalls == 1 && copy != original
                        && copy.getForegroundServiceType() == 2,
                "original constructor runs once with copied LX metadata and mediaPlayback bit 2");
        check(original.getForegroundServiceType() == 0, "shared original ServiceInfo stays unchanged");
        check(copy.flags == original.flags && copy.permission.equals(original.permission)
                        && copy.enabled == original.enabled && copy.exported == original.exported
                        && copy.packageName.equals(original.packageName) && copy.name.equals(original.name)
                        && copy.processName.equals(original.processName) && copy.applicationInfo == original.applicationInfo
                        && copy.applicationInfo.uid == 10322 && copy.applicationInfo.targetSdkVersion == 29,
                "all other service and application metadata is retained");
        boolean preserved = true;
        for (int i = 0; i < args.length; i++) preserved &= args[i] == previous[i];
        check(preserved && hook.proceededArgs != args && hook.proceededArgs[6] == copy,
                "constructor receives a replacement args copy while original args remain untouched");
        boolean othersSame = true;
        for (int i = 0; i < args.length; i++) if (i != 6) othersSame &= hook.proceededArgs[i] == previous[i];
        check(othersSame, "only replacement array index 6 differs from the original arguments");
        check(XposedModule.logs.size() == 2 && XposedModule.logs.get(0).contains("0.3.0")
                        && XposedModule.logs.get(0).contains("installed ServiceRecord")
                        && XposedModule.logs.get(1).contains("mediaPlayback 0x0 -> 0x2"),
                "installation and first metadata rewrite logs are distinguishable");
        unchanged(hook, arguments(copy), "already corrected metadata is not copied again");
        boolean corrected = true;
        for (int i = 0; i < 1000; i++) {
            ServiceInfo next = targetInfo(); Object[] nextArgs = arguments(next);
            ServiceRecord later = (ServiceRecord) hook.invoke(null, nextArgs);
            corrected &= later.serviceInfo != next && later.serviceInfo.getForegroundServiceType() == 2
                    && next.getForegroundServiceType() == 0 && nextArgs[6] == next;
        }
        check(corrected && XposedModule.logs.size() == 2,
                "1000 later LX service records are corrected within the two-log system budget");
        ServiceInfo other = targetInfo(); other.packageName += ".other";
        unchanged(hook, arguments(other), "another package is unchanged");
        other = targetInfo(); other.name += "Other";
        unchanged(hook, arguments(other), "same-package other service is unchanged");
        other = targetInfo(); other.processName += ":worker";
        unchanged(hook, arguments(other), "metadata for another process is unchanged");
        other = targetInfo(); other.applicationInfo.packageName += ".other";
        unchanged(hook, arguments(other), "application package mismatch is unchanged");
        other = targetInfo(); other.applicationInfo = null;
        unchanged(hook, arguments(other), "missing application metadata is unchanged");
        other = targetInfo(); other.applicationInfo.targetSdkVersion = 34;
        unchanged(hook, arguments(other), "future target SDK is unchanged without bypassing permission checks");
        other = targetInfo(); other.mForegroundServiceType = 1;
        unchanged(hook, arguments(other), "other declared foreground types are retained");
        unchanged(hook, arguments(null), "null metadata reaches the original constructor unchanged");
        args = arguments(targetInfo()); args[9] = TARGET + ":other";
        unchanged(hook, args, "actual constructor process mismatch is unchanged");
        args = arguments(targetInfo()); args[1] = new ComponentName("other", SERVICE);
        unchanged(hook, args, "component package mismatch is unchanged");
        args = arguments(targetInfo()); args[1] = new ComponentName(TARGET, SERVICE + "Other");
        unchanged(hook, args, "component class mismatch is unchanged");
        args = arguments(targetInfo()); args[2] = new ComponentName(TARGET, SERVICE + "Other");
        unchanged(hook, args, "instance component mismatch is unchanged");
        args = arguments(targetInfo()); args[2] = null;
        unchanged(hook, args, "null instance component is unchanged");
        RuntimeException failure = new IllegalStateException("Original constructor failure");
        ServiceRecord.originalFailure = failure;
        boolean same = false;
        try { hook.invoke(null, arguments(targetInfo())); } catch (Throwable actual) { same = actual == failure; }
        check(same && hook.originalCalls == 1, "original constructor exception propagates once without replacement");
        ServiceRecord.originalFailure = null;
        ServiceInfo.copyFailure = new IllegalStateException("Metadata copy failure");
        boolean fellBack = true;
        for (int i = 0; i < 1000; i++) {
            ServiceInfo next = targetInfo();
            fellBack &= ((ServiceRecord) hook.invoke(null, arguments(next))).serviceInfo == next;
        }
        check(fellBack && hook.originalCalls == 1 && XposedModule.logs.size() == 3,
                "metadata-copy failures preserve original constructor path and log once");

        module = process(true, "android"); startSystem(module);
        check(XposedModule.registrations.size() == 1, "system lifecycle authority accepts classic android name");
        module = process(false, "system_server"); startSystem(module);
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.isEmpty(),
                "system-looking process name without system role cannot install constructor hook");
        module = process(true, "system_server"); Build.VERSION.SDK_INT = 35;
        startSystem(module); startSystem(module);
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.size() == 1
                        && XposedModule.logs.get(0).contains("unsupported SDK"),
                "unverified Android version logs once and installs nothing");
        module = process(true, "system_server");
        SystemServerStartingParam missing = system(new ClassLoader(null) {});
        module.onSystemServerStarting(missing); startSystem(module);
        check(XposedModule.registrations.isEmpty() && XposedModule.logs.size() == 1
                        && XposedModule.logs.get(0).contains("installation failed"),
                "missing OEM class logs once and never retries or changes the system");
        module = process(true, "system_server");
        XposedModule.hookFailure = new IllegalStateException("Framework install failure");
        startSystem(module); startSystem(module);
        check(XposedModule.hookAttempts == 1 && XposedModule.logs.size() == 1,
                "failed system registration attempts once per process");
    }

    public static void main(String[] ignored) throws Throwable {
        lifecycleAndApp(); systemMetadata();
        System.out.println("Passed " + checks + " production modern-API JVM checks.");
        System.out.println("Real API compilation and APK inspection are separate; ART/Bluetooth need device verification.");
    }
}
