package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.menu.SolarGenMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class SolarGenGui extends AcademyBaseUI<SolarGenMenu> {

    // AcademyCraft 1.0.7 page_solar.xml intentionally composes the solar page
    // with ui_windbase.png; ui_phasegen.png contains unrelated tank/slot art.
    private static final ResourceLocation UI_SOLAR_GEN = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_windbase.png");
    private static final ResourceLocation EFFECT_SOLAR = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/effect/effect_solar.png");

    // effect_solar.png 尺寸 104x210，垂直排列3个图标，每帧 104x70
    private static final int TEXTURE_WIDTH = 104;
    private static final int TEXTURE_HEIGHT = 210;
    private static final int FRAME_WIDTH = 104;
    private static final int FRAME_HEIGHT = 70;
    // page_solar.xml uses a 0.6 scale at x=56,y=23.
    private static final int DRAW_WIDTH = 62;
    private static final int DRAW_HEIGHT = 42;

    public SolarGenGui(SolarGenMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.WIFI);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics p_97808_, int p_97809_, int p_97810_) {
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderStandardMachinePanel(graphics, UI_SOLAR_GEN);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 根据天气和时间渲染状态图标
        renderWeatherIcon(graphics);

        RenderSystem.disableBlend();

        // 右侧能量信息面板
        renderEnergyInfoPanel(graphics);
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(InfoArea.histBuffer(menu.getEnergy(), menu.getMaxEnergy()));
        String rate = switch (menu.getStatus()) {
            case STRONG -> "3 IF/t";
            case WEAK -> "0.6 IF/t";
            case STOPPED -> "0 IF/t";
        };
        info.seplineInfo().property("产能", rate);
        info.draw(graphics, this.leftPos, this.topPos);
    }

    /**
     * 根据当前世界的天气和时间渲染对应的状态图标
     */
    private void renderWeatherIcon(GuiGraphics graphics) {
        if (this.menu.pos == null) return;
        var status = menu.getStatus();
        int iconIndex = status.ordinal();

        int guiLeft = this.leftPos;
        int guiTop = this.topPos;
        int x = guiLeft + 56;
        int y = guiTop + 23;

        int vOffset = iconIndex * FRAME_HEIGHT;
        graphics.blit(EFFECT_SOLAR, x, y, DRAW_WIDTH, DRAW_HEIGHT, 0, vOffset, FRAME_WIDTH, FRAME_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }
}
