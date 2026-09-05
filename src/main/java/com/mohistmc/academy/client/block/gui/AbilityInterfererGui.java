package com.mohistmc.academy.client.block.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.network.AbilityInterfererConfigPacket;
import com.mohistmc.academy.network.AbilityInterfererStatePacket;
import com.mohistmc.academy.skill.AbilityInterferenceRules;
import com.mohistmc.academy.utils.RenderUtils;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.menu.AbilityInterfererMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Interactive 1.12.2-style Ability Interferer page with server-owned configuration. */
@OnlyIn(Dist.CLIENT)
public final class AbilityInterfererGui extends AcademyBaseUI<AbilityInterfererMenu> {
    private static final ResourceLocation PARENT = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/parent/parent_background.png");
    private static final ResourceLocation UI = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/ui/ui_interfere.png");
    private static final int ROWS = 4;

    private final List<Button> removeButtons = new ArrayList<>();
    private List<AbilityInterfererStatePacket.Entry> whitelist = List.of();
    private EditBox addName;
    private Button toggle;
    private Button rangeDown;
    private Button rangeUp;
    private Button add;
    private Button previous;
    private Button next;
    private boolean canManage;
    private boolean initialStateRequested;
    private int page;
    private int stateRange = AbilityInterferenceRules.MIN_RANGE;
    private int stateEnergy;
    private boolean stateEnabled;

