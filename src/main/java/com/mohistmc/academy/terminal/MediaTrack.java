package com.mohistmc.academy.terminal;


import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record MediaTrack(
        String trackId,
        String nameKey,
        String descKey,
        String tag,
        ResourceLocation texture,
        ResourceLocation soundId,
        int durationSeconds,
        @Nullable Path externalSource,
        String externalName,
        String externalDescription
) {
    public MediaTrack(String trackId, String nameKey, String descKey, String tag,
                      ResourceLocation texture, ResourceLocation soundId, int durationSeconds) {
        this(trackId, nameKey, descKey, tag, texture, soundId, durationSeconds,
                null, "", "");
    }

    public boolean external() {
        return externalSource != null;
    }

    public Component displayName() {
        return external() ? Component.literal(externalName) : Component.translatable(nameKey);
    }

    public Component displayDescription() {
        return external() ? Component.literal(externalDescription) : Component.translatable(descKey);
    }

    public MediaTrack withExternalMetadata(String name, String description) {
        if (!external()) return this;
        return new MediaTrack(trackId, nameKey, descKey, tag, texture, soundId,
                durationSeconds, externalSource, name, description);
    }
}
