package com.mohistmc.academy.skill.ability.vecmanip;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 地面冲击 —— 向地面释放冲击波，破坏地形并伤害敌人 */
public class GroundShockEffect implements ChargingSkillEffect {

    private static final int MIN_TICKS = 5;

    @Override
    public String getId() {
        return "ground_shock";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return MIN_TICKS;
    }

    @Override
    public int getMaxChargeTicks() {
        return MIN_TICKS;
    }

    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float cp = lerpf(80, 150, data.getProficiency(getId()));
        float overload = lerpf(15, 10, data.getProficiency(getId()));
        return ticks >= MIN_TICKS && player.onGround()
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (ticks < MIN_TICKS) return;

        float exp = data.getProficiency(getId());

        if (!player.onGround()) return;

        float cp = lerpf(80, 150, exp);
        float overload = lerpf(15, 10, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        ServerLevel level = player.serverLevel();
        Vec3 planeLook = player.getLookAngle().normalize();
        if (Math.abs(planeLook.x) + Math.abs(planeLook.z) < 1.0E-6) return;

        double[] energy = {lerpf(60, 120, exp)};
        float damage = lerpf(4, 6, exp);
        int maxIter = (int) lerpf(10, 25, exp);
        float groundBreakProb = 0.3f;
        float dropRate = lerpf(0.3f, 1.0f, exp);
        float ySpeed = (0.6f + level.random.nextFloat() * 0.3f) * lerpf(0.8f, 1.3f, exp);

        int px = (int) player.getX();
        int py = (int) player.getY() - 1;
        int pz = (int) player.getZ();

        Set<BlockPos> visitedBlocks = new HashSet<>();
        Set<Entity> visitedEntities = new HashSet<>();
        LegacyPlotter plotter = new LegacyPlotter(px, py, pz, planeLook.x, planeLook.z);
        // 1.0.7 called vanilla Vec3.rotateAroundY(90).  That API takes
        // radians, so reproducing the played release literally is important:
        // replacing it with a conceptual 90-degree perpendicular changes the
        // five-lane ground footprint.
        double cos90 = Math.cos(90.0);
        double sin90 = Math.sin(90.0);
        Vec3 lateral = new Vec3(planeLook.x * cos90 + planeLook.z * sin90,
                planeLook.y, planeLook.z * cos90 - planeLook.x * sin90);
        Vec3[] offsets = {Vec3.ZERO, lateral, lateral.scale(-1), lateral.scale(2), lateral.scale(-2)};
        double[] probabilities = {1.0, 0.7, 0.7, 0.3, 0.3};
        boolean destroyBlocks = DynamicSkillRules.destroysBlocks(level, getId());

        for (int iter = 0; iter < maxIter && energy[0] > 0; iter++) {
            int[] next = plotter.next();
            int x = next[0], y = next[1], z = next[2];
            for (int i = 0; i < offsets.length; i++) {
                if (level.random.nextDouble() >= probabilities[i]) continue;
                Vec3 offset = offsets[i];
                BlockPos pos = new BlockPos((int) (x + offset.x), (int) (y + offset.y), (int) (z + offset.z));
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !visitedBlocks.add(pos)) continue;

                if (state.is(Blocks.STONE)) {
                    energy[0] -= 0.4;
                    if (destroyBlocks && mayBreak(level, player, pos, state)) level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
                } else if (state.is(Blocks.GRASS_BLOCK)) {
                    energy[0] -= 0.2;
                    if (destroyBlocks && mayBreak(level, player, pos, state)) level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                } else if (state.is(Blocks.FARMLAND)) {
                    energy[0] -= 0.1;
                } else {
                    energy[0] -= 0.5;
                }

                if (destroyBlocks && level.random.nextDouble() < groundBreakProb)
                    energy[0] = breakWithForce(level, player, new BlockPos(x, y, z), false, dropRate, energy[0]);
                for (int above = 1; above <= 3; above++)
                    if (destroyBlocks) energy[0] = breakWithForce(level, player, new BlockPos(x, y + above, z), false, dropRate, energy[0]);

                AABB aabb = new AABB(pos.getX() - 0.2, pos.getY() - 0.2, pos.getZ() - 0.2,
                        pos.getX() + 1.4, pos.getY() + 2.2, pos.getZ() + 1.4);
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, aabb,
                        entity -> entity != player && entity.isAlive())) {
                    if (!visitedEntities.add(living)) continue;
                    energy[0] -= 1;
                    AcademyDamageHelper.hurt(player, living, player.damageSources().playerAttack(player),
                            DynamicSkillRules.damage(getId(), damage));
                    living.setDeltaMovement(living.getDeltaMovement().x, ySpeed, living.getDeltaMovement().z);
                    living.hurtMarked = true;
                    DynamicSkillRules.addExp(player, data, getId(), 0.002f);
                }
                EffectHelper.windBurst(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 3, 0.3);
            }
        }

        // 熟练度=1 时破坏大范围弱方块
        if (exp >= 1.0f && DynamicSkillRules.destroysBlocks(level, getId())) {
            energy[0] = Double.MAX_VALUE;
            for (int ox = -5; ox < 5; ox++) {
                for (int oy = -1; oy < 1; oy++) {
                    for (int oz = -5; oz < 5; oz++) {
                        BlockPos pos = new BlockPos(px + ox, py + oy, pz + oz);
                        BlockState state = level.getBlockState(pos);
                        float hardness = state.getDestroySpeed(level, pos);
                        if (hardness >= 0 && hardness <= 0.6f && !state.isAir())
                            energy[0] = breakWithForce(level, player, pos, true, dropRate, energy[0]);
                    }
                }
            }
        }

        DynamicSkillRules.addExp(player,data, getId(), 0.001f);
        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.VM_GROUNDSHOCK, SoundSource.PLAYERS, 2.0f, 1.0f);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(80, 40, proficiency);
    }

    private static boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!level.mayInteract(player, pos)) return false;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    private static double breakWithForce(ServerLevel level, ServerPlayer player, BlockPos pos,
                                         boolean mayDrop, float dropRate, double energy) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.FARMLAND) || !state.getFluidState().isEmpty()) return energy;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || energy < hardness || !mayBreak(level, player, pos, state)) return energy;
        if (level.destroyBlock(pos, mayDrop && level.random.nextFloat() < dropRate, player)) return energy - hardness;
        return energy;
    }

    /** Integer line iterator copied from 1.0.7's Plotter for the same wave footprint. */
    private static final class LegacyPlotter {
        private final boolean zMajor;
        private final double secondaryPerPrimary;
        private final int direction;
        private final int primary0;
        private final int secondary0;
        private final int y;
        private int primary;
        private int secondary;

        LegacyPlotter(int x0, int y0, int z0, double dx, double dz) {
            zMajor = Math.abs(dz) > Math.abs(dx);
            double primaryDelta = zMajor ? dz : dx;
            double secondaryDelta = zMajor ? dx : dz;
            primary0 = zMajor ? z0 : x0;
            secondary0 = zMajor ? x0 : z0;
            primary = primary0;
            secondary = secondary0;
            y = y0;
            direction = primaryDelta > 0 ? 1 : -1;
            secondaryPerPrimary = secondaryDelta / primaryDelta;
        }

        int[] next() {
            int nextPrimary = primary + direction;
            double wantedSecondary = secondary0 + (nextPrimary - primary0) * secondaryPerPrimary;
            if (Math.abs(wantedSecondary - secondary) > 0.5)
                secondary += (int) Math.signum(secondaryPerPrimary) * direction;
            else primary = nextPrimary;
            return zMajor ? new int[]{secondary, y, primary} : new int[]{primary, y, secondary};
        }
    }
}

