package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class LocationTeleportChunkPlanTest {
    @Test void exactChunkBoundaryDoesNotLoadAnUnrelatedNeighbor() {
        var chunks = new LinkedHashSet<LocationTeleportChunkPlan.Chunk>();
        assertTrue(LocationTeleportChunkPlan.addBox(chunks, 0, 0, 16, 16, 16));
        assertEquals(new LinkedHashSet<>(java.util.List.of(new LocationTeleportChunkPlan.Chunk(0, 0))), chunks);
    }

    @Test void collisionBoxLoadsEveryIntersectedChunk() {
        var chunks = new LinkedHashSet<LocationTeleportChunkPlan.Chunk>();
        assertTrue(LocationTeleportChunkPlan.addBox(chunks, 15.75, -0.25, 16.25, 0.25, 16));
        assertEquals(4, chunks.size());
        assertTrue(chunks.contains(new LocationTeleportChunkPlan.Chunk(0, -1)));
        assertTrue(chunks.contains(new LocationTeleportChunkPlan.Chunk(1, 0)));
    }

    @Test void nonFiniteAndOversizedPlansFailClosedWithoutUnboundedIteration() {
        assertFalse(LocationTeleportChunkPlan.addBox(new LinkedHashSet<>(), Double.NaN, 0, 1, 1, 16));
        assertFalse(LocationTeleportChunkPlan.addBox(new LinkedHashSet<>(), 0, 0, 16 * 17, 16, 16));
    }

    @Test void aggregateMountGraphCannotExceedTheSharedChunkBudget() {
        var chunks = new LinkedHashSet<LocationTeleportChunkPlan.Chunk>();
        assertTrue(LocationTeleportChunkPlan.addBox(chunks, 0, 0, 16 * 15, 16, 16));
        assertFalse(LocationTeleportChunkPlan.addBox(chunks, 16 * 15, 0, 16 * 17, 16, 16));
        assertEquals(17, chunks.size(), "planner may detect the first excess chunk but must stop immediately");
    }
}
