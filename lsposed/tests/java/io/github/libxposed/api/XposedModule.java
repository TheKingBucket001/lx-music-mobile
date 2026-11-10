package io.github.libxposed.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Fake framework dispatches production interceptors around the actual Java original.
public abstract class XposedModule implements XposedModuleInterface, XposedInterface {
    public static final List<Registration> registrations = new ArrayList<>();
    public static final List<String> logs = new ArrayList<>();
    public static RuntimeException hookFailure;
    public static RuntimeException logFailure;
    public static int hookAttempts;
    public static int apiVersionReads;

    public static void reset() {
        registrations.clear(); logs.clear(); hookFailure = null; logFailure = null;
        hookAttempts = 0; apiVersionReads = 0;
    }

    public final int getApiVersion() { apiVersionReads++; return 102; }

    public final HookBuilder hook(final Executable executable) {
        hookAttempts++;
        if (hookFailure != null) throw hookFailure;
        return new HookBuilder() {
            private ExceptionMode mode = ExceptionMode.DEFAULT;
            public HookBuilder setExceptionMode(ExceptionMode next) { mode = next; return this; }
            public HookHandle intercept(Hooker hooker) {
                registrations.add(new Registration(executable, hooker, mode));
                return new HookHandle() {};
            }
        };
    }

    public final void log(int priority, String tag, String message) {
        if (logFailure != null) throw logFailure;
        if (priority != android.util.Log.INFO) throw new AssertionError("Unexpected log level");
        logs.add(tag + " " + message);
    }

    public final void log(int priority, String tag, String message, Throwable error) {
        log(priority, tag, message + " " + error);
    }

    public static final class Registration {
        public final Executable executable;
        public final Hooker hooker;
        public final ExceptionMode exceptionMode;
        public Object[] originalArgs;
        public Object[] proceededArgs;
        public int originalCalls;

        Registration(Executable executable, Hooker hooker, ExceptionMode mode) {
            this.executable = executable; this.hooker = hooker; this.exceptionMode = mode;
        }

        public Object invoke(final Object receiver, final Object... args) throws Throwable {
            originalArgs = args;
            proceededArgs = null;
            originalCalls = 0;
            return hooker.intercept(new Chain() {
                public Executable getExecutable() { return executable; }
                public Object getThisObject() { return receiver; }
                public List<Object> getArgs() { return Collections.unmodifiableList(Arrays.asList(args)); }
                public Object getArg(int index) { return args[index]; }
                public Object proceed() throws Throwable { return callOriginal(receiver, args); }
                public Object proceed(Object[] replacement) throws Throwable {
                    return callOriginal(receiver, replacement);
                }
            });
        }

        private Object callOriginal(Object receiver, Object[] args) throws Throwable {
            proceededArgs = args;
            originalCalls++;
            executable.setAccessible(true);
            try {
                if (executable instanceof Method) return ((Method) executable).invoke(receiver, args);
                return ((Constructor<?>) executable).newInstance(args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
