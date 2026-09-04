package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.client.gui.RegularMachineLayout;
import com.mohistmc.academy.network.ConsoleCommandPacket;
import com.mohistmc.academy.world.menu.DevNormalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Normal developer machine page; both developer tiers share the same entry workflow. */
public class DevNormalGui extends AcademyBaseUI<DevNormalMenu> {
    private boolean compactFallback;
    private boolean compactLayout;

    public DevNormalGui(DevNormalMenu menu, Inventory inv, Component title) {
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

    /** Exposes only the production panel state to the isolated real-client gate. */
    public boolean isNetworkPanelOpenForVisualGate() {
        return Boolean.getBoolean("academy.machineVisualGate") && panelActive;
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        InfoArea info = new InfoArea();
        info.histogram(InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy()));
        info.property("类型", "普通");
        info.draw(graphics, leftPos, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // DeveloperMachinePanel supplies its own title and inventory frame.
        // Suppress AbstractContainerScreen's duplicate English labels.
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (compactFallback) {
            graphics.fill(0, 0, width, height, 0xDD101018);
            graphics.drawCenteredString(font, "窗口过小，请放大窗口或降低 GUI 缩放",
                    width / 2, Math.max(4, height / 2 - 4), 0xFFFFFFFF);
            return;
        }
        if (compactLayout) renderCompactDeveloperPanel(graphics, false);
        else renderDeveloperMachinePanel(graphics);
        graphics.drawString(font, Component.translatable("block.academy.dev_normal"), leftPos + 8, topPos + 7, 0xFF28C4E8);
        boolean hasAbility = Minecraft.getInstance().player != null && Minecraft.getInstance().player
                .getData(com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY).hasAbility();
        graphics.drawString(font, hasAbility ? "使用左侧无线按钮连接附近节点"
                : "携带因子指定类别；否则随机", leftPos + 8, topPos + 27, 0xFFAAAAAA);
        drawActionButton(graphics, leftPos + 94, topPos + 60, 72, 16,
                "技能树", mouseX, mouseY);
        renderEnergyInfoPanel(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (compactFallback) return true;
        if (panelActive && button == 0 && isHoveringButton(
                getSidebarLeft(), getSidebarTop(), 18, 18, mouseX, mouseY)) {
            if (menu.pos != null) {
                PacketDistributor.sendToServer(new ConsoleCommandPacket(menu.pos, "learn"));
            }
            return true;
        }
        if (!panelActive && button == 0
                && inside(mouseX, mouseY, leftPos + 94, topPos + 60, 72, 16)) {
            if (menu.pos != null) {
                PacketDistributor.sendToServer(new ConsoleCommandPacket(menu.pos, "learn"));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int w, int h,
                                  String label, double mouseX, double mouseY) {
        int color = inside(mouseX, mouseY, x, y, w, h) ? 0xFF3498DB : 0xFF2980B9;
        graphics.fill(x, y, x + w, y + h, color);
        graphics.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected int getSidebarLeft() {
        if (compactFallback) return -1000;
        return compactLayout ? super.getSidebarLeft() : RegularMachineLayout.developerSidebarLeft(width);
    }
}
