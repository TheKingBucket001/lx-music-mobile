import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import com.android.server.am.ServiceRecord;
import dev.bucket.lxmusic.stopfix.MediaServiceTypeHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Field;

public final class MediaServiceTypeHookTest {
    private static final String TARGET = "cn.toside.music.mobile";
    private static final String SERVICE = "com.guichaguri.trackplayer.service.MusicService";
    private static int checks;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        System.out.println("PASS " + label);
    }

    private static XC_LoadPackage.LoadPackageParam load(String pkg, String process) {
        XC_LoadPackage.LoadPackageParam p = new XC_LoadPackage.LoadPackageParam();
        p.packageName = pkg;
        p.processName = process;
        p.classLoader = MediaServiceTypeHookTest.class.getClassLoader();
        return p;
    }

    // Simulate a fresh system process; the production installation guard remains private.
    private static void freshProcess() throws Exception {
        Field field = MediaServiceTypeHook.class.getDeclaredField("installationAttempted");
        field.setAccessible(true);
        field.setBoolean(null, false);
        XposedBridge.constructors.clear();
        XposedBridge.logs.clear();
        ServiceRecord.calls = 0;
        ServiceRecord.originalFailure = null;
        Build.VERSION.SDK_INT = 36;
    }

    private static ServiceInfo targetInfo() {
        ServiceInfo info = new ServiceInfo();
        info.packageName = TARGET;
        info.name = SERVICE;
        info.processName = TARGET;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = TARGET;
        info.applicationInfo.targetSdkVersion = 29;
        info.applicationInfo.uid = 10322;
        info.flags = 0x400;
        info.permission = "test.original.permission";
        info.exported = true;
        info.enabled = true;
        return info;
    }

    private static Object[] arguments(ServiceInfo info) {
        ComponentName component = new ComponentName(TARGET, SERVICE);
        return new Object[] {null, component, component, TARGET, 10322, null,
                info, true, null, TARGET, -1, null, false};
    }

    private static void unchanged(XposedBridge.ConstructorRegistration hook,
            Object[] args, String label) throws Throwable {
        Object original = args[6];
        ServiceRecord record = (ServiceRecord) hook.invoke(args);
        check(record.serviceInfo == original && args[6] == original, label);
    }

    public static void main(String[] ignored) throws Throwable {
        freshProcess();
        MediaServiceTypeHook module = new MediaServiceTypeHook();
        module.handleLoadPackage(load(TARGET, TARGET));
        module.handleLoadPackage(load("other", "system_server"));
        module.handleLoadPackage(load("android", "android:other"));
        check(XposedBridge.constructors.isEmpty() && XposedBridge.logs.isEmpty(),
                "system hook ignores LX, other packages and non-system processes");
        module.handleLoadPackage(load("android", "android"));
        module.handleLoadPackage(load("android", "system_server"));
        check(XposedBridge.constructors.size() == 1, "system installs once for classic android process name");
        XposedBridge.ConstructorRegistration hook = XposedBridge.constructors.get(0);
        check(hook.constructor.getDeclaringClass() == ServiceRecord.class
                        && hook.constructor.getParameterTypes().length == 13,
                "only the verified complete ServiceRecord constructor is hooked");
        check(XposedHelpers.registrations.isEmpty(), "system entry installs no app getter or teardown hooks");

        ServiceInfo original = targetInfo();
        Object[] args = arguments(original);
        Object[] previousArgs = args.clone();
        ServiceRecord record = (ServiceRecord) hook.invoke(args);
        ServiceInfo copy = record.serviceInfo;
        check(ServiceRecord.calls == 1 && copy != original && copy.getForegroundServiceType() == 2,
                "original constructor runs with copied LX metadata and mediaPlayback bit 2");
        check(original.getForegroundServiceType() == 0, "shared original ServiceInfo stays unchanged");
        check(copy.flags == original.flags && copy.permission.equals(original.permission)
                        && copy.enabled == original.enabled && copy.exported == original.exported
                        && copy.packageName.equals(original.packageName) && copy.name.equals(original.name)
                        && copy.processName.equals(original.processName)
                        && copy.applicationInfo == original.applicationInfo
                        && copy.applicationInfo.uid == 10322 && copy.applicationInfo.targetSdkVersion == 29,
                "all other service and application metadata is retained");
        boolean unchangedArgs = true;
        for (int i = 0; i < args.length; i++) if (i != 6) unchangedArgs &= args[i] == previousArgs[i];
        check(unchangedArgs, "only the ServiceInfo constructor argument is replaced");
        check(XposedBridge.logs.size() == 2 && XposedBridge.logs.get(0).contains("0.2.0")
                        && XposedBridge.logs.get(0).contains("installed ServiceRecord")
                        && XposedBridge.logs.get(1).contains("mediaPlayback 0x0 -> 0x2"),
                "installation and metadata rewrite logs are distinguishable");
        unchanged(hook, arguments(copy), "already corrected metadata is not copied again");

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
        unchanged(hook, arguments(null), "null metadata is passed through to the original constructor");
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

        RuntimeException failure = new IllegalStateException("Original system constructor failure");
        ServiceRecord.originalFailure = failure;
        boolean sameException = false;
        try { hook.invoke(arguments(targetInfo())); }
        catch (Throwable actual) { sameException = actual == failure; }
        check(sameException, "original system constructor exception remains intact");

        freshProcess();
        module.handleLoadPackage(load("android", "system_server"));
        check(XposedBridge.constructors.size() == 1, "system_server process alias is accepted");
        freshProcess(); Build.VERSION.SDK_INT = 35;
        module.handleLoadPackage(load("android", "android"));
        check(XposedBridge.constructors.isEmpty() && XposedBridge.logs.get(0).contains("unsupported SDK"),
                "unverified Android version logs unsupported and installs nothing");

        freshProcess();
        XC_LoadPackage.LoadPackageParam missing = load("android", "android");
        missing.classLoader = new ClassLoader(null) {};
        module.handleLoadPackage(missing);
        check(XposedBridge.constructors.isEmpty() && XposedBridge.logs.size() == 1
                        && XposedBridge.logs.get(0).contains("installation failed"),
                "missing OEM system class leaves the system unchanged without throwing");
        System.out.println("Passed " + checks + " system metadata JVM checks.");
        System.out.println("Real LSPosed dispatch and system dumpsys types=0x2 still require device verification.");
    }
}
