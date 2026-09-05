package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.InfoArea;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mohistmc.academy.network.NodeConfigPacket;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class BaseNodeGui<T extends BaseNodeMenu> extends AcademyBaseUI<T> {

    private static final ResourceLocation UI_NODE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/ui/ui_node.png");
    private static final ResourceLocation EFFECT_NODE = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/effect/effect_node.png");
    private boolean connected = false;
    private final StringBuilder nodeNameInput = new StringBuilder();
    private final StringBuilder nodePasswordInput = new StringBuilder();
    private boolean nodeInputInitialized;
    private boolean nodeNameEdited;
    private boolean passwordEdited;
    private EditFocus editFocus = EditFocus.NONE;
    private int infoPanelX = Integer.MIN_VALUE;
    private float nameRowY;
    private float passwordRowY;

    private enum EditFocus { NONE, NAME, PASSWORD }

    public BaseNodeGui(T t, Inventory inv, Component p_97743_) {
        super(t, inv, p_97743_, WirelessState.NODE);
    }


    private long lastAnimTime = 0;
    private int animIndex = 8;
    private boolean animationStateInitialized;
    private boolean previousConnected;

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不显示物品栏标题
    }

    @Override
    public void renderBackground(GuiGraphics stack,  int mouseX, int mouseY, float p_97788_) {
        renderStandardMachinePanel(stack, UI_NODE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // A node is "connected" when it belongs to a matrix network.  The
        // nearby-node list is only meaningful for wireless users, so deriving
        // this visual from activeNode made every node look disconnected even
        // when its authoritative block-entity state said otherwise.
        this.connected = menu.isConnected();
        long now = System.currentTimeMillis();
        if (!animationStateInitialized || this.connected != previousConnected) {
            animIndex = this.connected ? 0 : 8;
            previousConnected = this.connected;
            animationStateInitialized = true;
            lastAnimTime = now;
        }
        long frameTime = this.connected ? 800L : 3000L;
        if (now - lastAnimTime >= frameTime) {
            animIndex++;
            if (this.connected && animIndex > 7) animIndex = 0;
            if (!this.connected && (animIndex < 8 || animIndex > 9)) animIndex = 8;
            lastAnimTime = now;
        }
        // effect_node is a 186x750 vertical strip (ten 186x75 frames).
        // Sample the frame on the V axis; using the U axis made the texture
        // appear shifted/blank after the 1.21 GUI blit migration.
        RenderUtils.render(186 / 2, 75 / 2,
                186, 75,
                this.leftPos + (GUI_WIDTH - 186 / 2) / 2,
                this.topPos + (GUI_HEIGHT - 75 / 2) / 2 - (75 / 2) - 2,
                stack, EFFECT_NODE,
                0, 75 * animIndex, 186, 750);
        RenderSystem.disableBlend();

        // 右侧能量信息面板
        renderEnergyInfoPanel(stack);
    }

    @Override
    protected void renderEnergyInfoPanel(GuiGraphics graphics) {
        initializeNodeInput();
        String name = nodeNameInput + (editFocus == EditFocus.NAME ? "▌" : "");
        String password = passwordEdited ? "*".repeat(nodePasswordInput.length())
                + (editFocus == EditFocus.PASSWORD ? "▌" : "") : "点击修改";
        InfoArea info = new InfoArea()
                .histogram(
                        InfoArea.histEnergy(menu.getNodeEnergy(), menu.getNodeMaxEnergy()),
                        InfoArea.histCapacity(menu.getNodeLoad(), menu.getNodeCapacity()))
                .seplineInfo()
                .property("带宽", menu.getNodeBandwidth() + " IF/t")
                .property("范围", menu.getNodeRange() + " 格")
                .property("所有者", trimInfo(menu.getOwnerLabel()));
        info.property("节点名", trimInfo(name));
        nameRowY = info.lastElementY();
        info.property("密码", menu.canEditNode() ? password : "仅所有者可改");
        passwordRowY = info.lastElementY();
        infoPanelX = InfoArea.resolvePanelX(this.leftPos);
        info.draw(graphics, this.leftPos, this.topPos);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        initializeNodeInput();
        if (button == 0 && menu.canEditNode() && infoPanelX != Integer.MIN_VALUE) {
            int panelY = this.topPos + InfoArea.Y;
            if (inside(mouseX, mouseY, infoPanelX + 42, panelY + Math.round(nameRowY),
                    InfoArea.W - 44, 9)) {
                editFocus = EditFocus.NAME;
                // The legacy property editor selected its current value.  A
                // fresh node should therefore let the user type a real name
                // directly instead of accidentally producing UnnamedHome.
                if (!nodeNameEdited && BaseNodeBlockEntity.DEFAULT_NODE_NAME.contentEquals(nodeNameInput)) {
                    nodeNameInput.setLength(0);
                }
                return true;
            }
            if (inside(mouseX, mouseY, infoPanelX + 42, panelY + Math.round(passwordRowY),
                    InfoArea.W - 44, 9)) {
                editFocus = EditFocus.PASSWORD;
                if (!passwordEdited) {
                    nodePasswordInput.setLength(0);
                    passwordEdited = true;
                }
                return true;
            }
        }
        editFocus = EditFocus.NONE;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editFocus == EditFocus.NONE) return super.keyPressed(keyCode, scanCode, modifiers);
        StringBuilder target = editFocus == EditFocus.NAME ? nodeNameInput : nodePasswordInput;
        if (keyCode == 259) {
            if (editFocus == EditFocus.NAME) nodeNameEdited = true;
            if (!target.isEmpty()) target.deleteCharAt(target.length() - 1);
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            // Preserve the edit while the per-open session is still synchronizing.
            if (submitNodeConfig()) editFocus = EditFocus.NONE;
            return true;
        }
        if (keyCode == 256) {
            editFocus = EditFocus.NONE;
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editFocus == EditFocus.NONE) return super.charTyped(codePoint, modifiers);
        if (Character.isISOControl(codePoint)) return true;
        StringBuilder target = editFocus == EditFocus.NAME ? nodeNameInput : nodePasswordInput;
        if (editFocus == EditFocus.NAME) nodeNameEdited = true;
        int max = editFocus == EditFocus.NAME
                ? NetworkInputLimits.NODE_NAME : NetworkInputLimits.PASSWORD;
        if (target.length() < max) target.append(codePoint);
        return true;
    }

    private void initializeNodeInput() {
        if (!nodeInputInitialized) {
            nodeNameInput.append(menu.getInitialNodeName());
            nodeInputInitialized = true;
        }
        // Other viewers can rename this node while this screen stays open.
        // Keep an in-progress or unfocused unsent draft, including before the
        // action nonce is ready; only an untouched display follows the mirror.
        if (!nodeNameEdited && editFocus != EditFocus.NAME) {
            String current = menu.getCurrentNodeName();
            if (!current.contentEquals(nodeNameInput)) {
                nodeNameInput.setLength(0);
                nodeNameInput.append(current);
            }
        }
    }

    private boolean submitNodeConfig() {
        if (!menu.actionSessionReady() || !menu.canEditNode() || menu.pos == null) return false;
        boolean nameEdit = editFocus == EditFocus.NAME;
        if (nameEdit && !NetworkInputLimits.validRequired(nodeNameInput.toString(), NetworkInputLimits.NODE_NAME)) {
            return false;
        }
        // Commit only the property the viewer confirms. A password edit from
        // another open menu must not overwrite a newer name with its opening snapshot.
        PacketDistributor.sendToServer(new NodeConfigPacket(menu.nextActionToken(), menu.pos,
                nameEdit ? java.util.Optional.of(nodeNameInput.toString()) : java.util.Optional.empty(),
                nameEdit ? java.util.Optional.empty() : java.util.Optional.of(nodePasswordInput.toString())));
        if (nameEdit) {
            nodeNameEdited = false;
        } else {
            passwordEdited = false;
            nodePasswordInput.setLength(0);
        }
        return true;
    }
    /** Real-client gate hook: edit and submit through the production input handlers. */
    public final boolean renameNodeForVisualGate(String name) {
        return draftNodeNameForVisualGate(name) && keyPressed(257, 0, 0);
    }

    /** Leaves an actual unsent name draft so concurrent UI gates can test focus changes. */
    public final boolean draftNodeNameForVisualGate(String name) {
        if (!visualGateEnabled() || !menu.actionSessionReady() || name == null
                || name.isBlank() || name.length() > NetworkInputLimits.NODE_NAME
                || !menu.canEditNode() || infoPanelX == Integer.MIN_VALUE) return false;
        if (!mouseClicked(infoPanelX + 50.0,
                this.topPos + InfoArea.Y + Math.round(nameRowY) + 4.0, 0)
                || editFocus != EditFocus.NAME) return false;
        while (!nodeNameInput.isEmpty()) keyPressed(259, 0, 0);
        for (int i = 0; i < name.length(); i++) charTyped(name.charAt(i), 0);
        return true;
    }

    /** Uses the same hit box, editing and Enter path as a player in the two-client gate. */
    public final boolean setPasswordForVisualGate(String password) {
        if (!visualGateEnabled() || !menu.actionSessionReady() || password == null
                || password.length() > NetworkInputLimits.PASSWORD || !menu.canEditNode()
                || infoPanelX == Integer.MIN_VALUE) return false;
        if (!mouseClicked(infoPanelX + 50.0,
                this.topPos + InfoArea.Y + Math.round(passwordRowY) + 4.0, 0)
                || editFocus != EditFocus.PASSWORD) return false;
        while (!nodePasswordInput.isEmpty()) keyPressed(259, 0, 0);
        for (int i = 0; i < password.length(); i++) charTyped(password.charAt(i), 0);
        return keyPressed(257, 0, 0);
    }

    /** Full untrimmed value last rendered by the production property editor. */
    public final String displayedNodeNameForVisualGate() {
        return visualGateEnabled() ? nodeNameInput.toString() : null;
    }

    private static boolean visualGateEnabled() {
        return Boolean.getBoolean("academy.machineVisualGate")
                || Boolean.getBoolean("academy.concurrentMenuGate");
    }
    private String trimInfo(String value) {
        return this.font.plainSubstrByWidth(value == null ? "" : value, 58);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
