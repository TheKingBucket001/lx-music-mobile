package de.robv.android.xposed;

import java.util.ArrayList;
import java.util.List;

public final class XposedBridge {
    public static final List<String> logs = new ArrayList<>();

    public static void log(String text) { logs.add(text); }
}
