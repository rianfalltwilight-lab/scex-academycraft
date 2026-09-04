package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.entity.StormWingVisualEntity;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyEntities;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 风暴之翼 —— 获得临时飞行能力，推开周围弱方块和实体 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public class StormWingEffect implements ChargingSkillEffect {
    private static final Map<UUID, FlightSnapshot> FLIGHT = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> EXTERNAL_FLIGHT = ConcurrentHashMap.newKeySet();
    private record FlightSnapshot(boolean mayfly, boolean flying, boolean grantedMayfly, UUID visualId) {}
    /** Compatibility lease: another flight provider can retain flight across StormWing cleanup. */
    public static void claimExternalFlight(UUID player){EXTERNAL_FLIGHT.add(player);}
    public static void releaseExternalFlight(UUID player){EXTERNAL_FLIGHT.remove(player);}

    @Override
    public String getId() {
        return "storm_wing";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return (int) lerpf(70, 30, 0f) + 1;
    }

    @Override
    public int getMaxChargeTicks() {
        return (int) lerpf(70, 30, 0f);
    }

    @Override
    public int getMinChargeTicks(PlayerAbilityData data) {
        return (int) lerpf(70, 30, data.getProficiency(getId())) + 1;
    }

    @Override
    public int getMaxChargeTicks(PlayerAbilityData data) {
        return (int) lerpf(70, 30, data.getProficiency(getId()));
    }

    @Override
    public int getSessionTimeoutTicks(PlayerAbilityData data) {
        return Integer.MAX_VALUE;
    }

    @Override
    public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        // The original key is a toggle: a second press ends the already-active
        // context, regardless of how long it has remained active past the meter.
        return ticks >= getMinChargeTicks(data) && FLIGHT.containsKey(player.getUUID());
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        // 1.0.7 consumes nothing during the wind-up phase.
        return DynamicSkillRules.enabled(getId());
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        StormWingVisualEntity visual = new StormWingVisualEntity(
                AcademyEntities.STORM_WING_VISUAL.get(), player.serverLevel())
                .configure(player, (int) lerpf(70, 30, data.getProficiency(getId())));
        UUID visualId = player.serverLevel().addFreshEntity(visual) ? visual.getUUID() : null;
        FlightSnapshot old = new FlightSnapshot(player.getAbilities().mayfly,
                player.getAbilities().flying, !player.getAbilities().mayfly, visualId);
        FLIGHT.put(player.getUUID(), old);
        // The legacy context grants allowFlying as soon as it is made alive;
        // actual resource consumption starts only after the charge transition.
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        int chargeTime = (int) lerpf(70, 30, exp);

        player.fallDistance = 0;
        performEnvironmentalEffects(player, data, ticks);

        // Legacy transition is stateTick > chargeTime, not meter == chargeTime.
        if (ticks == chargeTime + 1) {
            if (!payFlightTick(data)) return false;
            performActivate(player, data);
            performFlightEffects(player, data);
        } else if (ticks > chargeTime + 1 && (!payFlightTick(data) || !performFlightEffects(player, data))) return false;
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        cleanupFlight(player);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        cleanupFlight(player);
        if (!data.isDevMode()) data.setCooldown(getId(), getCooldownTicks(data.getProficiency(getId())));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    private void performActivate(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();

        // 1.0.7 granted allowFlying but never switched the vanilla flying
        // flag on.  Movement belongs to StormWing itself; enabling creative
        // flight here adds a second, incompatible movement controller.
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        StormWingVisualEntity visual = visual(player.serverLevel(), FLIGHT.get(player.getUUID()));
        if (visual != null) visual.activate();

        // 熟练度=1 时推开周围实体
        if (exp == 1.0f) {
            for (Entity e : level.getEntities(player,
                    new AABB(player.getX() - 6, player.getY() - 6, player.getZ() - 6,
                            player.getX() + 6, player.getY() + 6, player.getZ() + 6),
                    ent -> ent.isAlive() && ent.position().distanceToSqr(player.position()) <= 36)) {
                Vec3 delta = e.getEyePosition().subtract(player.position());
                // The old source randomized each component before normalizing.
                delta = new Vec3(delta.x * (.9 + level.random.nextDouble() * .3),
                        delta.y * (.9 + level.random.nextDouble() * .3),
                        delta.z * (.9 + level.random.nextDouble() * .3)).normalize()
                        .scale(0.5 + level.random.nextFloat() * 0.5);
                e.setDeltaMovement(delta.x, delta.y, delta.z);
                e.hurtMarked = true;
            }
        }

    }

    private void cleanupFlight(ServerPlayer player) {
        FlightSnapshot old=FLIGHT.remove(player.getUUID());
        StormWingVisualEntity visual=visual(player.serverLevel(),old);
        if(visual!=null)visual.terminate();
        if(old!=null&&old.grantedMayfly()&&!player.isCreative()&&!player.isSpectator()&&!EXTERNAL_FLIGHT.contains(player.getUUID())){
            player.getAbilities().mayfly=false;
            if(!old.flying())player.getAbilities().flying=false;
            player.onUpdateAbilities();
        }
        player.fallDistance = 0;
    }

    private boolean payFlightTick(PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float cp = lerpf(40, 25, exp);
        float overload = lerpf(10, 7, exp);
        return DynamicSkillRules.tryPay(data,getId(),cp,overload);
    }

    private boolean performFlightEffects(ServerPlayer player, PlayerAbilityData data) {
        if(!player.getAbilities().mayfly) return false;
        float exp = data.getProficiency(getId());

        DynamicSkillRules.addExp(player,data, getId(), 0.00005f);
        player.fallDistance = 0;

        // Approach the legacy target velocity by exactly ACCEL=.16 per axis.
        Vec3 forward = player.getLookAngle().normalize();
        // With yaw=0 the old A-key vector is +X.  ServerPlayer.xxa is the
        // left impulse, so the lateral basis must be (forward.z, 0, -forward.x).
        Vec3 left = new Vec3(forward.z, 0, -forward.x);
        if (left.lengthSqr() > 1.0e-6) left = left.normalize();
        Vec3 direction = forward.scale(player.zza).add(left.scale(player.xxa));
        if (direction.lengthSqr() > 1.0e-6) {
            float speed = (exp < .45f ? .7f : 1.2f) * lerpf(2, 3, exp);
            Vec3 target = direction.normalize().scale(speed);
            Vec3 current = player.getDeltaMovement();
            player.setDeltaMovement(approach(current.x, target.x, .16),
                    approach(current.y, target.y, .16), approach(current.z, target.z, .16));
        } else {
            Vec3 current = player.getDeltaMovement();
            Vec3 feet = player.position();
            HitResult ground = player.level().clip(new ClipContext(feet.add(0, .5, 0),
                    feet.add(0, -.3, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            player.setDeltaMovement(current.x,
                    ground.getType() == HitResult.Type.MISS ? current.y + .078 : .1,
                    current.z);
        }
        player.hurtMarked=true;

        return true;
    }

    private void performEnvironmentalEffects(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        // The old server context performs this during both CHARGE and ACTIVE.
        if (exp < 0.15f && DynamicSkillRules.destroysBlocks(level, getId())) {
            for (int i = 0; i < 40; i++) {
                int dx = level.random.nextIntBetweenInclusive(-10, 10);
                int dy = level.random.nextIntBetweenInclusive(-10, 10);
                int dz = level.random.nextIntBetweenInclusive(-10, 10);
                BlockPos pos = player.blockPosition().offset(dx, dy, dz);
                BlockState state = level.getBlockState(pos);
                float hardness = state.getDestroySpeed(level, pos);
                if (hardness >= 0 && hardness <= 0.3f && !state.isAir()) {
                    BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(level, pos, state, player);
                    NeoForge.EVENT_BUS.post(breakEvent);
                    if (!breakEvent.isCanceled()) {
                        level.destroyBlock(pos, false);
                    }
                }
            }
        }
    }

    private static StormWingVisualEntity visual(ServerLevel level, FlightSnapshot snapshot) {
        if (snapshot == null || snapshot.visualId() == null) return null;
        Entity entity = level.getEntity(snapshot.visualId());
        return entity instanceof StormWingVisualEntity visual ? visual : null;
    }

    private static double approach(double from, double to, double limit) {
        double delta = to - from;
        return from + Math.min(Math.abs(delta), limit) * Math.signum(delta);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FLIGHT.clear();
        EXTERNAL_FLIGHT.clear();
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(30, 10, proficiency);
    }
}

