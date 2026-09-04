package com.mohistmc.academy.listener;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ClientSessionState;
import com.mohistmc.academy.world.block.IDevMachine;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * 客户端事件监听器
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class ClientListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSessionState.reset();
    }

    @SubscribeEvent
    public static void onClientLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ClientSessionState.reset();
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        BlockHitResult hitResult = event.getTarget();

        var level = event.getCamera().getEntity().level();
        BlockState state = level.getBlockState(hitResult.getBlockPos());
        if (state.getBlock() instanceof IDevMachine) {
          event.setCanceled(true);
        }
    }

    /** Suppress legacy-overridden attack input and treat a right-click miss as cancel. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (com.mohistmc.academy.client.gui.FreqTransmitterGui.isTargetingWorldBlock()) {
            if (event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                return;
            }
            if (event.isUseItem() && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
                var hit = net.minecraft.client.Minecraft.getInstance().hitResult;
                if (!(hit instanceof net.minecraft.world.phys.BlockHitResult)) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                    com.mohistmc.academy.client.gui.FreqTransmitterGui.cancelActiveSession();
                }
            }
            return;
        }

        // In 1.0.7, mapped ability delegates owned their mouse buttons while
        // abilities were active. Preserve normal mining/use for empty slots.
        boolean overridden = event.isAttack()
                ? com.mohistmc.academy.client.KeyInputHandler.overridesMouseButton(
                        GLFW.GLFW_MOUSE_BUTTON_LEFT)
                : event.isUseItem()
                && com.mohistmc.academy.client.KeyInputHandler.overridesMouseButton(
                        GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (overridden) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }
}