    public AbilityInterfererGui(AbilityInterfererMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WirelessState.WIFI);
        setRenderWireless(true);
    }

    @Override
    protected boolean hasJeiSideInfoArea() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        removeButtons.clear();
        initialStateRequested = false;
        stateRange = menu.getRange();
        stateEnergy = menu.getEnergy();
        stateEnabled = menu.isEnabled();

        toggle = addRenderableWidget(Button.builder(toggleLabel(), ignored -> send(
                        AbilityInterfererConfigPacket.TOGGLE, 0, ""))
                .bounds(leftPos + 50, topPos + 22, 48, 16).build());
        rangeDown = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> send(
                        AbilityInterfererConfigPacket.SET_RANGE,
                        AbilityInterferenceRules.stepRange(stateRange, -1), ""))
                .bounds(leftPos + 50, topPos + 42, 18, 16).build());
        rangeUp = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> send(
                        AbilityInterfererConfigPacket.SET_RANGE,
                        AbilityInterferenceRules.stepRange(stateRange, 1), ""))
                .bounds(leftPos + 104, topPos + 42, 18, 16).build());

        addName = new EditBox(font, leftPos + 12, topPos + 68, 106, 16,
                Component.translatable("gui.academy.interferer.player_name"));
        addName.setMaxLength(AbilityInterfererBlockEntity.MAX_PLAYER_NAME);
        addRenderableWidget(addName);
        add = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
                    String name = addName.getValue().strip();
                    if (!name.isEmpty() && menu.actionSessionReady()) {
                        send(AbilityInterfererConfigPacket.ADD_WHITELIST, 0, name);
                        addName.setValue("");
                    }
                }).bounds(leftPos + 121, topPos + 68, 20, 16).build());
        previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                    if (page > 0) page--;
                }).bounds(leftPos + 143, topPos + 68, 14, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                    if ((page + 1) * ROWS < whitelist.size()) page++;
                }).bounds(leftPos + 159, topPos + 68, 14, 16).build());

        for (int row = 0; row < ROWS; row++) {
            final int selectedRow = row;
            removeButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
                        int index = page * ROWS + selectedRow;
                        if (index >= 0 && index < whitelist.size()) {
                            send(AbilityInterfererConfigPacket.REMOVE_WHITELIST, 0,
                                    whitelist.get(index).id().toString());
                        }
                    }).bounds(leftPos + 146, topPos + 88 + row * 16, 18, 14).build()));
        }
        requestInitialStateWhenReady();
        updateWidgets();
    }

    private Component toggleLabel() {
        return Component.translatable(stateEnabled
                ? "gui.academy.interferer.disable" : "gui.academy.interferer.enable");
    }

    private void send(int action, int value, String target) {
        if (menu.pos != null && menu.actionSessionReady()) {
            PacketDistributor.sendToServer(new AbilityInterfererConfigPacket(menu.nextActionToken(),
                    menu.pos, action, value, target));
        }
    }

    private void requestInitialStateWhenReady() {
        if (!initialStateRequested && menu.pos != null && menu.actionSessionReady()) {
            send(AbilityInterfererConfigPacket.REQUEST, 0, "");
            initialStateRequested = true;
        }
    }

    public static void acceptServerState(AbilityInterfererStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbilityInterfererGui screen)
                || screen.menu.pos == null || !screen.menu.pos.equals(packet.pos())
                || screen.menu.containerId != packet.containerId()) return;
        screen.stateEnergy = Math.clamp(packet.energy(), 0, AbilityInterferenceRules.MAX_ENERGY);
        screen.stateRange = AbilityInterferenceRules.clampRange(packet.range());
        screen.stateEnabled = packet.enabled();
        screen.canManage = packet.owner();
        screen.whitelist = packet.whitelist() == null ? List.of() : List.copyOf(packet.whitelist());
        screen.page = Math.min(screen.page,
                Math.max(0, (screen.whitelist.size() - 1) / ROWS));
        screen.updateWidgets();
    }

    private void updateWidgets() {
        if (toggle == null) return;
        boolean visible = !panelActive;
        boolean editable = canManage && menu.actionSessionReady();
        toggle.visible = visible;
        rangeDown.visible = visible;
        rangeUp.visible = visible;
        addName.visible = visible;
        add.visible = visible;
        previous.visible = visible;
        next.visible = visible;
        toggle.active = editable;
        rangeDown.active = editable && stateRange > AbilityInterferenceRules.MIN_RANGE;
        rangeUp.active = editable && stateRange < AbilityInterferenceRules.MAX_RANGE;
        addName.setEditable(editable);
        add.active = editable && whitelist.size() < AbilityInterfererBlockEntity.MAX_WHITELIST;
        previous.active = editable && page > 0;
        next.active = editable && (page + 1) * ROWS < whitelist.size();
        toggle.setMessage(toggleLabel());
        for (int row = 0; row < removeButtons.size(); row++) {
            Button button = removeButtons.get(row);
            int index = page * ROWS + row;
            button.visible = visible && index < whitelist.size();
            // The owner is sorted first and is permanently whitelisted.
            button.active = editable && index > 0 && index < whitelist.size();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        requestInitialStateWhenReady();
        updateWidgets();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, leftPos, topPos, graphics, PARENT);
        RenderUtils.render(GUI_WIDTH, GUI_HEIGHT, leftPos, topPos, graphics, UI);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (panelActive) return;
        graphics.drawString(font, Component.translatable("gui.academy.interferer.switch"),
                10, 26, 0xE8F6FF, false);
        graphics.drawString(font, Component.translatable("gui.academy.interferer.range"),
                10, 46, 0xE8F6FF, false);
        String rangeText = Integer.toString(stateRange);
        graphics.drawString(font, rangeText, 86 - font.width(rangeText) / 2,
                46, 0xFFFFFF, false);
        graphics.drawString(font, stateEnergy + "/" + AbilityInterferenceRules.MAX_ENERGY + " IF",
                101, 10, 0xBFEAFF, false);
        graphics.drawString(font, Component.translatable("gui.academy.interferer.whitelist"),
                12, 59, 0xE8F6FF, false);
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            if (index >= whitelist.size()) break;
            String name = whitelist.get(index).name();
            if (index == 0) name += " *";
            graphics.drawString(font, name, 16, 91 + row * 16, 0xFFFFFF, false);
        }
        if (!canManage) {
            graphics.drawString(font, Component.translatable("gui.academy.interferer.read_only"),
                    12, 153, 0xFF8080, false);
        }
    }
}
