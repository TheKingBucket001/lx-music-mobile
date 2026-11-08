package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

public final class XposedBridge {
    public static final List<String> logs = new ArrayList<>();
    public static final List<ConstructorRegistration> constructors = new ArrayList<>();

    public static void log(String text) { logs.add(text); }

    public static XC_MethodHook.Unhook hookMethod(Member member, XC_MethodHook callback) {
        if (!(member instanceof Constructor<?>)) throw new AssertionError("Expected a constructor");
        constructors.add(new ConstructorRegistration((Constructor<?>) member, callback));
        return callback.new Unhook();
    }

    public static final class ConstructorRegistration {
        public final Constructor<?> constructor;
        public final XC_MethodHook callback;

        ConstructorRegistration(Constructor<?> constructor, XC_MethodHook callback) {
            this.constructor = constructor;
            this.callback = callback;
        }

        public Object invoke(Object... args) throws Throwable {
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.method = constructor;
            param.args = args;
            callback.beforeHookedMethod(param);
            if (param.hasThrowable()) throw param.getThrowable();
            constructor.setAccessible(true);
            try {
                return constructor.newInstance(param.args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
