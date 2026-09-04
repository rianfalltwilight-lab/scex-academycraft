package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Client-only camera/hand reconstruction for VecManip. The server remains authoritative. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class LegacyVecmanipClientController {
    private static boolean groundWasCharging;
    private static int lastGroundTicks;
    private static boolean groundReleaseEligible;
    private static int groundSlamTicks;

    private static boolean vectorWasCharging;
    private static String vectorSkill = "";
    private static int lastVectorTicks;
    private static boolean vectorReleaseEligible;
    private static long vectorChargeStartedNanos;
    private static long vectorPunchStartedNanos;

    private LegacyVecmanipClientController() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { reset(); return; }
        if (mc.screen != null) {
            groundWasCharging = false;
            vectorWasCharging = false;
            groundSlamTicks = 0;
            return;
        }
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        tickGround(mc, data);
        tickVectorHand(mc, data);
    }

    private static void tickGround(Minecraft mc, PlayerAbilityData data) {
        boolean charging = ChargingHudOverlay.isCharging("ground_shock");
        if (charging) {
            int tick = Math.max(1, ChargingHudOverlay.currentTicks());
            float pitchDelta = tick < 4 ? tick / 4f : tick <= 20 ? 1f
                    : tick <= 25 ? 1f - (tick - 20) / 5f : 0;
            mc.player.setXRot(mc.player.getXRot() - pitchDelta * .2f);
            lastGroundTicks = tick;
            float exp = data.getProficiency("ground_shock");
            groundReleaseEligible = mc.player.onGround() && (data.isDevMode()
                    || data.getCurrentCp() >= 80 + 70 * exp
                    && data.getCurrentOverload() + 15 - 5 * exp <= data.getMaxOverload());
        } else if (groundWasCharging && lastGroundTicks >= 5 && groundReleaseEligible) {
            groundSlamTicks = 4;
        }
        groundWasCharging = charging;
        if (groundSlamTicks > 0) {
            mc.player.setXRot(mc.player.getXRot() + 3.4f);
            groundSlamTicks--;
        }
    }

    private static void tickVectorHand(Minecraft mc, PlayerAbilityData data) {
        String current = ChargingHudOverlay.isCharging("dir_shock") ? "dir_shock"
                : ChargingHudOverlay.isCharging("dir_blast") ? "dir_blast" : "";
        boolean charging = !current.isEmpty();
        if (charging && !vectorWasCharging) vectorChargeStartedNanos = System.nanoTime();
        if (charging) {
            vectorSkill = current;
            lastVectorTicks = ChargingHudOverlay.currentTicks();
            float exp = data.getProficiency(current);
            float cp = "dir_shock".equals(current) ? 50 + 50 * exp : 160 + 40 * exp;
            float overload = "dir_shock".equals(current) ? 18 - 6 * exp : 50 - 20 * exp;
            vectorReleaseEligible = data.isDevMode() || data.getCurrentCp() >= cp
                    && data.getCurrentOverload() + overload <= data.getMaxOverload();
        } else if (vectorWasCharging && lastVectorTicks >= 7 && lastVectorTicks <= 49
                && vectorReleaseEligible
                && ("dir_blast".equals(vectorSkill) || hasLivingTarget(mc, 3))) {
            vectorPunchStartedNanos = System.nanoTime();
        }
        vectorWasCharging = charging;
    }

    private static boolean hasLivingTarget(Minecraft mc, double range) {
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(mc.player.getLookAngle().scale(range));
        HitResult wall = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player));
        double wallDistance = wall.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : start.distanceTo(wall.getLocation());
        List<Entity> entities = mc.level.getEntities(mc.player, new AABB(start, end).inflate(1),
                entity -> entity.isAlive() && entity.isPickable()
                        && (entity instanceof LivingEntity || entity instanceof EnderDragonPart));
        for (Entity entity : entities) {
            var hit = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (hit.isPresent() && start.distanceTo(hit.get()) < wallDistance) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        long now = System.nanoTime();
        float punch = (now - vectorPunchStartedNanos) / 300_000_000f;
        PoseStack pose = event.getPoseStack();
        if (vectorPunchStartedNanos != 0 && punch >= 0 && punch <= 1.15f) {
            float t = Math.min(1, punch);
            float y = piecewise(t, 0, .8f, .5f, .75f, 1, 0);
            float x = piecewise(t, 0, -.04f, .5f, -.04f, 1, 0);
            float z = piecewise(t, 0, 0, .3f, -.4f, 1, 0);
            float rx = piecewise(t, 0, -40, .5f, -45, 1, 0);
            float ry = piecewise(t, 0, 0, .3f, 10, 1, 0);
            pose.translate(x, y, z);
            pose.mulPose(Axis.XP.rotationDegrees(rx));
            pose.mulPose(Axis.YP.rotationDegrees(ry));
        } else if (vectorWasCharging) {
            float t = Math.min(1, Math.max(0, (now - vectorChargeStartedNanos) / 150_000_000f));
            pose.translate(-.02f * t, .4f * t, -.05f * t);
            pose.mulPose(Axis.XP.rotationDegrees(-20 * t));
        }
    }

    private static float piecewise(float t, float t0, float v0, float t1, float v1, float t2, float v2) {
        if (t <= t1) return v0 + (v1 - v0) * (t - t0) / (t1 - t0);
        return v1 + (v2 - v1) * (t - t1) / (t2 - t1);
    }

    public static void reset() {
        groundWasCharging = vectorWasCharging = false;
        lastGroundTicks = lastVectorTicks = groundSlamTicks = 0;
        groundReleaseEligible = vectorReleaseEligible = false;
        vectorSkill = "";
        vectorChargeStartedNanos = vectorPunchStartedNanos = 0;
    }
}
