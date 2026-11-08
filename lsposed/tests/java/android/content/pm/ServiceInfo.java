package android.content.pm;

public final class ServiceInfo {
    public String packageName;
    public String name;
    public String processName;
    public ApplicationInfo applicationInfo;
    public int mForegroundServiceType;
    public int flags;
    public String permission;
    public boolean exported;
    public boolean enabled;

    public ServiceInfo() {}

    public ServiceInfo(ServiceInfo other) {
        packageName = other.packageName;
        name = other.name;
        processName = other.processName;
        applicationInfo = other.applicationInfo;
        mForegroundServiceType = other.mForegroundServiceType;
        flags = other.flags;
        permission = other.permission;
        exported = other.exported;
        enabled = other.enabled;
    }

    public int getForegroundServiceType() { return mForegroundServiceType; }
}
