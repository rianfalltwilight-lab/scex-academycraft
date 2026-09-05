package com.mohistmc.academy.skill.ability.telekinesis;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Drowned;

/**
 * A server-only, session-owned follower using the existing vanilla Drowned network appearance.
 * It is intentionally transient: unloading or restarting ends the skill instead of saving a
 * no-AI hostile mob whose owner session no longer exists. This does not reproduce the old model.
 */
final class LiquidShadowEntity extends Drowned {
    private final UUID abilityOwner;

    LiquidShadowEntity(ServerLevel level, UUID abilityOwner) {
        super(EntityType.DROWNED, level);
        this.abilityOwner = abilityOwner;
    }

    UUID abilityOwner() { return abilityOwner; }

    @Override public void tick() {
        // Tracking can end before an entity is fully unloaded. Never let a re-tracked
        // instance continue to exist after its owner session has been invalidated.
        if (!TelekinesisPassiveHandler.isCurrentShadow(this)) {
            discard();
            return;
        }
        super.tick();
        // NoAI prevents vanilla travel as well as hostile goals. The owner supplies
        // the follower velocity; apply it through normal collision resolution here
        // so pursuit advances without enabling unpaid Drowned AI or crossing walls.
        if (isAlive()) move(MoverType.SELF, getDeltaMovement());
    }

    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean save(CompoundTag tag) { return false; }
    @Override public boolean saveAsPassenger(CompoundTag tag) { return false; }
}
