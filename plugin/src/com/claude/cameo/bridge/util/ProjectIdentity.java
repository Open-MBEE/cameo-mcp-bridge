package com.claude.cameo.bridge.util;

import java.lang.ref.WeakReference;
import java.util.UUID;

/** Session identity distinguishes open projects even when copied models share IDs. */
public final class ProjectIdentity {
    private static WeakReference<Object> current = new WeakReference<>(null);
    private static String currentToken;
    private ProjectIdentity() {}
    public static synchronized String token(Object project) {
        if(current.get()!=project) {
            current = new WeakReference<>(project);
            currentToken = UUID.randomUUID().toString();
        }
        return currentToken;
    }
}
