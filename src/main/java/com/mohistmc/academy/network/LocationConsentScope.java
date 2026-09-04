package com.mohistmc.academy.network;

import java.util.Set;
import java.util.UUID;

/** Pure-Java immutable authorization boundary for an exact destination and mount graph. */
public record LocationConsentScope(UUID caster, LocationSnapshot location, Set<EntitySnapshot> entities,
                                   Set<RidingEdge> ridingEdges) {
    public LocationConsentScope {
        entities = Set.copyOf(entities);
        ridingEdges = Set.copyOf(ridingEdges);
    }

    public record LocationSnapshot(int index, String name, String dimension, long xBits, long yBits, long zBits) {
        public static LocationSnapshot of(int index, String name, String dimension, double x, double y, double z) {
            return new LocationSnapshot(index, name, dimension, Double.doubleToLongBits(x),
                    Double.doubleToLongBits(y), Double.doubleToLongBits(z));
        }
    }

    public record EntitySnapshot(UUID id, String type, UUID tameOwner) {}
    public record RidingEdge(UUID passenger, UUID vehicle) {}
}
