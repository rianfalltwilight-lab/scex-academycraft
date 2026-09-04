package com.mohistmc.academy.network;

import java.util.Set;

/**
 * Pure, bounded chunk planning for Location Teleport destination collision checks.
 *
 * <p>The packet never supplies these coordinates. They are derived from the complete
 * server-owned mount graph after the selected saved location has been sanitized.</p>
 */
final class LocationTeleportChunkPlan {
    private LocationTeleportChunkPlan() {}

    record Chunk(int x, int z) {}

    static boolean addBox(Set<Chunk> chunks, double minX, double minZ,
                          double maxX, double maxZ, int limit) {
        if (limit <= 0 || !Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || maxX <= minX || maxZ <= minZ) return false;

        long minChunkX = Math.floorDiv((long) Math.floor(minX), 16L);
        long minChunkZ = Math.floorDiv((long) Math.floor(minZ), 16L);
        long maxChunkX = Math.floorDiv((long) Math.floor(Math.nextDown(maxX)), 16L);
        long maxChunkZ = Math.floorDiv((long) Math.floor(Math.nextDown(maxZ)), 16L);
        if (minChunkX < Integer.MIN_VALUE || maxChunkX > Integer.MAX_VALUE
                || minChunkZ < Integer.MIN_VALUE || maxChunkZ > Integer.MAX_VALUE) return false;

        long width = maxChunkX - minChunkX + 1L;
        long depth = maxChunkZ - minChunkZ + 1L;
        if (width <= 0 || depth <= 0 || width > limit || depth > limit
                || width * depth > limit) return false;

        for (long x = minChunkX; x <= maxChunkX; x++) {
            for (long z = minChunkZ; z <= maxChunkZ; z++) {
                chunks.add(new Chunk((int) x, (int) z));
                if (chunks.size() > limit) return false;
            }
        }
        return true;
    }
}
