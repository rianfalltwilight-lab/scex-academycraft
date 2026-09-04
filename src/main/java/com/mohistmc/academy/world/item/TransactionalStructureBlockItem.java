package com.mohistmc.academy.world.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

/** Atomic main+sub placement for Academy multiblocks. */
public abstract class TransactionalStructureBlockItem extends BlockItem {
    protected TransactionalStructureBlockItem(Block block, Properties properties) { super(block, properties); }
    protected abstract List<BlockPos> subPositions(BlockPos main, BlockState state);
    protected abstract BlockState subState();
    protected boolean committed(Level level, BlockPos main, BlockState state, List<BlockPos> subs) { return true; }

    @Override public InteractionResult place(BlockPlaceContext context) {
        Level level=context.getLevel(); Player player=context.getPlayer();
        BlockPos main=context.getClickedPos();
        if(!level.getBlockState(main).canBeReplaced(context)) main=main.relative(context.getClickedFace());
        BlockState predicted=getBlock().getStateForPlacement(context);
        if(predicted==null||player==null) return InteractionResult.FAIL;
        List<BlockPos> targets=subPositions(main,predicted);
        for(BlockPos pos:targets) {
            BlockState old=level.getBlockState(pos);
            if(level.getBlockEntity(pos)!=null||!old.canBeReplaced()||!level.mayInteract(player,pos)) return InteractionResult.FAIL;
        }
        ItemStack originalStack=context.getItemInHand().copy();
        BlockSnapshot mainSnapshot=!level.isClientSide?BlockSnapshot.create(level.dimension(),level,main,3):null;
        InteractionResult result=super.place(context);
        if(level.isClientSide||!result.consumesAction()||!level.getBlockState(main).is(getBlock())) return result;
        List<BlockSnapshot> snapshots=new ArrayList<>();
        for(BlockPos pos:targets) {
            BlockSnapshot snapshot=BlockSnapshot.create(level.dimension(),level,pos,3); snapshots.add(snapshot);
            BlockState wanted = subState();
            boolean placed = level.setBlock(pos, wanted, 19);
            boolean canceled = placed && EventHooks.onBlockPlace(player, snapshot, context.getClickedFace());
            // A protection/listener is allowed to mutate the just-placed block
            // without cancelling the event.  Treat that as a failed commit at
            // this exact step; continuing to place the remaining proxies emits
            // spurious placement events and exposes a transient partial
            // structure to other mods before the final validation runs.
            boolean retained = placed && level.getBlockState(pos).equals(wanted);
            if(!placed || canceled || !retained) {
                rollback(snapshots,mainSnapshot,player,context,originalStack);
                return InteractionResult.FAIL;
            }
        }
        boolean finalized;
        try { finalized=committed(level,main,level.getBlockState(main),targets); }
        catch(RuntimeException failure) { finalized=false; }
        if(!finalized) {
            rollback(snapshots,mainSnapshot,player,context,originalStack);
            return InteractionResult.FAIL;
        }
        return result;
    }

    private static void rollback(List<BlockSnapshot> snapshots, BlockSnapshot mainSnapshot, Player player,
                                 BlockPlaceContext context, ItemStack originalStack) {
        for(int i=snapshots.size()-1;i>=0;i--) snapshots.get(i).restore();
        mainSnapshot.restore();
        if(!player.getAbilities().instabuild) {
            ItemStack current=player.getItemInHand(context.getHand());
            if(current.isEmpty()) {
                // A one-item stack is replaced by ItemStack.EMPTY after vanilla
                // placement.  Restore the exact pre-transaction stack when the
                // world transaction is rolled back.
                player.setItemInHand(context.getHand(),originalStack.copy());
            } else if(current.is(originalStack.getItem())) {
                // Preserve component mutations made by protection/listener
                // code, while compensating only the count consumed by place().
                current.setCount(Math.max(current.getCount(),originalStack.getCount()));
            }
            // Never overwrite a different non-empty item installed by another
            // listener; ownership of that hand mutation is outside this block
            // placement transaction.
        }
    }
}
