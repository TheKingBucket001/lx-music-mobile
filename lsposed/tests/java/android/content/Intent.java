package android.content;

import java.util.HashMap;
import java.util.Map;

// JVM test double only; never part of the Android module source set.
public final class Intent {
    public static final class FilterComparison {}
    private final String action;
    private final Map<String, String> extras = new HashMap<>();
    public RuntimeException readFailure;

    public Intent(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public Intent putExtra(String key, String value) {
        extras.put(key, value);
        return this;
    }

    public String getStringExtra(String key) {
        if (readFailure != null) throw readFailure;
        return extras.get(key);
    }
}
