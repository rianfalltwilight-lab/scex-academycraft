package com.mohistmc.academy.client.sound;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.entity.StormWingVisualEntity;
import com.mohistmc.academy.world.AcademySounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Owns legacy follow-player loops so cancellation never leaves stacked sounds behind. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class AbilityLoopSoundManager {
    private static FollowPlayerLoop movement, charging, intensify, magManip, mineRay, shield;
    private static final Map<Integer, FollowEntityLoop> STORM_WING = new HashMap<>();
    private static FollowPlayerLoop meltdownerCharge;
    private static boolean meltdownerWasCharging;
    private AbilityLoopSoundManager() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        movement = sync(mc, movement, "mag_movement", AcademySounds.EM_MOVE_LOOP.value(), 1.0f);
        charging = sync(mc, charging, "charging", AcademySounds.EM_CHARGE_LOOP.value(), .3f);
        intensify = sync(mc, intensify, "body_intensify", AcademySounds.EM_INTENSIFY_LOOP.value(), 1.0f);
        magManip = sync(mc, magManip, "mag_manip", AcademySounds.EM_LF_LOOP.value(), 1.0f);
        mineRay = sync(mc, mineRay, AcademySounds.MD_MINE_LOOP.value(), .3f,
                "mine_ray_basic", "mine_ray_luck", "mine_ray_expert");
        shield = sync(mc, shield, "light_shield", AcademySounds.MD_SHIELD_LOOP.value(), 1.0f);
        syncStormWingEntities(mc);

        boolean mdCharging = ACConfig.Client.enableSkillSounds() && mc.player != null
                && ChargingHudOverlay.isCharging("meltdowner");
        if (mdCharging && !meltdownerWasCharging) {
            meltdownerCharge = new FollowPlayerLoop(
                    AcademySounds.MD_MD_CHARGE.value(), mc.player, 1.0f, 1.0f,
                    false, () -> ChargingHudOverlay.isCharging("meltdowner"));
            mc.getSoundManager().play(meltdownerCharge);
        } else if (!mdCharging && meltdownerCharge != null) {
            mc.getSoundManager().stop(meltdownerCharge);
            meltdownerCharge = null;
        }
        meltdownerWasCharging = mdCharging;
    }

    public static void resetClientSession() { stopMovement(); }

    private static void stopMovement() {
        if (movement != null) Minecraft.getInstance().getSoundManager().stop(movement);
        if (charging != null) Minecraft.getInstance().getSoundManager().stop(charging);
        if (intensify != null) Minecraft.getInstance().getSoundManager().stop(intensify);
        if (magManip != null) Minecraft.getInstance().getSoundManager().stop(magManip);
        if (mineRay != null) Minecraft.getInstance().getSoundManager().stop(mineRay);
        if (shield != null) Minecraft.getInstance().getSoundManager().stop(shield);
        STORM_WING.values().forEach(sound -> Minecraft.getInstance().getSoundManager().stop(sound));
        STORM_WING.clear();
        if (meltdownerCharge != null) Minecraft.getInstance().getSoundManager().stop(meltdownerCharge);
        movement = charging = intensify = magManip = mineRay = shield = meltdownerCharge = null;
        meltdownerWasCharging = false;
    }

    private static FollowPlayerLoop sync(Minecraft mc, FollowPlayerLoop current, String skill,
                                         SoundEvent sound, float volume) {
        return sync(mc, current, sound, volume, skill);
    }

    private static FollowPlayerLoop sync(Minecraft mc, FollowPlayerLoop current,
                                         SoundEvent sound, float volume, String... skills) {
        boolean wanted = ACConfig.Client.enableSkillSounds() && mc.player != null
                && isAnyCharging(skills);
        if(wanted&&(current==null||current.isStopped())){current=new FollowPlayerLoop(sound,mc.player,volume,1f,true,()->isAnyCharging(skills));mc.getSoundManager().play(current);}
        if(!wanted&&current!=null){mc.getSoundManager().stop(current);current=null;}
        return current;
    }

    private static boolean isAnyCharging(String... skills) {
        for (String skill : skills) if (ChargingHudOverlay.isCharging(skill)) return true;
        return false;
    }

    private static void syncStormWingEntities(Minecraft mc) {
        if (!ACConfig.Client.enableSkillSounds() || mc.player == null || mc.level == null) {
            STORM_WING.values().forEach(sound -> mc.getSoundManager().stop(sound));
            STORM_WING.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (StormWingVisualEntity entity : mc.level.getEntitiesOfClass(StormWingVisualEntity.class,
                mc.player.getBoundingBox().inflate(64), candidate -> !candidate.isTerminating())) {
            seen.add(entity.getId());
            FollowEntityLoop current = STORM_WING.get(entity.getId());
            if (current == null || current.isStopped()) {
                current = new FollowEntityLoop(AcademySounds.VM_STORM_WING.value(), entity, 1f);
                STORM_WING.put(entity.getId(), current);
                mc.getSoundManager().play(current);
            }
        }
        STORM_WING.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            mc.getSoundManager().stop(entry.getValue());
            return true;
        });
    }

    private static final class FollowPlayerLoop extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private final java.util.function.BooleanSupplier alive;

        FollowPlayerLoop(SoundEvent sound, LocalPlayer player, float volume, float pitch,
                         boolean looping, java.util.function.BooleanSupplier alive) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.alive = alive;
            this.volume = volume;
            this.pitch = pitch;
            this.looping = looping;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            updatePosition();
        }

        @Override public void tick() {
            if (player.isRemoved() || !alive.getAsBoolean()) { stop(); return; }
            updatePosition();
        }

        private void updatePosition() { x = player.getX(); y = player.getY(); z = player.getZ(); }
    }

    private static final class FollowEntityLoop extends AbstractTickableSoundInstance {
        private final StormWingVisualEntity entity;

        FollowEntityLoop(SoundEvent sound, StormWingVisualEntity entity, float volume) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.entity = entity;
            this.volume = volume;
            pitch = 1;
            looping = true;
            delay = 0;
            attenuation = Attenuation.LINEAR;
            updatePosition();
        }

        @Override public void tick() {
            if (entity.isRemoved() || entity.isTerminating()) { stop(); return; }
            updatePosition();
        }

        private void updatePosition() { x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
    }
}
