package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.client.gui.RegularMachineLayout;
import com.mohistmc.academy.network.ConsoleCommandPacket;
import com.mohistmc.academy.world.menu.DevAdvancedMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/** Advanced developer's slotless wireless bridge. Normal use opens SkillTreeGui directly. */
@OnlyIn(Dist.CLIENT)
public class DevAdvancedGui extends AcademyBaseUI<DevAdvancedMenu> {
    private boolean compactFallback;
    private boolean compactLayout;

    public DevAdvancedGui(DevAdvancedMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.WIFI);
        setReserveSideInfoArea(false);
    }

    @Override protected void init() {
        super.init();
        updateLayout();
    }

    @Override public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        updateLayout();
    }

    private void updateLayout() {
        compactFallback = width < GUI_WIDTH || height < GUI_HEIGHT;
        compactLayout = width < RegularMachineLayout.DEVELOPER_COMPOSITION_WIDTH;
        if (compactLayout && !compactFallback) {
            leftPos = RegularMachineLayout.machineLeft(width, true);
        } else if (!compactFallback) {
            leftPos = RegularMachineLayout.developerMenuLeft(width);
        }
    }

    public void openNetworkPage(BlockPos expectedPos, int expectedContainerId) {
        if (menu.pos != null && menu.pos.equals(expectedPos) && menu.containerId == expectedContainerId) {
            openInitialWirelessPanel();
        }
    }

    @Override protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy()));
        info.property("类型", "高级");
        info.draw(graphics, leftPos, topPos);
    }

    @Override protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // The developer canvas owns all labels.
    }

    @Override public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (compactFallback) {
            graphics.fill(0, 0, width, height, 0xDD101018);
            graphics.drawCenteredString(font, "窗口过小，请放大窗口或降低 GUI 缩放",
                    width / 2, Math.max(4, height / 2 - 4), 0xFFFFFFFF);
            return;
        }
        if (compactLayout) renderCompactDeveloperPanel(graphics, false);
        else renderDeveloperMachinePanel(graphics);
        graphics.drawString(font, Component.translatable("block.academy.dev_advanced"),
                leftPos + 8, topPos + 7, 0xFF28C4E8);
        graphics.drawString(font, "材料取自主手和玩家背包（与 1.0.7 一致）",
                leftPos + 8, topPos + 27, 0xFFAAAAAA);
        drawActionButton(graphics, leftPos + 94, topPos + 60, 72, 16,
                "返回技能树", mouseX, mouseY);
        renderEnergyInfoPanel(graphics);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (compactFallback) return true;
        if (panelActive && button == 0 && isHoveringButton(
                getSidebarLeft(), getSidebarTop(), 18, 18, mouseX, mouseY)) {
            reopenSkillTree();
            return true;
        }
        if (!panelActive && button == 0 && isHoveringButton(
                leftPos + 94, topPos + 60, 72, 16, mouseX, mouseY)) {
            reopenSkillTree();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void reopenSkillTree() {
        if (menu.pos != null) PacketDistributor.sendToServer(new ConsoleCommandPacket(menu.pos, "learn"));
    }

    /** Route the isolated visual gate through the same button handler a player uses. */
    public boolean clickReturnToSkillTreeForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate")) return false;
        if (panelActive) {
            return mouseClicked(getSidebarLeft() + 9.0, getSidebarTop() + 9.0, 0);
        }
        return mouseClicked(leftPos + 130.0, topPos + 68.0, 0);
    }

    public boolean isNetworkPanelOpenForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") && panelActive;
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int w, int h,
                                  String label, double mouseX, double mouseY) {
        int color = isHoveringButton(x, y, w, h, mouseX, mouseY) ? 0xFF3498DB : 0xFF2980B9;
        graphics.fill(x, y, x + w, y + h, color);
        graphics.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    @Override protected int getSidebarLeft() {
        if (compactFallback) return -1000;
        return compactLayout ? super.getSidebarLeft() : RegularMachineLayout.developerSidebarLeft(width);
    }
}
