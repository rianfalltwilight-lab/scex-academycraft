package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.menu.WindGenBaseMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class WindBaseGui extends AcademyBaseUI<WindGenBaseMenu> {

    private static final ResourceLocation UI_WIN_BASE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_windbase.png");
    private static final ResourceLocation IC_WIN_BASE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_wind_base.png");
    private static final ResourceLocation IC_WIN_MIDDLE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_wind_middle.png");
    private static final ResourceLocation IC_WIN_MAIN = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/icons/icon_wind_main.png");

    public WindBaseGui(WindGenBaseMenu menu, Inventory inv, Component p_97743_) {
        super(menu, inv, p_97743_, WirelessState.WIFI);
    }

    @Override
    protected void renderLabels(GuiGraphics p_97808_, int p_97809_, int p_97810_) {
    }

    @Override
    public void renderBackground(GuiGraphics stack, int mouseX, int mouseY, float p_97788_) {
        renderStandardMachinePanel(stack, UI_WIN_BASE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderMachineIcon(stack, IC_WIN_BASE, 49);
        float middleBrightness = menu.isMiddleComplete() ? 1.0f : 0.2f;
        RenderSystem.setShaderColor(middleBrightness, middleBrightness, middleBrightness, 1);
        renderMachineIcon(stack, IC_WIN_MIDDLE, 31);
        float mainBrightness = !menu.isStructureComplete() ? 0.2f : menu.isWorking() ? 1.0f : 0.6f;
        RenderSystem.setShaderColor(mainBrightness, mainBrightness, mainBrightness, 1);
        renderMachineIcon(stack, IC_WIN_MAIN, 13);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        renderWindInfo(stack);
    }

    private void renderMachineIcon(GuiGraphics graphics, ResourceLocation texture, int y) {
        RenderUtils.render(24, 24, this.leftPos + (GUI_WIDTH - 24) / 2,
                this.topPos + y, graphics, texture);
    }

    private void renderWindInfo(GuiGraphics graphics) {
        new InfoArea()
                .histogram(InfoArea.histBuffer(menu.getEnergy(), menu.getMaxEnergy()))
                .seplineInfo()
                .property("海拔", menu.pos == null ? "-" : Integer.toString(menu.pos.getY()))
                .property("速率", String.format(Locale.ROOT, "%.1f IF/t", menu.getGenerationRate()))
                .draw(graphics, this.leftPos, this.topPos);
    }
}
