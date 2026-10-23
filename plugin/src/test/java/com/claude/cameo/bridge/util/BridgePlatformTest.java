package com.claude.cameo.bridge.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class BridgePlatformTest {
    @Test public void packagedTargetAndTestJvmMatchSelectedSdk() {
        String target = System.getProperty("expectedCameoTarget");
        assertEquals(target, BridgePlatform.target());
        int expected = "2022x".equals(target) ? 11 : 17;
        assertEquals(expected, BridgePlatform.javaRelease());
        assertEquals("Tests must use the matching platform JVM", expected, Runtime.version().feature());
        assertTrue(BridgePlatform.acceptsHost(target));
        if ("2022x".equals(target)) {
            assertFalse(BridgePlatform.acceptsHost("2024x"));
            assertFalse(BridgePlatform.acceptsHost("2022xBAD"));
            assertTrue(BridgePlatform.acceptsHost("2022x Refresh2"));
        }
    }
}
