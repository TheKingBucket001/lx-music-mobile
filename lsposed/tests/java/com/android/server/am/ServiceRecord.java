package com.android.server.am;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;

// Test-only shape of the two OEM constructors, not an Android system implementation.
public final class ServiceRecord {
    public static int calls;
    public static RuntimeException originalFailure;
    public final ServiceInfo serviceInfo;

    ServiceRecord(ActivityManagerService ams, ComponentName name, ComponentName instanceName,
            String definingPackage, int definingUid, Intent.FilterComparison intent,
            ServiceInfo info, boolean foreground, Runnable restarter) {
        this(ams, name, instanceName, definingPackage, definingUid, intent, info,
                foreground, restarter, info.processName, -1, null, false);
    }

    ServiceRecord(ActivityManagerService ams, ComponentName name, ComponentName instanceName,
            String definingPackage, int definingUid, Intent.FilterComparison intent,
            ServiceInfo info, boolean foreground, Runnable restarter, String process,
            int sandboxUid, String sandboxPackage, boolean sharedIsolated) {
        calls++;
        if (originalFailure != null) throw originalFailure;
        serviceInfo = info;
    }
}
