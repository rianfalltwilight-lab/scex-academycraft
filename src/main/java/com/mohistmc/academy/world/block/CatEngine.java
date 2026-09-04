package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.entity.CatEngineBlockEntity;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.impl.NodeConn;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CatEngine extends BaseEntityBlock {

    public static final MapCodec<CatEngine> CODEC = simpleCodec(CatEngine::new);
    public CatEngine(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CatEngine> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState p_220827_, Level p_220828_, BlockPos p_220829_, RandomSource p_220830_) {

    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new CatEngineBlockEntity(p_153215_, p_153216_);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof CatEngineBlockEntity engine)
                || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }

        WiWorldData data = WiWorldData.getNonCreate(server);
        NodeConn existing = data == null ? null : data.getNodeConnection(engine);
        if (existing != null) {
            if (WirelessSystem.unlinkUser(server, engine)) {
                engine.setLinkedForSync(false);
                player.sendSystemMessage(Component.translatable("ac.cat_engine.unlink"));
            }
            return InteractionResult.CONSUME;
        }

        List<IWirelessNode> candidates = linkableNodes(server, pos, player);
        if (candidates.isEmpty()) {
            player.sendSystemMessage(Component.translatable("ac.cat_engine.notfound"));
            return InteractionResult.CONSUME;
        }

        IWirelessNode node = candidates.get(server.random.nextInt(candidates.size()));
        if (WirelessSystem.linkGenerator(server, node, engine, false, "")) {
            engine.setLinkedForSync(true);
            player.sendSystemMessage(Component.translatable("ac.cat_engine.linked", node.getNodeName()));
        } else {
            // The connection can become full between discovery and commit.
            player.sendSystemMessage(Component.translatable("ac.cat_engine.notfound"));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Legacy WirelessHelper.getNodesInRange searched a 20-block sphere and
     * accepted standalone nodes; a Matrix was never a prerequisite.  Keep the
     * scan server-side and loaded-chunk-only so right-click cannot force-load
     * terrain or trust a client-supplied position.
     */
    static List<IWirelessNode> linkableNodes(ServerLevel level, BlockPos center, Player player) {
        final int radius = 20;
        final int radiusSq = radius * radius;
        WiWorldData data = WiWorldData.getNonCreate(level);
        List<IWirelessNode> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius && result.size() < 100; dx++) {
            for (int dz = -radius; dz <= radius && result.size() < 100; dz++) {
                int horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > radiusSq) continue;
                int maxDy = (int) Math.sqrt(radiusSq - horizontalSq);
                for (int dy = -maxDy; dy <= maxDy && result.size() < 100; dy++) {
                    BlockPos candidatePos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(candidatePos) || !level.mayInteract(player, candidatePos)) continue;
                    if (!(level.getBlockEntity(candidatePos) instanceof IWirelessNode node)) continue;
                    double range = Double.isFinite(node.getRange())
                            ? Math.clamp(node.getRange(), 0.0, 256.0) : 0.0;
                    if (center.distSqr(candidatePos) > range * range) continue;
                    NodeConn conn = data == null ? null : data.getExistingNodeConnection(node);
                    if (conn == null || conn.getLoad() < conn.getCapacity()) result.add(node);
                }
            }
        }
        return result;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, AcademyBlockEntities.CAT_ENGINE.get(), CatEngineBlockEntity::tickAnim);
        }
        return (l, p, s, be) -> {
            if (be instanceof CatEngineBlockEntity e) {
                e.tick(l, p, s);
            }
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Revoke the exact generator link before the block entity leaves the
            // level. Waiting for the periodic stale-node sweep leaves a window in
            // which a replacement at the same position can inherit the old edge.
            if (level instanceof ServerLevel server
                    && level.getBlockEntity(pos) instanceof CatEngineBlockEntity engine) {
                WirelessSystem.unlinkUser(server, engine);
                engine.setLinkedForSync(false);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

}
