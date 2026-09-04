package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.WindGenBase;
import com.mohistmc.academy.world.block.WindGenBaseSubBlock;
import com.mohistmc.academy.world.block.WindGenMain;
import com.mohistmc.academy.world.block.WindGenPillar;
import com.mohistmc.academy.world.block.WindGenFan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

public class WindGenMainBlockEntity extends AcademyContainerBlockEntity {
    private static final long FAN_RETRY_TICKS = 40;
    private long nextFanAttemptTick;
    private long lastRefreshTick = Long.MIN_VALUE;
    private UUID ownerUUID;
    /** Runtime state belongs to this placed turbine, never to the registered Block singleton. */
    private boolean structureComplete;
    private boolean fanInstalled;
    private boolean working;
    private boolean visualFanVisible;

    public WindGenMainBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(AcademyBlockEntities.WINDGEN_MAIN.get(), p_155229_, p_155230_);
        setItems(net.minecraft.core.NonNullList.withSize(getContainerSize(), net.minecraft.world.item.ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; setChanged(); }
    /** Migrate a command-placed or pre-owner turbine only through an administrator interaction. */
    public boolean claimLegacyOwnerIfAbsent(Player player) {
        if (MachineOwnership.canClaimLegacy(ownerUUID, player)) {
            ownerUUID = player.getUUID();
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
            return true;
        }
        return false;
    }
    /** Compatibility name retained for existing integration tests/callers. */
    public boolean hasValidFan() { return working; }
    public boolean isStructureComplete() { return structureComplete; }
    public boolean isFanInstalled() { return fanInstalled; }
    public boolean isWorking() { return working; }
    public boolean isVisualFanVisible() { return visualFanVisible; }

    /** Only the forward proxy renders the large fan model. */
    public boolean shouldRenderFanAt(BlockPos proxyPos) {
        if (!visualFanVisible || proxyPos == null || !(getBlockState().getBlock() instanceof WindGenMain)) {
            return false;
        }
        return fanPosition(worldPosition, getBlockState().getValue(WindGenMain.FACING)).equals(proxyPos);
    }

    /** Return the position immediately in front of the horizontal main block. */
    public static BlockPos fanPosition(BlockPos mainPos, Direction facing) {
        return switch (facing) {
            case EAST -> mainPos.east();
            case WEST -> mainPos.west();
            case NORTH -> mainPos.north();
            case SOUTH -> mainPos.south();
            default -> mainPos;
        };
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        ownerUUID = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        nextFanAttemptTick = Math.max(0L, tag.getLong("next_fan_attempt"));
        structureComplete = tag.getBoolean("structure_complete");
        fanInstalled = tag.getBoolean("fan_installed");
        working = tag.getBoolean("working");
        visualFanVisible = tag.getBoolean("visual_fan_visible");
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (ownerUUID != null) tag.putUUID("owner", ownerUUID);
        tag.putLong("next_fan_attempt", nextFanAttemptTick);
        putRuntimeState(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        putRuntimeState(tag);
        return tag;
    }

    private void putRuntimeState(CompoundTag tag) {
        tag.putBoolean("structure_complete", structureComplete);
        tag.putBoolean("fan_installed", fanInstalled);
        tag.putBoolean("working", working);
        tag.putBoolean("visual_fan_visible", visualFanVisible);
    }

    public void tick(WindGenMain block, Level level, BlockPos pos, Direction facing) {
        refreshIfDue(level, pos, facing);
    }

    /** AcademyCraft 1.12.2 rechecked the 15x15 clearance plane every ten ticks. */
    public boolean refreshIfDue(Level level, BlockPos pos, Direction facing) {
        long now = level.getGameTime();
        if (lastRefreshTick == Long.MIN_VALUE || now - lastRefreshTick >= 10) {
            lastRefreshTick = now;
            return refreshFanState(level, pos, facing);
        }
        return working;
    }

    /**
     * Re-evaluate the complete fan/clearance state synchronously.  The base
     * ticker calls this before generation, so generation never depends on
     * whether the main BE happened to tick first in a given server tick.
     */
    public boolean refreshFanState(Level level, BlockPos pos, Direction facing) {
        if (level == null || pos == null || facing == null) {
            setRuntimeState(false, false, false, false);
            return false;
        }
        boolean hasBase = findBase() != null
                && WindGenMain.hasCompleteProxySet(level, pos, getBlockState());
        BlockPos fanPos = fanPosition(pos, facing);

        // A fan is a single legacy item.  Sanitise malformed NBT/old worlds
        // before checking it so a copied stack can never turn into a hidden
        // multi-fan inventory.
        sanitizeFanSlot(level, pos);
        boolean installed = isFanItemInstalled();
        boolean clear = hasBase && checkFanSpace(level, fanPos, facing);
        boolean nowWorking = hasBase && installed && clear;
        setRuntimeState(hasBase, installed, nowWorking, nowWorking);
        updateProxyRunning(level, pos, nowWorking);
        return nowWorking;
    }

    public boolean isFanItemInstalled() {
        return !getItems().isEmpty() && getItems().get(0).is(AcademyItems.WINDGEN_FAN.get());
    }

    private void sanitizeFanSlot(Level level, BlockPos pos) {
        if (getItems().isEmpty()) return;
        ItemStack stack = getItems().get(0);
        if (!stack.is(AcademyItems.WINDGEN_FAN.get()) || stack.getCount() <= 1) return;
        int overflow = stack.getCount() - 1;
        stack.setCount(1);
        setChanged();
        if (!level.isClientSide && overflow > 0) {
            ItemStack returned = new ItemStack(AcademyItems.WINDGEN_FAN.get(), overflow);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, returned));
        }
    }

