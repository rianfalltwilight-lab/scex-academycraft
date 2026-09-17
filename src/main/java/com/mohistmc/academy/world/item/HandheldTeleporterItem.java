package com.mohistmc.academy.world.item;

import com.mohistmc.academy.network.LocationTeleportChunkPlan;
import com.mohistmc.academy.skill.ability.teleporter.TeleportDestinations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/** Overworld-only bind-and-return teleporter consuming 5,000 IF and one ender pearl. */
public final class HandheldTeleporterItem extends ExtraEnergyItem {
    private static final TicketType<UUID> TELEPORT_TICKET =
            TicketType.create("academy_handheld_teleport", Comparator.<UUID>naturalOrder(), 20);
    public HandheldTeleporterItem() { super(10_000, 100); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.dimension().equals(Level.OVERWORLD)) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.fail(stack);

        ExtraItemData.TeleportTarget target = ExtraItemData.teleport(stack);
        if (player.isShiftKeyDown()) {
            if (target == null) {
                ExtraItemData.setTeleport(stack, Level.OVERWORLD.location(),
                        player.getX(), player.getY(), player.getZ());
                player.sendSystemMessage(Component.translatable("item.academy.handheld_teleporter.bound"));
            } else {
                ExtraItemData.clearTeleport(stack);
                player.sendSystemMessage(Component.translatable("item.academy.handheld_teleporter.cleared"));
            }
            return InteractionResultHolder.consume(stack);
        }
        if (target == null || (!player.getAbilities().instabuild
                && (getEnergyStored(stack) < 5_000 || !ExtraItemActions.has(player, Items.ENDER_PEARL))))
            return InteractionResultHolder.fail(stack);

        ServerLevel destination = serverPlayer.server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, target.dimension()));
        if (destination == null || destination != serverPlayer.serverLevel())
            return InteractionResultHolder.fail(stack);
        Vec3 feet = new Vec3(target.x(), target.y(), target.z());
        if (!TeleportDestinations.isWithinBounds(player, destination, feet))
            return InteractionResultHolder.fail(stack);
        var box = player.getBoundingBox().move(feet.subtract(player.position()));
        var chunks = new LinkedHashSet<LocationTeleportChunkPlan.Chunk>();
        if (!LocationTeleportChunkPlan.addBox(chunks, box.minX, box.minZ, box.maxX, box.maxZ, 16))
            return InteractionResultHolder.fail(stack);
        UUID ticket = UUID.randomUUID();
        var ticketed = new ArrayList<ChunkPos>();
        try {
            for (var chunk : chunks) {
                ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
                destination.getChunkSource().addRegionTicket(TELEPORT_TICKET, pos, 1, ticket);
                ticketed.add(pos);
                if (destination.getChunkSource().getChunk(chunk.x(), chunk.z(), ChunkStatus.FULL, true) == null)
                    return InteractionResultHolder.fail(stack);
            }
            if (!TeleportDestinations.isSafe(player, destination, feet)) return InteractionResultHolder.fail(stack);
            return teleport(serverPlayer, destination, stack, feet);
        } finally {
            for (ChunkPos pos : ticketed) destination.getChunkSource().removeRegionTicket(TELEPORT_TICKET, pos, 1, ticket);
        }
    }

    private InteractionResultHolder<ItemStack> teleport(ServerPlayer player, ServerLevel destination,
                                                       ItemStack stack, Vec3 feet) {
        if (!player.getAbilities().instabuild
                && (getEnergyStored(stack) < 5_000 || !ExtraItemActions.has(player, Items.ENDER_PEARL)))
            return InteractionResultHolder.fail(stack);
        // Same-level ServerPlayer.teleportTo commits synchronously. Check its result before payment.
        Vec3 origin = player.position();
        if (!player.teleportTo(destination, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot()))
            return InteractionResultHolder.fail(stack);
        if (!player.getAbilities().instabuild) {
            if (!ExtraItemActions.consumeOne(player, Items.ENDER_PEARL)) return InteractionResultHolder.fail(stack);
            consume(stack, 5_000);
        }
        destination.playSound(null, net.minecraft.core.BlockPos.containing(origin), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        player.fallDistance = 0;
        destination.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ExtraItemData.TeleportTarget target = ExtraItemData.teleport(stack);
        if (target == null) tooltip.add(Component.translatable(
                "item.academy.handheld_teleporter.unbound").withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("item.academy.handheld_teleporter.position",
                (int) target.x(), (int) target.y(), (int) target.z()).withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return ExtraItemData.teleport(stack) != null; }
}
