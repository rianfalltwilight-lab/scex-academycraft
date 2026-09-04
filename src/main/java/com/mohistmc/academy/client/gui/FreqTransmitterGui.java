package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.network.FreqTransmitterActionPacket;
import com.mohistmc.academy.network.FreqTransmitterStatePacket;
import com.mohistmc.academy.network.NetworkInputLimits;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Frequency transmitter front-end.
 *
 * <p>The screen only opens a server session and accepts a password. Source and
 * target selection uses real world right-clicks after this screen closes,
 * matching the non-foreground 1.0.7 app without trusting client coordinates.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FreqTransmitterGui extends Screen {
    private static final int GUI_WIDTH = 260;
    private static final int GUI_HEIGHT = 170;
    private static final int COLOR_BG = 0xE0080818;
    private static final int COLOR_TITLE = 0xFF00BCD4;
    private static final int COLOR_TEXT = 0xFFCCCCCC;
    private static final int COLOR_DIM = 0xFF888899;
    private static final int COLOR_ACCENT = 0xFF004D5A;
    private static final UUID NO_NONCE = new UUID(0, 0);

    private static UUID activeNonce = NO_NONCE;
    private static int targetingState = FreqTransmitterStatePacket.CLOSED;
    private static String targetingMessage = "";
    private static final Set<UUID> LOCALLY_CANCELED = new HashSet<>();

    private final boolean opening;
    private final UUID nonce;
    private final String sourceLabel;
    private final String statusMessage;
    private final StringBuilder password = new StringBuilder();
    private boolean requestSent;
    private boolean serverTransition;
    private int guiLeft;
    private int guiTop;

    /** Opened from Data Terminal: request a fresh server session. */
    public FreqTransmitterGui() {
        this(true, UUID.randomUUID(), "", "正在建立服务器会话……");
    }

    private FreqTransmitterGui(boolean opening, UUID nonce, String sourceLabel, String statusMessage) {
        super(Component.translatable("item.academy.app_freq_transmitter"));
        this.opening = opening;
        this.nonce = nonce == null ? NO_NONCE : nonce;
        this.sourceLabel = sourceLabel == null ? "" : sourceLabel;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;
        if (opening && !requestSent) {
            requestSent = true;
            PacketDistributor.sendToServer(FreqTransmitterActionPacket.open(nonce));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COLOR_BG);
        drawBorder(graphics, guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, COLOR_ACCENT);
        graphics.drawString(font, "§l频率变送器", guiLeft + 10, guiTop + 10, COLOR_TITLE, false);

        int y = guiTop + 34;
        if (opening) {
            graphics.drawString(font, "正在由服务器创建一次性选择会话", guiLeft + 12, y, COLOR_TEXT, false);
            graphics.drawString(font, "会话建立后界面会自动关闭", guiLeft + 12, y + 14, COLOR_DIM, false);
            graphics.drawString(font, "随后右击矩阵或节点作为源设备", guiLeft + 12, y + 28, COLOR_DIM, false);
        } else {
            graphics.drawString(font, sourceLabel.isEmpty() ? "已选择源设备" : sourceLabel,
                    guiLeft + 12, y, COLOR_TEXT, false);
            graphics.drawString(font, statusMessage, guiLeft + 12, y + 14, COLOR_DIM, false);
            graphics.drawString(font, "密码", guiLeft + 12, y + 40, COLOR_TEXT, false);
            int inputX = guiLeft + 52;
            int inputY = y + 34;
            graphics.fill(inputX, inputY, guiLeft + GUI_WIDTH - 12, inputY + 18, 0xCC101828);
            drawBorder(graphics, inputX, inputY, GUI_WIDTH - 76, 18, COLOR_ACCENT);
            int ticks = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
            String masked = "*".repeat(password.length()) + ((ticks / 10) % 2 == 0 ? "_" : "");
            graphics.drawString(font, masked, inputX + 4, inputY + 5, 0xFFFFFFFF, false);

            int authorizeX = guiLeft + 52;
            int authorizeY = guiTop + 116;
            drawButton(graphics, authorizeX, authorizeY, 96, 20, "验证并选择目标",
                    inside(mouseX, mouseY, authorizeX, authorizeY, 96, 20));
            int cancelX = guiLeft + 154;
            drawButton(graphics, cancelX, authorizeY, 54, 20, "取消",
                    inside(mouseX, mouseY, cancelX, authorizeY, 54, 20));
        }
        graphics.drawString(font, "服务器校验：密码 / 距离 / 维度 / 权限",
                guiLeft + 12, guiTop + GUI_HEIGHT - 20, COLOR_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!opening && button == 0) {
            int y = guiTop + 116;
            if (inside(mouseX, mouseY, guiLeft + 52, y, 96, 20)) {
                submitPassword();
                return true;
            }
            if (inside(mouseX, mouseY, guiLeft + 154, y, 54, 20)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!opening) {
            if (keyCode == 259 && !password.isEmpty()) {
                password.deleteCharAt(password.length() - 1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                submitPassword();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!opening && !Character.isISOControl(codePoint)
                && password.length() < NetworkInputLimits.PASSWORD) {
            password.append(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void submitPassword() {
        if (nonce.equals(NO_NONCE)) return;
        PacketDistributor.sendToServer(FreqTransmitterActionPacket.authorize(nonce, password.toString()));
        password.setLength(0);
    }

    @Override
    public void removed() {
        super.removed();
        if (!serverTransition && !nonce.equals(NO_NONCE)) {
            LOCALLY_CANCELED.add(nonce);
            PacketDistributor.sendToServer(FreqTransmitterActionPacket.cancel(nonce));
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Client endpoint for the authoritative S2C session state. */
    public static void acceptServerState(FreqTransmitterStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        if (LOCALLY_CANCELED.contains(packet.nonce())) {
            if (packet.state() == FreqTransmitterStatePacket.CLOSED) {
                LOCALLY_CANCELED.remove(packet.nonce());
            } else {
                // OPEN and CANCEL share an ordered connection, but repeating
                // cancel here also makes this safe across delayed S2C delivery.
                PacketDistributor.sendToServer(FreqTransmitterActionPacket.cancel(packet.nonce()));
            }
            return;
        }
        if (current instanceof FreqTransmitterGui transmitter) {
            transmitter.serverTransition = true;
        }

        boolean ownsCurrentState = packet.nonce().equals(activeNonce)
                || current instanceof FreqTransmitterGui;
        if (packet.state() == FreqTransmitterStatePacket.CLOSED && !ownsCurrentState) {
            return;
        }

        activeNonce = packet.nonce();
        targetingState = packet.state();
        targetingMessage = packet.message();
        if (packet.state() == FreqTransmitterStatePacket.PASSWORD_REQUIRED) {
            minecraft.setScreen(new FreqTransmitterGui(false, packet.nonce(),
                    packet.sourceLabel(), packet.message()));
            return;
        }
        if (packet.state() == FreqTransmitterStatePacket.SELECT_SOURCE
                || packet.state() == FreqTransmitterStatePacket.SELECT_TARGET) {
            minecraft.setScreen(null);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(
                        "§7[频率变送器] §b" + packet.message()), true);
            }
            return;
        }

        activeNonce = NO_NONCE;
        targetingState = FreqTransmitterStatePacket.CLOSED;
        if (minecraft.screen instanceof FreqTransmitterGui) minecraft.setScreen(null);
        if (minecraft.player != null && !packet.message().isEmpty()) {
            minecraft.player.displayClientMessage(Component.literal(
                    "§7[频率变送器] §e" + packet.message()), true);
        }
    }

    public static boolean isTargetingWorldBlock() {
        return !activeNonce.equals(NO_NONCE)
                && (targetingState == FreqTransmitterStatePacket.SELECT_SOURCE
                || targetingState == FreqTransmitterStatePacket.SELECT_TARGET);
    }

    public static String getTargetingMessage() { return targetingMessage; }

    public static void cancelActiveSession() {
        if (!activeNonce.equals(NO_NONCE)) {
            PacketDistributor.sendToServer(FreqTransmitterActionPacket.cancel(activeNonce));
        }
        resetClientSession();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(
                    "§7[频率变送器] §e已取消选择"), true);
        }
    }

    public static void resetClientSession() {
        activeNonce = NO_NONCE;
        targetingState = FreqTransmitterStatePacket.CLOSED;
        targetingMessage = "";
        LOCALLY_CANCELED.clear();
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h,
                            String text, boolean hovered) {
        int color = hovered ? 0xFF3498DB : 0xFF2775AE;
        graphics.fill(x, y, x + w, y + h, color);
        graphics.drawCenteredString(font, text, x + w / 2, y + 6, 0xFFFFFFFF);
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
