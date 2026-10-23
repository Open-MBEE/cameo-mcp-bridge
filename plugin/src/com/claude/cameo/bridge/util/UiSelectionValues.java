package com.claude.cameo.bridge.util;

import com.google.gson.JsonArray;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Normalizes the array/collection/scalar selection shapes used by Cameo releases. */
public final class UiSelectionValues {
    private static final Logger LOG = Logger.getLogger(UiSelectionValues.class.getName());

    private UiSelectionValues() {}

    public static List<Object> items(Object value) {
        List<Object> items = new ArrayList<>();
        if (value == null) return items;
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) items.add(Array.get(value, i));
        } else if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) items.add(item);
        } else {
            items.add(value);
        }
        return items;
    }

    /** A successful null result means no selection; do not probe unsupported aliases. */
    public static Object firstSupported(Object target, JsonArray warnings, String... methodNames) {
        if (target == null) return null;
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException | SecurityException error) {
                String kind = error instanceof InvocationTargetException
                        && ((InvocationTargetException) error).getCause() != null
                        ? ((InvocationTargetException) error).getCause().getClass().getSimpleName()
                        : error.getClass().getSimpleName();
                if (warnings != null) warnings.add(JsonCompat.string(target.getClass().getName() + "." + methodName
                        + " unavailable (" + kind + ")"));
                // Failure class is useful; exception messages may contain model content.
                LOG.log(Level.FINE, "UI accessor {0}.{1} unavailable ({2})",
                        new Object[]{target.getClass().getName(), methodName, kind});
            }
        }
        return null;
    }
}
