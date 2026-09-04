package com.mohistmc.academy.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Loader-independent ordering policy for the wireless-node picker.
 *
 * <p>Keeping the policy free of Minecraft classes lets ordinary unit tests
 * exercise the exact production ordering without launching a game runtime.</p>
 */
final class NodeCandidateOrdering {
    private NodeCandidateOrdering() {}

    record Candidate<T>(T value, long key, double distanceSquared, int y, int x, int z) {}

    static <T> List<T> order(Collection<Candidate<T>> candidates, long connectedKey, int max) {
        if (candidates == null || candidates.isEmpty() || max <= 0) return List.of();

        List<Candidate<T>> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt((Candidate<T> candidate) -> candidate.key() == connectedKey ? 0 : 1)
                .thenComparingDouble(Candidate::distanceSquared)
                .thenComparingInt(Candidate::y)
                .thenComparingInt(Candidate::x)
                .thenComparingInt(Candidate::z));

        int end = Math.min(max, ordered.size());
        List<T> result = new ArrayList<>(end);
        for (int i = 0; i < end; i++) result.add(ordered.get(i).value());
        return result;
    }
}
