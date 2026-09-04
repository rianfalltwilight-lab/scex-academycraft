package com.mohistmc.academy.world.block;

import java.util.UUID;

public interface IDevStructure {
    UUID getStructureId();
    void setStructureId(UUID id);

    /** Corrupt/legacy structure metadata must not make an entire chunk fail to load. */
    static UUID parseStructureId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
