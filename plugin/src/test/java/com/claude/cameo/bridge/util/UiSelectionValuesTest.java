package com.claude.cameo.bridge.util;

import com.google.gson.JsonArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class UiSelectionValuesTest {
    @Test
    public void normalizesCameoArrayAndOtherSelectionShapes() {
        assertEquals(List.of("one", "two"), UiSelectionValues.items(new String[]{"one", "two"}));
        assertEquals(List.of("one", "two"), UiSelectionValues.items(List.of("one", "two")));
        assertEquals(List.of("one"), UiSelectionValues.items("one"));
        assertTrue(UiSelectionValues.items(new String[0]).isEmpty());
        assertTrue(UiSelectionValues.items(null).isEmpty());
    }

    @Test
    public void successfulArrayAccessorDoesNotProbeFallbacks() {
        ArrayTree tree = new ArrayTree();
        JsonArray warnings = new JsonArray();
        Object selected = UiSelectionValues.firstSupported(tree, warnings, "getSelectedNodes", "getSelectedNode");
        assertEquals(List.of("ExamplePackage"), UiSelectionValues.items(selected));
        assertEquals(0, tree.fallbackCalls);
        assertEquals(0, warnings.size());
    }

    @Test
    public void successfulNullIsAnEmptySelectionNotAnUnavailableApi() {
        JsonArray warnings = new JsonArray();
        assertNull(UiSelectionValues.firstSupported(new EmptyTree(), warnings, "getSelectedNodes", "missing"));
        assertEquals(0, warnings.size());
    }

    @Test
    public void missingAndThrowingAccessorsFallbackWithContentFreeReasons() {
        JsonArray warnings = new JsonArray();
        Object selected = UiSelectionValues.firstSupported(new ScalarTree(), warnings,
                "missing", "broken", "getSelectedNode");
        assertEquals("ExamplePackage", selected);
        assertEquals(2, warnings.size());
        assertTrue(warnings.toString().contains("NoSuchMethodException"));
        assertTrue(warnings.toString().contains("IllegalStateException"));
        assertFalse(warnings.toString().contains("PRIVATE MODEL CONTENT"));
    }

    public static class ArrayTree {
        int fallbackCalls;
        public String[] getSelectedNodes() { return new String[]{"ExamplePackage"}; }
        public String getSelectedNode() { fallbackCalls++; return "wrong"; }
    }
    public static class EmptyTree {
        public Object getSelectedNodes() { return null; }
    }
    public static class ScalarTree {
        public Object broken() { throw new IllegalStateException("PRIVATE MODEL CONTENT"); }
        public String getSelectedNode() { return "ExamplePackage"; }
    }
}
