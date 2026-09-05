package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.network.InitMatrixPacket;
import com.mohistmc.academy.network.MatrixConfigPacket;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.world.menu.MatrixMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/** Wireless matrix inventory plus the 1.0.7-style right information area. */
@OnlyIn(Dist.CLIENT)
public class MatrixGui extends AcademyBaseUI<MatrixMenu> {
    private static final ResourceLocation UI_MATRIX = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/ui/ui_matrix.png");
    private static final int PANEL_X = 183;
    private static final int PANEL_Y = 5;
    private static final int PANEL_W = 100;
    private static final int PANEL_H = 140;
    private static final int BUTTON_X = 6;
    private static final int BUTTON_W = PANEL_W - 12;
    private static final int BUTTON_H = 16;

    private boolean editingSsid;
    private boolean editingPassword;
    private final StringBuilder ssidInput = new StringBuilder();
    private final StringBuilder passwordInput = new StringBuilder();
    private boolean inputStateInitialized;

    public MatrixGui(MatrixMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, WirelessState.DEFAULT);
        // The inventory and the legacy 100px InfoArea are one 283px logical
        // screen. Reserving that width makes AbstractContainerScreen centre the
        // whole composition, so INIT/link controls remain reachable at common
        // GUI-scale widths instead of being clipped beyond the right edge.
        this.imageWidth = PANEL_X + PANEL_W;
        // DEFAULT has no wireless sub-page. Leaving the common sidebar enabled
        // opened a blank page and disabled every slot.
        setRenderWireless(false);
    }

    @Override
    protected void init() {
        super.init();
        // Include the always-visible inventory selector when centring the
        // 295px matrix composition. At 320 logical pixels this yields
        // sidebar x=2 and panel right=317, with neither edge clipped.
        this.leftPos = com.mohistmc.academy.client.gui.RegularMachineLayout
                .contentLeftWithSidebar(this.width, this.imageWidth);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderStandardMachinePanel(graphics, UI_MATRIX);
        initializeInputState();

        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        InfoArea.drawPanel(graphics, px, py, PANEL_W, PANEL_H);
        graphics.drawString(font, menu.isOperational() ? "§a已初始化"
                        : menu.isInitialized() ? "§e网络暂停（缺少组件）" : "§e初始化矩阵",
                px + 6, py + 7, 0xFFFFFFFF, false);

        String ssid = editingSsid ? ssidInput + "▌"
                : menu.isInitialized() ? ssidInput.toString()
                : ssidInput.isEmpty() ? "点击设置" : ssidInput.toString();
        graphics.drawString(font, trim("SSID: " + ssid), px + 6, py + 23, 0xFFCCCCCC, false);
        String password = editingPassword ? "*".repeat(passwordInput.length()) + "▌"
                : menu.hasPasswordConfigured() ? "点击修改" : "无";
        graphics.drawString(font, trim("密码: " + password), px + 6, py + 36, 0xFFCCCCCC, false);

        if (menu.isInitialized()) {
            graphics.drawString(font, "容量: " + menu.getCapacity(), px + 6, py + 53, 0xFFAAAAAA, false);
            graphics.drawString(font, "带宽: " + menu.getBandwidth() + " IF/t", px + 6, py + 66, 0xFFAAAAAA, false);
            graphics.drawString(font, "范围: " + menu.getRange() + " 格", px + 6, py + 79, 0xFFAAAAAA, false);
            graphics.drawString(font, trim("所有者: " + menu.getOwnerLabel()),
                    px + 6, py + 92, 0xFF888888, false);
            if (!menu.isOperational()) {
                graphics.drawString(font, trim("§c补齐核心与三块约束板后自动恢复"), px + 6, py + 106,
                        0xFFFFFFFF, false);
            }
            graphics.drawString(font, trim(menu.canEdit()
                            ? "点字段，回车保存" : "在节点无线页选择"),
                    px + 6, py + 121, 0xFF8FBBD0, false);
        } else {
            graphics.drawString(font, menu.hasInitializationMaterials()
                            ? "§a核心与约束板已就绪" : "§c需要核心与三块约束板",
                    px + 6, py + 58, 0xFFFFFFFF, false);
            drawButton(graphics, px + BUTTON_X, py + 78, Component.literal("INIT"),
                    mouseX, mouseY, menu.hasInitializationMaterials() && menu.actionSessionReady() && menu.canEdit());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        initializeInputState();
        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (!menu.isInitialized()) {
            if (inside(mouseX, mouseY, px + BUTTON_X, py + 78, BUTTON_W, BUTTON_H)
                    && menu.hasInitializationMaterials() && menu.actionSessionReady() && menu.pos != null && menu.canEdit()) {
                PacketDistributor.sendToServer(new InitMatrixPacket(menu.nextActionToken(), menu.pos,
                        ssidInput.isEmpty() ? "Unnamed" : ssidInput.toString(), passwordInput.toString()));
                return true;
            }
        }
        if (menu.canEdit() && inside(mouseX, mouseY, px + 4, py + 18, PANEL_W - 8, 14)) {
            editingSsid = true;
            editingPassword = false;
            return true;
        }
        if (menu.canEdit() && inside(mouseX, mouseY, px + 4, py + 32, PANEL_W - 8, 14)) {
            editingPassword = true;
            editingSsid = false;
            passwordInput.setLength(0);
            return true;
        }

        editingSsid = false;
        editingPassword = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editingSsid && !editingPassword) return super.keyPressed(keyCode, scanCode, modifiers);
        StringBuilder target = editingSsid ? ssidInput : passwordInput;
        if (keyCode == 259) {
            if (!target.isEmpty()) target.deleteCharAt(target.length() - 1);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (!menu.actionSessionReady()) return true;
            if (menu.isInitialized() && menu.pos != null) {
                PacketDistributor.sendToServer(new MatrixConfigPacket(menu.nextActionToken(), menu.pos,
                        editingSsid ? java.util.Optional.of(ssidInput.toString()) : java.util.Optional.empty(),
                        editingPassword ? java.util.Optional.of(passwordInput.toString()) : java.util.Optional.empty()));
                passwordInput.setLength(0);
            }
            editingSsid = false;
            editingPassword = false;
            return true;
        }
        if (keyCode == 256) {
            editingSsid = false;
            editingPassword = false;
            passwordInput.setLength(0);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!editingSsid && !editingPassword) return super.charTyped(codePoint, modifiers);
        StringBuilder target = editingSsid ? ssidInput : passwordInput;
        int limit = editingSsid ? NetworkInputLimits.SSID : NetworkInputLimits.PASSWORD;
        if (!Character.isISOControl(codePoint) && target.length() < limit) target.append(codePoint);
        return true;
    }

    private void initializeInputState() {
        if (inputStateInitialized) return;
        ssidInput.append(menu.getInitialSsid());
        inputStateInitialized = true;
    }

    /** Exercise the real INIT hit box in the isolated client integration gate. */
    public boolean clickInitForVisualGate() {
        if (!Boolean.getBoolean("academy.machineVisualGate")) return false;
        initializeInputState();
        int px = this.leftPos + PANEL_X;
        int py = this.topPos + PANEL_Y;
        return mouseClicked(px + BUTTON_X + BUTTON_W / 2.0, py + 86.0, 0);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, Component label,
                            double mouseX, double mouseY, boolean enabled) {
        int color = !enabled ? 0xFF555566
                : inside(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H) ? 0xFF3498DB : 0xFF2775AE;
        graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, color);
        graphics.drawCenteredString(font, label, x + BUTTON_W / 2, y + 4,
                enabled ? 0xFFFFFFFF : 0xFF999999);
    }

    private String trim(String value) {
        return font.plainSubstrByWidth(value, PANEL_W - 12);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
