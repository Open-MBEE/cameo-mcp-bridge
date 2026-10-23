package com.claude.cameo.bridge.util;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BridgePortTest {
    @Test public void usesSavedPortUnlessVmOverrideIsPresent() {
        assertEquals(18740, BridgePort.resolve(null, BridgePort.DEFAULT));
        assertEquals(19000, BridgePort.resolve(null, 19000));
        assertEquals(19001, BridgePort.resolve("19001", 19000));
        assertEquals(1, BridgePort.resolve(null, 1));
        assertEquals(65535, BridgePort.resolve("65535", 19000));
    }

    @Test public void refusesInvalidPortsRatherThanBindingAnUnexpectedPort() {
        for (String value : new String[] {"", "abc", "0", "-1", "65536", "18740.5"}) {
            assertThrows(IllegalArgumentException.class, () -> BridgePort.resolve(value, 18740));
        }
        assertThrows(IllegalArgumentException.class, () -> BridgePort.resolve(null, 0));
        assertThrows(IllegalArgumentException.class, () -> BridgePort.resolve(null, 65536));
    }
}