    private void setRuntimeState(boolean complete, boolean installed, boolean nowWorking, boolean visible) {
        boolean changed = structureComplete != complete || fanInstalled != installed
                || working != nowWorking || visualFanVisible != visible;
        structureComplete = complete;
        fanInstalled = installed;
        working = nowWorking;
        visualFanVisible = visible;
        if (changed) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private void updateProxyRunning(Level level, BlockPos mainPos, boolean running) {
        for (BlockPos proxy : WindGenMain.proxyPositions(mainPos, getBlockState())) {
            BlockEntity fanEntity = level.getBlockEntity(proxy);
            if (fanEntity instanceof WindGenFanBlockEntity fan && fan.isRunning != running) {
                fan.isRunning = running;
                fan.setChanged();
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(proxy, fan.getBlockState(), fan.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Permission checked automatic placement with cancellation backoff. */
    public boolean tryPlaceFan(Level level, BlockPos fanPos, Direction facing) {
        if (level == null || fanPos == null || facing == null) return false;
        if (level.getGameTime() < nextFanAttemptTick) return false;
        if (!(level instanceof ServerLevel server) || ownerUUID == null || !server.isLoaded(fanPos)) {
            backOffFanPlacement(level);
            return false;
        }
        var actor = FakePlayerFactory.get(server, new GameProfile(ownerUUID, "[AcademyWind]"));
        if (!server.mayInteract(actor, fanPos)) {
            backOffFanPlacement(level);
            return false;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, fanPos, 19);
        boolean placed = level.setBlock(fanPos, AcademyBlocks.WINDGEN_FAN.get()
                .defaultBlockState().setValue(WindGenFan.FACING, facing), 19);
        if (!placed || EventHooks.onBlockPlace(actor, snapshot, facing)) {
            snapshot.restore();
            backOffFanPlacement(level);
            return false;
        }
        return true;
    }

    private void backOffFanPlacement(Level level) {
        nextFanAttemptTick = level.getGameTime() + FAN_RETRY_TICKS;
        setChanged();
    }

    private boolean checkFanSpace(Level level, BlockPos pos, Direction facing) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (facing == Direction.EAST || facing == Direction.WEST) {
            for (int dy = -7; dy <= 7; dy++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (dy == 0 && dz == 0) continue;
                    BlockPos checkPos = new BlockPos(x, y + dy, z + dz);
                    BlockState state = level.getBlockState(checkPos);
                    if (!state.isAir()) {
                        return false;
                    }
                }
            }
        } else {
            for (int dy = -7; dy <= 7; dy++) {
                for (int dx = -7; dx <= 7; dx++) {
                    if (dy == 0 && dx == 0) continue;
                    BlockPos checkPos = new BlockPos(x + dx, y + dy, z);
                    BlockState state = level.getBlockState(checkPos);
                    if (!state.isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private WindGenBaseBlockEntity findBase() {
        if (level == null) return null;
        BlockPos pos = getBlockPos();
        int pillars = 0;
        boolean sawBaseSubBlock = false;
        for (int i = 1; i <= WindGenBase.MAX_PILLARS + 2; i++) {
            BlockPos below = pos.below(i);
            BlockEntity be = level.getBlockEntity(below);
            Block block = level.getBlockState(below).getBlock();
            if (!sawBaseSubBlock && block instanceof WindGenPillar) {
                pillars++;
                if (pillars > WindGenBase.MAX_PILLARS) return null;
                continue;
            }
            if (!sawBaseSubBlock && block instanceof WindGenBaseSubBlock) {
                if (pillars < WindGenBase.MIN_PILLARS) return null;
                sawBaseSubBlock = true;
                continue;
            }
            if (sawBaseSubBlock && be instanceof WindGenBaseBlockEntity) {
                return pillars <= WindGenBase.MAX_PILLARS ? (WindGenBaseBlockEntity) be : null;
            }
            return null;
        }
        return null;
    }
}
