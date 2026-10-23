package com.claude.cameo.bridge.util;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.claude.cameo.bridge.util.PrimaryModelTraversal.Ownership.*;

public class PrimaryModelTraversalTest {
    static class Node {
        final List<Node> children = new ArrayList<>();
        PrimaryModelTraversal.Ownership owner = PRIMARY;
        // Equal model values must still be counted as distinct object identities.
        @Override public boolean equals(Object other) { return other instanceof Node; }
        @Override public int hashCode() { return 1; }
    }
    private PrimaryModelTraversal.Result<Node> walk(Node root, int count, int steps) {
        return PrimaryModelTraversal.collect(root, n -> n.owner, n -> n.children.iterator(), count, steps, 5_000_000_000L);
    }
    @Test public void attachedLibraryIsPrunedBeforeItsThousandsOfContentsAreVisited() {
        Node root = new Node(), block = new Node(), library = new Node();
        library.owner = ATTACHED;
        for (int i=0; i<6000; i++) library.children.add(new Node());
        root.children.addAll(List.of(block, library));
        var result = walk(root, 2, 10);
        assertEquals(2, result.objects().size());
        assertSame(block, result.objects().get(1));
        assertEquals(1, result.attached().size());
        assertEquals(3, result.encounters());
    }
    @Test public void repeatedContainmentsAndCyclesDoNotInflateSourceCount() {
        Node root = new Node(), child = new Node();
        root.children.addAll(List.of(child, child)); child.children.add(root);
        var result = walk(root, 2, 10);
        assertEquals(2, result.objects().size()); assertEquals(2, result.repeated());
    }
    @Test public void genuinelyLargePrimaryProjectStillRefusesWithCounts() {
        Node root = new Node(); for(int i=0; i<5000; i++) root.children.add(new Node());
        var error = assertThrows(IllegalArgumentException.class, () -> walk(root, 5000, 50000));
        assertTrue(error.getMessage().contains("primary=5001"));
        assertTrue(error.getMessage().contains("bounded primary-project fingerprint"));
    }
    @Test public void unknownOwnershipIsNotSilentlyOmitted() {
        Node root = new Node(), unknown = new Node(); unknown.owner = UNKNOWN; root.children.add(unknown);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> walk(root, 10, 10)).getMessage().contains("cannot establish project ownership"));
    }
    @Test public void workLimitAppliesEvenToDuplicateEntries() {
        Node root = new Node(); for (int i=0; i<20; i++) root.children.add(root);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> walk(root, 5, 10)).getMessage().contains("work limit"));
    }
}
