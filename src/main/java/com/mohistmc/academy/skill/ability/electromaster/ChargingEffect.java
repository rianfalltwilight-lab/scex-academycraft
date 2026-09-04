package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 电流回冲 —— 持续按住给方块/物品充能 */
public class ChargingEffect implements ChargingSkillEffect {

    private static final int MIN_TICKS = 1;
    private static final Map<UUID, Float> OVERLOAD_FLOORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ITEM_MODES = new ConcurrentHashMap<>();

    @FunctionalInterface
    private interface Receiver {
        int receive(int amount, boolean simulate);
    }

    @Override
    public String getId() {
        return "charging";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return MIN_TICKS;
    }

    @Override
    public int getMaxChargeTicks() {
        return 1;
    }

    @Override
    public int getSessionTimeoutTicks(PlayerAbilityData data) {
        return Integer.MAX_VALUE;
    }

    @Override
    public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }

    @Override public boolean canRelease(ServerPlayer player,PlayerAbilityData data,int ticks){return ticks>=1&&OVERLOAD_FLOORS.containsKey(player.getUUID());}

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        float overload = lerpf(65, 48, data.getProficiency(getId()));
        return DynamicSkillRules.canPay(data, getId(), 0, overload);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float overload = lerpf(65, 48, exp);
        if (!DynamicSkillRules.tryPay(data,getId(),0,overload)) return;
        OVERLOAD_FLOORS.put(player.getUUID(), data.getCurrentOverload());
        // 1.0.7 captures this mode once on key-down, even for an unsupported item.
        ITEM_MODES.put(player.getUUID(), !player.getMainHandItem().isEmpty());
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float consumption = lerpf(3, 7, exp);
        float chargeAmount = lerpf(15, 35, exp);
        Float overloadFloor = OVERLOAD_FLOORS.get(player.getUUID());
        if (overloadFloor == null) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < overloadFloor) {
            data.setCurrentOverload(overloadFloor);
        }

        int simulated = 0;
        int requested = (int) chargeAmount;
        Receiver receiver = null;
        ItemStack held = player.getMainHandItem();
        Boolean itemMode = ITEM_MODES.get(player.getUUID());
        if (itemMode == null || itemMode && held.isEmpty()) return false;
        boolean supported = false;
        Vec3 visualEnd = player.getEyePosition().add(player.getLookAngle().scale(15.0));

        if (itemMode) {
            supported = EnergyItemHelper.isEnergyItem(held);
            if (supported) {
                receiver = (amount, simulate) -> EnergyItemHelper.receiveEnergy(held, amount, simulate);
            } else {
                // 1.0.7 registered IC2/RF EnergyItemManagers. NeoForge's
                // item energy capability is their 1.21.1 compatibility edge.
                IEnergyStorage external = held.getCapability(Capabilities.EnergyStorage.ITEM);
                if (external != null) {
                    supported = true;
                    receiver = external::receiveEnergy;
                    requested = ExternalEnergyConversion.ifToFe(requested);
                }
            }
        } else {
            BlockHitResult hit = (BlockHitResult) player.pick(15.0, 0, false);
            ServerLevel level = player.serverLevel();
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && level.hasChunkAt(hit.getBlockPos())) {
                visualEnd = hit.getLocation();
                BlockPos pos = hit.getBlockPos();
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IFEnergyStorage storage) {
                    supported = true;
                    receiver = storage::receiveEnergy;
                } else {
                    // Query the struck face first, matching sided NeoForge
                    // receivers. A null-context fallback keeps unsided legacy
                    // and third-party providers usable.
                    IEnergyStorage external = level.getCapability(
                            Capabilities.EnergyStorage.BLOCK, pos, hit.getDirection());
                    if (external == null) external = level.getCapability(
                            Capabilities.EnergyStorage.BLOCK, pos, null);
                    if (external != null) {
                        supported = true;
                        receiver = external::receiveEnergy;
                        requested = ExternalEnergyConversion.ifToFe(requested);
                    }
                }
            }
        }

        if (receiver != null) simulated = receiver.receive(requested, true);

        // 1.0.7 consumes CP and grants the smaller practice increment even
        // while the ray points at an unsupported/full target.
        if (!DynamicSkillRules.tryPay(data,getId(),consumption,0)) return false;
        int accepted = 0;
        if (simulated > 0 && receiver != null) {
            int committed = receiver.receive(simulated, false);
            accepted = ElectromasterRules.committedCharge(simulated, committed);
        }
        if ((ticks & 1) == 0) {
            Vec3 eye = player.getEyePosition();
            if (!itemMode) {
                // The old charging arc always reached the ray endpoint (or the
                // full 15-block miss), while the surround arc was visible only
                // on a supported receiver.
                EffectHelper.electricTether(player.serverLevel(), eye, visualEnd, 2);
                if (supported) {
                    EffectHelper.arcSpark(player.serverLevel(), visualEnd.x, visualEnd.y,
                            visualEnd.z, 3, .08, 2);
                }
            } else {
                EffectHelper.arcSpark(player.serverLevel(), player.getX(),
                        player.getY() + .8, player.getZ(), 3, .08, 2);
            }
        }
        DynamicSkillRules.addExp(player,data, getId(),
                supported ? 0.0001f : 0.00003f);
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        // Proficiency is settled per effective accepted tick, never for rejected receives.
        OVERLOAD_FLOORS.remove(player.getUUID());
        ITEM_MODES.remove(player.getUUID());
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        OVERLOAD_FLOORS.remove(player.getUUID());
        ITEM_MODES.remove(player.getUUID());
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        // 蓄力技能通过 Charging 接口执行
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return 0;
    }
}
