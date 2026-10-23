package com.claude.cameo.bridge.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProjectIdentityTest {
    @Test public void projectIdentityIsStableUntilAnotherProjectIsObserved() {
        Object first=new Object(), second=new Object();
        String token=ProjectIdentity.token(first);
        assertEquals(token,ProjectIdentity.token(first));
        assertNotEquals(token,ProjectIdentity.token(second));
        assertNotEquals(token,ProjectIdentity.token(first));
    }
    @Test public void equalObjectsDoNotShareSessionIdentity() {
        Object first=new String("same source identity"), second=new String("same source identity");
        assertNotEquals(ProjectIdentity.token(first),ProjectIdentity.token(second));
    }
}
