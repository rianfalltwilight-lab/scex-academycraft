package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ImagFusorGui extends AcademyBaseUI<ImagFusorMenu> {

    private static final ResourceLocation UI_IMAG_FUSOR = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_imagfusor.png");
    private static final ResourceLocation PROGRESS_FUSOR = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/progress/progress_fusor.png");
    // page_imagfusor.xml: x=0.1875 centered, y=46.5, w=60.96875, h=15.
    private static final int PROGRESS_X = 58;
    private static final int PROGRESS_Y = 47;
    private static final int PROGRESS_W = 61;
    private static final int PROGRESS_H = 15;
    private static final int PROGRESS_TEX_W = 126;
    private static final int PROGRESS_TEX_H = 30;
    public ImagFusorGui(ImagFusorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.WIFI);
        // Legacy GuiImagFusor includes WirelessPage.userPage(tile).
        setRenderWireless(true);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String text = menu.getCurrentRecipePhaseLiquid() <= 0
                ? "IDLE" : Integer.toString(menu.getCurrentRecipePhaseLiquid());
        // page_imagfusor.xml: 44x12 label at x=68,y=12, font size 12.
        // AbstractContainerScreen has already translated this pose to the GUI
        // origin, so render the legacy 12px centred label in local space.
        graphics.pose().pushPose();
        graphics.pose().translate(90, 13, 0);
        graphics.pose().scale(4.0f / 3.0f, 4.0f / 3.0f, 1.0f);
        graphics.drawCenteredString(this.font, text, 0, 0, 0xCCFFFFFF);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderStandardMachinePanel(graphics, UI_IMAG_FUSOR);
        renderLegacyProgress(graphics);
        renderEnergyInfoPanel(graphics);
    }

    private void renderLegacyProgress(GuiGraphics graphics) {
        int progress = menu.getProcessingTime();
        if (progress <= 0) return;
        int width = Math.clamp(progress * PROGRESS_W / menu.getProcessingDuration(), 0, PROGRESS_W);
        if (width <= 0) return;
        int sampledWidth = width * PROGRESS_TEX_W / PROGRESS_W;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(PROGRESS_FUSOR, this.leftPos + PROGRESS_X, this.topPos + PROGRESS_Y,
                width, PROGRESS_H, 0f, 0f, sampledWidth, PROGRESS_TEX_H,
                PROGRESS_TEX_W, PROGRESS_TEX_H);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(
                InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy()),
                InfoArea.histPhaseLiquid(menu.getFluidAmount(), menu.getMaxFluid()));
        String process = menu.getProcessingTime() <= 0
                ? "待机"
                : menu.getProcessingTime() + "/" + menu.getProcessingDuration() + " t";
        info.seplineInfo().property("进度", process);
        info.draw(graphics, this.leftPos, this.topPos);
    }
}
