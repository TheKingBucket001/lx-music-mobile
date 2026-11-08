package de.robv.android.xposed;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final class MethodHookParam {
        public Object thisObject;
        public java.lang.reflect.Member method;
        public Object[] args;
        private Object result;
        private Throwable throwable;

        public Object getResult() { return result; }
        public boolean hasThrowable() { return throwable != null; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable value) { throwable = value; result = null; }
        public void setResult(Object value) { result = value; throwable = null; }
    }

    public final class Unhook {}
}
