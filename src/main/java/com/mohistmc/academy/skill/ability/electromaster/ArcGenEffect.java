package com.mohistmc.academy.skill.ability.electromaster;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 电弧激发 —— 向前方发射电弧，命中实体造成伤害，命中水面有概率电出熟鱼，命中方块有概率点火 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public class ArcGenEffect implements SkillEffect {
    private static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public String getId() {
        return "arc_gen";
    }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        return DynamicSkillRules.canPay(data, getId(), lerpf(30, 70, exp), lerpf(18, 11, exp));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        long now = player.serverLevel().getGameTime();
        Long previous = LAST_CAST.get(player.getUUID());
        if (previous != null && now - previous < 2) return;
        LAST_CAST.put(player.getUUID(), now);
        float exp = data.getProficiency(getId());

        // 消耗 CP 和 Overload（与旧代码一致）
        float cp = lerpf(30, 70, exp);
        float overload = lerpf(18, 11, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        float range = lerpf(6, 15, exp);
        float damage = lerpf(5, 9, exp);
        float igniteProb = lerpf(0, 0.6f, exp);
        double fishProb = exp > 0.5f ? 0.1 : 0;
        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        TraceResult result = trace(player, eyePos, lookVec, range);

        Vec3 visualEnd = eyePos.add(lookVec.scale(range));
        if (result != null) {
            if (result.isEntity && result.entity != null) visualEnd = result.entity.getEyePosition();
            else if (result.hitPos != null) visualEnd = result.hitPos;
        }
        EffectHelper.electricTether(level, eyePos, visualEnd, 10);

        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.EM_ARC_WEAK, SoundSource.PLAYERS, 0.5f, 1.0f);

        float expIncr = 0f;

        if (result != null) {
            if (result.isEntity && result.entity != null) {
                // === 命中实体 ===
                Entity target = result.entity;
                ElectromasterDamageHelper.attack(player,target,player.damageSources().playerAttack(player),
                        DynamicSkillRules.damage(getId(),damage));

                expIncr = getExpIncr(exp, true);
            } else if (result.blockPos != null) {
                // === 命中方块 ===
                BlockState state = level.getBlockState(result.blockPos);

                if (state.is(Blocks.WATER)) {
                    if (level.random.nextDouble() < fishProb) {
                        Vec3 pos = result.hitPos != null ? result.hitPos : Vec3.atCenterOf(result.blockPos);
                        if (authorized(player, level, result.blockPos)) {
                            ItemEntity fish = new ItemEntity(level,
                                    pos.x, pos.y, pos.z,
                                    new ItemStack(Items.COOKED_COD));
                            // addFreshEntity itself posts NeoForge's cancellable join event.
                            if (level.addFreshEntity(fish)) {
                                com.mohistmc.academy.advancement.LegacyAdvancementBridge.award(player,"electromaster/arc_gen");
                            }
                        }
                    }
                } else {
                    if (level.random.nextDouble() < igniteProb) {
                        BlockPos firePos = result.blockPos.above();
                        if (level.isEmptyBlock(firePos) && authorized(player, level, firePos)) {
                            BlockEvent.EntityPlaceEvent permission = new BlockEvent.EntityPlaceEvent(
                                    BlockSnapshot.create(level.dimension(), level, firePos),
                                    level.getBlockState(result.blockPos), player);
                            NeoForge.EVENT_BUS.post(permission);
                            if (!permission.isCanceled()) {
                                level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                            }
                        }
                    }
                }

                expIncr = getExpIncr(exp, false);
            }

            if (expIncr > 0 && !data.isDevMode()) {
                DynamicSkillRules.addExp(player,data, getId(), expIncr);
            }
        }

        // 冷却时间（由 SkillRegistry 的 Builder 处理基础冷却，这里不强制设置）
    }

    private static boolean authorized(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_CAST.clear(); }


    /** 射线追踪，检测实体和方块（包括水）。 */
    private TraceResult trace(ServerPlayer player, Vec3 start, Vec3 dir, double range) {
        ServerLevel level = player.serverLevel();

        Vec3 end = start.add(dir.scale(range));
        // LambdaLib's filNormal used exact collision shapes and ArcGen added
        // water to that selector. Vanilla's collider + any-fluid clip is the
        // 1.21.1 equivalent; fixed 0.3-block stepping skipped slabs/fences.
        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = rayTraceEntities(player, start, entityEnd);
        if (entityHit != null) {
            TraceResult result = new TraceResult();
            result.isEntity = true;
            result.entity = entityHit.getEntity();
            result.hitPos = entityHit.getLocation();
            return result;
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            TraceResult result = new TraceResult();
            result.blockPos = blockHit.getBlockPos();
            result.hitPos = blockHit.getLocation();
            return result;
        }
        return null;
    }

    private EntityHitResult rayTraceEntities(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB searchArea = new AABB(start, end).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, searchArea,
                e -> e != player && e.isAlive() && e.isPickable());

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vec3 closestHit = null;

        for (Entity entity : entities) {
            AABB box = entity.getBoundingBox().inflate(0.3);
            var result = box.clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                    closestHit = result.get();
                }
            }
        }

        if (closest != null) {
            return new EntityHitResult(closest, closestHit);
        }
        return null;
    }

    private float getExpIncr(float exp, boolean effectiveHit) {
        if (effectiveHit) {
            return lerpf(0.0048f, 0.0072f, exp);
        } else {
            return lerpf(0.0018f, 0.0027f, exp);
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(15, 5, proficiency);
    }

    // ==================== 内部类 ====================

    private static class TraceResult {
        boolean isEntity;
        Entity entity;
        BlockPos blockPos;
        Vec3 hitPos;
    }
}
