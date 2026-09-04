package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.world.menu.PhaseGenMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class PhaseGenGui extends AcademyBaseUI<PhaseGenMenu> {

    private static final ResourceLocation UI_PHASE_GEN = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_phasegen.png");

    public PhaseGenGui(PhaseGenMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.WIFI);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics p_97808_, int p_97809_, int p_97810_) {
        // 由 renderBackground 处理
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderStandardMachinePanel(graphics, UI_PHASE_GEN);

        // 右侧能量信息面板
        renderEnergyInfoPanel(graphics);
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(
                InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy()),
                // GuiPhaseGen 1.0.7 used this distinct magenta IF bar for
                // phase liquid; the solid rectangle formerly drawn over the
                // machine diagram was never present in the original screen.
                new InfoArea.HistElement("IF", 0xFFB983FB,
                        (double) menu.getFluidAmount() / menu.getTankSize(),
                        menu.getFluidAmount() + " mB"));
        info.draw(graphics, this.leftPos, this.topPos);
    }
}
