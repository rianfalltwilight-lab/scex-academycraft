package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.menu.EnergyBridgeMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Legacy energy bridge opens directly on WirelessPage.userPage. */
public final class EnergyBridgeGui extends AcademyBaseUI<EnergyBridgeMenu> {
    private static final ResourceLocation PARENT = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/parent/parent_background.png");

    public EnergyBridgeGui(EnergyBridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WirelessState.WIFI);
        setRenderWireless(true);
    }

    @Override
    protected void init() {
        super.init();
        openInitialWirelessPanel();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, leftPos, topPos, graphics, PARENT);
        RenderSystem.disableBlend();
        renderEnergyInfoPanel(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable(menu.isInput()
                        ? "gui.academy.energy_bridge.input" : "gui.academy.energy_bridge.output"),
                13, 28, 0xE8F6FF, false);
        graphics.drawString(font, Component.translatable("gui.academy.energy_bridge.ratio"),
                13, 45, 0xBFEAFF, false);
        graphics.drawString(font, String.format("%.2f / %.0f IF", menu.getStoredIf(), menu.getMaxIf()),
                13, 62, 0xFFFFFF, false);
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(InfoArea.histBuffer(menu.getStoredIf(), menu.getMaxIf()));
        info.seplineInfo().property(menu.isInput() ? "方向" : "方向",
                menu.isInput() ? "RF/FE → IF" : "IF → RF/FE");
        info.draw(graphics, leftPos, topPos);
    }
}
