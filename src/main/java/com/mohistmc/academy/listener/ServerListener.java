package com.mohistmc.academy.listener;

import com.mohistmc.academy.command.LegacyAimCommands;
import com.mohistmc.academy.command.LegacyAchievementCommands;
import com.mohistmc.academy.command.MachineOwnershipMigrationCommands;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.tutorial.TutorialUnlocks;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.MagManipTransactionData;
import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * 服务器事件监听器
 */
public class ServerListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
        var data=MagManipTransactionData.get(event.getServer().overworld());
        var result=data.migrateLoadedRepresentations(event.getServer().overworld(),64);
        if(result.cleaned()>0||result.pending()>0)LOGGER.warn("MagManip safe-projection migration cleaned={} pending={} malformed/absent-dimension={}; pending rows remain durable and nothing was materialized",result.cleaned(),result.pending(),result.malformed());
    }

    @SubscribeEvent
    public void retryMagManipMigration(ServerTickEvent.Post event) {
        if((event.getServer().getTickCount()%100)!=0)return;
        var data=MagManipTransactionData.get(event.getServer().overworld());
        if(data.pendingMigrationCount()==0)return;
        var result=data.migrateLoadedRepresentations(event.getServer().overworld(),16);
        if(result.cleaned()>0)LOGGER.warn("MagManip migration retry cleaned={} pending={}; no chunks were force-loaded",result.cleaned(),result.pending());
    }

    // ==================== 物品获得记录(教程条件) ====================

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (!stack.isEmpty()) { markObtained(event.getPlayer(), stack.getItem());
            if(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp)
                com.mohistmc.academy.advancement.LegacyAdvancementBridge.obtained(sp,BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),false); }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            MagManipBlockEntity.resumeLoadedTransaction(player);
            TutorialUnlocks.reconcile(player.getData(AcademyAttachments.PLAYER_ABILITY));
            grantLegacyTutorialItem(player);
        }
    }

    /**
     * AcademyCraft 1.0.7 TutorialData spawned one tutorial item above each
     * player once, controlled by generic.giveCloudTerminal.  Keep the item as
     * an entity instead of silently inserting it so a full inventory behaves
     * like the original and the grant is visible to the player.
     */
    private static void grantLegacyTutorialItem(net.minecraft.server.level.ServerPlayer player) {
        if (!ACConfig.Server.giveCloudTerminal()) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (data.isTutorialItemGranted()) return;

        data.setTutorialItemGranted(true);
        ItemStack stack = new ItemStack(AcademyItems.TUTORIAL.get());
        ItemEntity drop = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + 1.0,
                player.getZ(), stack);
        if (!player.serverLevel().addFreshEntity(drop)) {
            // Entity creation can be canceled by an integration.  Leave the
            // grant retryable instead of permanently losing the item.
            data.setTutorialItemGranted(false);
            return;
        }
        data.syncTo(player);
    }


    @SubscribeEvent
    public void onItemCraft(PlayerEvent.ItemCraftedEvent event) {
        markObtained(event.getEntity(), event.getCrafting().getItem());
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)
            com.mohistmc.academy.advancement.LegacyAdvancementBridge.obtained(sp,BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).toString(),true);
    }

    @SubscribeEvent
    public void onItemSmelt(PlayerEvent.ItemSmeltedEvent event) {
        markObtained(event.getEntity(), event.getSmelting().getItem());
    }

    private static void markObtained(Player player, Item item) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!data.hasObtained(itemId)) {
            data.markObtained(itemId);
            String tutorialId = TutorialUnlocks.activateForItem(data, itemId);
            if (tutorialId != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                NeoForge.EVENT_BUS.post(new com.mohistmc.academy.api.event.TutorialEvents.Activated(
                        serverPlayer, tutorialId));
                com.mohistmc.academy.network.SafePayloadSender.send(serverPlayer,
                        new com.mohistmc.academy.network.TutorialActivatedPacket(tutorialId));
            }
            data.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LegacyAimCommands.register(event.getDispatcher());
        LegacyAchievementCommands.register(event.getDispatcher());
        MachineOwnershipMigrationCommands.register(event.getDispatcher());
    }
}
