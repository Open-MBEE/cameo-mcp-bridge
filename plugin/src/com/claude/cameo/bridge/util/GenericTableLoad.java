package com.claude.cameo.bridge.util;

import com.nomagic.generictable.GenericTableManager;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Diagram;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeoutException;

/** 2024x adds an explicit table load wait; 2022x exposes refresh only. */
public final class GenericTableLoad {
    private GenericTableLoad() {}
    private static Method waitMethod() {
        try {
            return GenericTableManager.class.getMethod("waitForCellValuesLoad", Diagram.class);
        } catch (NoSuchMethodException absentOn2022x) {
            return null;
        }
    }
    public static boolean supportsWait() { return waitMethod() != null; }
    public static void await(Diagram diagram) throws TimeoutException, InterruptedException {
        Method method = waitMethod();
        if (method == null) return;
        try {
            method.invoke(null, diagram);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof TimeoutException) throw (TimeoutException) cause;
            if (cause instanceof InterruptedException) throw (InterruptedException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Table cell load wait failed", cause);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Table cell load wait inaccessible", error);
        }
    }
}
