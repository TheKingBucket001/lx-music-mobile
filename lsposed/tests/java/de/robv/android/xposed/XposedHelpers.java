package de.robv.android.xposed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

// Test-only fake bridge: invokes the real original method, then the production Hook callback.
public final class XposedHelpers {
    public static final List<Registration> registrations = new ArrayList<>();

    public static final class Registration {
        public final Method method;
        public final XC_MethodHook callback;

        Registration(Method method, XC_MethodHook callback) {
            this.method = method;
            this.callback = callback;
        }

        public Object invoke(Object receiver, Object... args) throws Throwable {
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.thisObject = receiver;
            param.args = args;
            try {
                param.setResult(method.invoke(receiver, args));
            } catch (InvocationTargetException failure) {
                param.setThrowable(failure.getCause());
            }
            callback.afterHookedMethod(param);
            if (param.hasThrowable()) throw param.getThrowable();
            return param.getResult();
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> type, String name, Object... args) {
        try {
            if (args.length != 2 || !(args[0] instanceof Class<?>) || !(args[1] instanceof XC_MethodHook)) {
                throw new AssertionError("Unexpected hook signature");
            }
            Method method = type.getDeclaredMethod(name, (Class<?>) args[0]);
            XC_MethodHook callback = (XC_MethodHook) args[1];
            registrations.add(new Registration(method, callback));
            return callback.new Unhook();
        } catch (NoSuchMethodException failure) {
            throw new AssertionError(failure);
        }
    }
}
