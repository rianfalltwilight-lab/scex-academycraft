package com.mohistmc.academy.client.sound;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** Makes the existing client skill-sound switch authoritative for every Academy ability sound. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class SkillSoundConfigHandler {
    private SkillSoundConfigHandler() {}

    @SubscribeEvent
    public static void beforeSound(PlaySoundEvent event) {
        if (ACConfig.Client.enableSkillSounds()) return;
        ResourceLocation id = event.getOriginalSound().getLocation();
        if (!AcademyCraft.MODID.equals(id.getNamespace())) return;
        String path = id.getPath();
        if (path.startsWith("ability.") || path.startsWith("em.") || path.startsWith("md.")
                || path.startsWith("tp.") || path.startsWith("vecmanip.")
                || path.startsWith("entity.silbarn_")) {
            event.setSound(null);
        }
    }
}
