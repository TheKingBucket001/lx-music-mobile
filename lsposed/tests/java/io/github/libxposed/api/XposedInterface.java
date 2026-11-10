package io.github.libxposed.api;

import java.lang.reflect.Executable;
import java.util.List;

// Minimal API 102 signatures, checked separately against the real API jar.
public interface XposedInterface {
    enum ExceptionMode { DEFAULT, PROTECTIVE, PASSTHROUGH }
    interface Chain {
        Executable getExecutable();
        Object getThisObject();
        List<Object> getArgs();
        Object getArg(int index);
        Object proceed() throws Throwable;
        Object proceed(Object[] args) throws Throwable;
    }
    interface Hooker { Object intercept(Chain chain) throws Throwable; }
    interface HookHandle {}
    interface HookBuilder {
        HookBuilder setExceptionMode(ExceptionMode mode);
        HookHandle intercept(Hooker hooker);
    }
}
