package com.claude.cameo.bridge.util;

import java.util.*;
import java.util.function.Function;

/** Identity-based containment walk; attached projects are boundaries, not source contents. */
public final class PrimaryModelTraversal {
    private PrimaryModelTraversal() {}
    public enum Ownership { PRIMARY, ATTACHED, UNKNOWN }
    public static final class Result<T> {
        private final List<T> objects, attached;
        private final int encounters, repeated;
        public Result(List<T> objects, List<T> attached, int encounters, int repeated) {
            this.objects=List.copyOf(objects); this.attached=List.copyOf(attached);
            this.encounters=encounters; this.repeated=repeated;
        }
        public List<T> objects() { return objects; }
        public List<T> attached() { return attached; }
        public int encounters() { return encounters; }
        public int repeated() { return repeated; }
    }

    public static <T> Result<T> collect(T root, Function<T, Ownership> ownership,
            Function<T, Iterator<T>> children, int maxObjects, int maxEncounters, long budgetNanos) {
        long started = System.nanoTime();
        Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<T> objects = new ArrayList<>(), attached = new ArrayList<>();
        Deque<Iterator<T>> stack = new ArrayDeque<>();
        stack.push(Collections.singleton(root).iterator());
        int encounters = 0, repeated = 0;
        while (!stack.isEmpty()) {
            if (System.nanoTime() - started > budgetNanos)
                throw refusal("five-second traversal budget exceeded", objects.size(), attached.size(), encounters, repeated);
            Iterator<T> iterator = stack.peek();
            if (!iterator.hasNext()) { stack.pop(); continue; }
            T object = iterator.next();
            if (++encounters > maxEncounters)
                throw refusal("containment work limit exceeded", objects.size(), attached.size(), encounters, repeated);
            if (!seen.add(object)) { repeated++; continue; }
            Ownership owner = ownership.apply(object);
            if (owner == Ownership.UNKNOWN)
                throw refusal("cannot establish project ownership", objects.size(), attached.size(), encounters, repeated);
            if (owner == Ownership.ATTACHED) { attached.add(object); continue; }
            objects.add(object);
            if (objects.size() > maxObjects)
                throw refusal("primary-project source exceeds " + maxObjects + " distinct objects", objects.size(), attached.size(), encounters, repeated);
            stack.push(children.apply(object));
        }
        return new Result<>(List.copyOf(objects), List.copyOf(attached), encounters, repeated);
    }

    private static IllegalArgumentException refusal(String reason, int primary, int attached, int encounters, int repeated) {
        return new IllegalArgumentException("Live export snapshot refused: " + reason + "; primary=" + primary
                + " attachedBoundaries=" + attached + " encounters=" + encounters + " repeated=" + repeated
                + ". Counts describe the bounded primary-project fingerprint");
    }
}
