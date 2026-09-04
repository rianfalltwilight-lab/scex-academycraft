package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequestNodesOrderingTest {
    @Test
    void connectedNodeSurvivesDenseCandidateLimit() {
        long connected = 99L;
        var candidates = new java.util.ArrayList<NodeCandidateOrdering.Candidate<String>>();
        candidates.add(new NodeCandidateOrdering.Candidate<>("connected", connected, 361, 0, 19, 0));
        for (int x = 1; x <= 40; x++) {
            candidates.add(new NodeCandidateOrdering.Candidate<>("node-" + x, x, x * x + 1, 0, x, 1));
        }

        List<String> ordered = NodeCandidateOrdering.order(candidates, connected, 32);

        assertEquals(32, ordered.size());
        assertEquals("connected", ordered.getFirst());
    }

    @Test
    void availableNodesAreStableAndNearestFirst() {
        var candidates = List.of(
                new NodeCandidateOrdering.Candidate<>("far", 3, 9, 4, 13, 10),
                new NodeCandidateOrdering.Candidate<>("near", 1, 1, 4, 11, 10),
                new NodeCandidateOrdering.Candidate<>("middle", 2, 4, 4, 12, 10));

        assertEquals(List.of("near", "middle", "far"),
                NodeCandidateOrdering.order(candidates, Long.MIN_VALUE, 32));
    }
}
