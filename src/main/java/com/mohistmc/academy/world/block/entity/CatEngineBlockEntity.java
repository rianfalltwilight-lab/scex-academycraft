package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 风力猫引擎 —— 实现 IWirelessGenerator 向无线能源网络供电。
 * @author Mgazul
 */
public class CatEngineBlockEntity extends BlockEntity implements IWirelessGenerator {

    public int time;
    public float rot;
    public float oRot;
    public float tRot;
    /** Client-visible legacy spin state: the cat spins while its buffer is refilling. */
    public boolean enable = false;
    public float rH = 0;

    /** AcademyCraft 1.0.7 TileCatEngine: buffer 2000, bandwidth 200, generation 500/t. */
    private static final int MAX_STORAGE = 2000;
    private static final double BANDWIDTH = 200;
    private static final double MAX_GENERATION = 500;
    private float storedEnergy = 0;
    private double thisTickGeneration;
    private boolean linked;
    private long lastClientSyncTick = Long.MIN_VALUE;

    public CatEngineBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.CAT_ENGINE.get(), pos, state);
    }

    // ==================== Animation ====================

    public static void tickAnim(Level level, BlockPos blockPos, BlockState blockState, CatEngineBlockEntity e) {
        e.oRot = e.rot;
        Player player = level.getNearestPlayer(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 10, false);
        if (player != null) {
            double d0 = player.getX() - ((double) blockPos.getX() + 0.5D);
            double d1 = player.getZ() - ((double) blockPos.getZ() + 0.5D);
            e.tRot = (float) Mth.atan2(d1, d0);
        }
        while (e.rot >= (float) Math.PI) e.rot -= ((float) Math.PI * 2F);
        while (e.rot < -(float) Math.PI) e.rot += ((float) Math.PI * 2F);
        while (e.tRot >= (float) Math.PI) e.tRot -= ((float) Math.PI * 2F);
        while (e.tRot < -(float) Math.PI) e.tRot += ((float) Math.PI * 2F);

        float f2;
        for (f2 = e.tRot - e.rot; f2 >= (float) Math.PI; f2 -= ((float) Math.PI * 2F)) {}
        while (f2 < -(float) Math.PI) f2 += ((float) Math.PI * 2F);
        e.rot += f2 * 0.4F;
        ++e.time;
    }

    /** Fill the legacy finite buffer; wireless transfer happens later in the server tick. */
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        float before = storedEnergy;
        double required = Math.max(0, MAX_STORAGE - storedEnergy);
        thisTickGeneration = Math.min(required, MAX_GENERATION);
        storedEnergy = Math.min(MAX_STORAGE, storedEnergy + (float) thisTickGeneration);

        boolean nowLinked = false;
        if (level instanceof ServerLevel server) {
            WiWorldData data = WiWorldData.getNonCreate(server);
            nowLinked = data != null && data.getNodeConnection(this) != null;
        }
        boolean runtimeChanged = linked != nowLinked || enable != (thisTickGeneration > 0);
        linked = nowLinked;
        enable = thisTickGeneration > 0;
        if (before != storedEnergy || runtimeChanged) setChanged();

        long now = level.getGameTime();
        if (runtimeChanged || lastClientSyncTick == Long.MIN_VALUE || now - lastClientSyncTick >= 20) {
            level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            lastClientSyncTick = now;
        }
    }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        double give = Math.min(Math.max(0, req), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) setChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return BANDWIDTH;
    }

    public float getStoredEnergy() { return storedEnergy; }
    public int getMaxStorage() { return MAX_STORAGE; }
    public double getThisTickGeneration() { return thisTickGeneration; }
    public boolean isLinked() { return linked; }

    public void setLinkedForSync(boolean value) {
        if (linked == value) return;
        linked = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ==================== NBT ====================

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("storedEnergy")) storedEnergy = MachineStateSanitizer.clampFinite(tag.getFloat("storedEnergy"), MAX_STORAGE);
        if (tag.contains("thisTickGeneration")) {
            double loaded = tag.getDouble("thisTickGeneration");
            thisTickGeneration = Double.isFinite(loaded) ? Math.clamp(loaded, 0, MAX_GENERATION) : 0;
        }
        if (tag.contains("linked")) linked = tag.getBoolean("linked");
        if (tag.contains("enable")) enable = tag.getBoolean("enable");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("storedEnergy", storedEnergy);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.putDouble("thisTickGeneration", thisTickGeneration);
        tag.putBoolean("linked", linked);
        tag.putBoolean("enable", enable);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
