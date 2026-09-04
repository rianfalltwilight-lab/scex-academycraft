package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.LocationTeleportActionPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.PlayerAbilityData.TeleportLocation;
import com.mohistmc.academy.world.AcademySounds;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.0.7 location-teleport list rebuilt with the original two-panel information
 * model. The server still owns CRUD, resource debit and teleporting; this screen
 * only provides the old preflight feedback and sends bounded actions.
 */
public final class LocationTeleportGui extends AcademyScreen {
    private static final int GUI_W = 390;
    private static final int GUI_H = 230;
    private static final int TOP = 28;
    private static final int LIST_W = 252;
    private static final int ROW_H = 32;
    private static final int ROWS = 5;
    private static final ResourceLocation TELEPORT_ICON = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/icons/icon_location_on.png");
    private static final ResourceLocation REMOVE_ICON = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/icons/icon_clear.png");
    private static final ResourceLocation ADD_ICON = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/check.png");

    private static List<TeleportLocation> latest = List.of();
    private EditBox name;
    private int page;
    private int hoveredRow = -1;
    private boolean hoverTeleport;
    private boolean hoverRemove;
    private boolean hoverAdd;

    public LocationTeleportGui() {
        super(Component.translatable("item.academy.factor_teleporter.location_teleport"));
    }

    @Override
    protected void init() {
        super.init();
        centerGui(Math.min(GUI_W, width - 12), Math.min(GUI_H, height - 12));
        name = new EditBox(font, guiLeft + 10, guiTop + guiHeight - 24,
                Math.max(80, Math.min(196, guiWidth - 58)), 18,
                Component.translatable("ac.gui.loctele.add"));
        name.setMaxLength(16);
        name.setHint(Component.translatable("ac.gui.loctele.add"));
        addRenderableWidget(name);
        clampPage();
        PacketDistributor.sendToServer(new LocationTeleportActionPacket(
                LocationTeleportActionPacket.QUERY, -1, ""));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        pushZ(g);
        drawBackground(g, 0xB4353535);
        g.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + TOP, 0xD02E3438);
        g.fill(guiLeft, guiTop + TOP, guiLeft + guiWidth, guiTop + TOP + 1, 0xFF7A929C);
        int split = split();
        g.fill(split, guiTop + TOP + 1, split + 1, guiTop + guiHeight, 0x9E7A929C);
        drawBorder(g, guiLeft, guiTop, guiWidth, guiHeight, 0x9E7A929C);
        g.drawString(font, title, guiLeft + 10, guiTop + 9, 0xFFC1CFD5);

        hoveredRow = -1;
        hoverTeleport = hoverRemove = hoverAdd = false;
        int begin = page * ROWS;
        int end = Math.min(begin + ROWS, latest.size());
        for (int index = begin; index < end; index++) {
            int visible = index - begin;
            int y = guiTop + TOP + 5 + visible * ROW_H;
            TeleportLocation location = latest.get(index);
            boolean rowHover = isHovered(guiLeft + 4, y, split - guiLeft - 8, ROW_H - 2,
                    mouseX, mouseY);
            if (rowHover) hoveredRow = index;
            g.fill(guiLeft + 4, y, split - 4, y + ROW_H - 2,
                    rowHover ? 0x704C6670 : (visible % 2 == 0 ? 0x50373F43 : 0x4031373A));
            Cost cost = cost(location);
            int nameColor = cost.available ? 0xFFC1CFD5 : 0xFFA2A2A2;
            g.drawString(font, location.name(), guiLeft + 10, y + 5, nameColor);
            String coords = String.format(Locale.ROOT, "%.0f, %.0f, %.0f", location.x(), location.y(), location.z());
            g.drawString(font, coords, guiLeft + 10, y + 17, 0xFF82939A);

            int removeX = split - 23;
            int teleportX = removeX - 21;
            if (cost.available) {
                boolean hovered = isHovered(teleportX, y + 7, 16, 16, mouseX, mouseY);
                if (hovered && rowHover) hoverTeleport = true;
                if (hovered) g.fill(teleportX - 2, y + 5, teleportX + 18, y + 25, 0x6057B7CA);
                g.blit(TELEPORT_ICON, teleportX, y + 7, 16, 16, 0, 0, 48, 48, 48, 48);
            }
            boolean removeHovered = isHovered(removeX, y + 7, 16, 16, mouseX, mouseY);
            if (removeHovered && rowHover) hoverRemove = true;
            if (removeHovered) g.fill(removeX - 2, y + 5, removeX + 18, y + 25, 0x60B34B4B);
            g.blit(REMOVE_ICON, removeX, y + 7, 16, 16, 0, 0, 48, 48, 48, 48);
        }

        int addX = guiLeft + Math.max(40, Math.min(214, guiWidth - 34));
        hoverAdd = isHovered(addX, guiTop + guiHeight - 23, 18, 18, mouseX, mouseY);
        if (hoverAdd) g.fill(addX - 2, guiTop + guiHeight - 25, addX + 20,
                guiTop + guiHeight - 3, 0x6057B7CA);
        g.blit(ADD_ICON, addX, guiTop + guiHeight - 23, 18, 18, 0, 0, 48, 48, 48, 48);

        if (page > 0) g.drawString(font, "<", split - 45, guiTop + guiHeight - 19, 0xFFB5E3EC);
        if (end < latest.size()) g.drawString(font, ">", split - 32, guiTop + guiHeight - 19, 0xFFB5E3EC);
        renderInformation(g, split + 8, guiTop + TOP + 8);
        popZ(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderInformation(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("ac.gui.loctele.info"), x, y, 0xFFC1CFD5);
        if (hoveredRow < 0 || hoveredRow >= latest.size()) {
            g.drawWordWrap(font, Component.translatable("ac.gui.loctele.hint"), x, y + 18,
                    Math.max(80, guiLeft + guiWidth - x - 8), 0xFF82939A);
            return;
        }
        TeleportLocation location = latest.get(hoveredRow);
        Cost cost = cost(location);
        g.drawWordWrap(font, Component.literal(dimensionName(location.dimension())), x, y + 18,
                Math.max(80, guiLeft + guiWidth - x - 8), 0xFFB4C5CC);
        g.drawString(font, String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)",
                location.x(), location.y(), location.z()), x, y + 42, 0xFF82939A);
        g.drawString(font, String.format(Locale.ROOT, "%.0f CP", cost.cp), x, y + 56,
                cost.available ? 0xFF72D9B2 : 0xFFE27A7A);
        if (cost.error != null)
            g.drawWordWrap(font, Component.translatable(cost.error), x, y + 76,
                    Math.max(80, guiLeft + guiWidth - x - 8), 0xFFE27A7A);
    }

    private Cost cost(TeleportLocation location) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return new Cost(false, 0, "ac.gui.loctele.err_cp");
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        float exp = data.getProficiency("location_teleport");
        boolean cross = !mc.level.dimension().location().toString().equals(location.dimension());
        double dx = mc.player.getX() - location.x();
        double dy = mc.player.getY() - location.y();
        double dz = mc.player.getZ() - location.z();
        float distance = (float) Math.min(800, Math.sqrt(dx * dx + dy * dy + dz * dz));
        float cp = (200 - 50 * exp) * (cross ? 2 : 1) * Math.max(8, (float) Math.sqrt(distance));
        if (cross && exp <= .8f) return new Cost(false, cp, "ac.gui.loctele.err_exp");
        if (!data.isDevMode() && data.getCurrentCp() < cp) return new Cost(false, cp, "ac.gui.loctele.err_cp");
        return new Cost(true, cp, null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredRow >= 0 && hoveredRow < latest.size()) {
            if (hoverRemove) {
                PacketDistributor.sendToServer(new LocationTeleportActionPacket(
                        LocationTeleportActionPacket.REMOVE, hoveredRow, ""));
                return true;
            }
            if (hoverTeleport && cost(latest.get(hoveredRow)).available) {
                // Final 1.12.2 plays this locally as soon as the enabled
                // teleport button is clicked; it is not a server broadcast.
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.playSound(AcademySounds.TP_TP.value(), .5f, 1f);
                PacketDistributor.sendToServer(new LocationTeleportActionPacket(
                        LocationTeleportActionPacket.PERFORM, hoveredRow, ""));
                onClose();
                return true;
            }
        }
        if (button == 0 && hoverAdd) {
            addLocation();
            return true;
        }
        int split = split();
        if (button == 0 && isHovered(split - 49, guiTop + guiHeight - 24, 14, 20,
                (int) mouseX, (int) mouseY) && page > 0) { page--; return true; }
        if (button == 0 && isHovered(split - 36, guiTop + guiHeight - 24, 14, 20,
                (int) mouseX, (int) mouseY) && (page + 1) * ROWS < latest.size()) { page++; return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && name != null && name.isFocused()) {
            addLocation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, (latest.size() - 1) / ROWS);
        page = Math.clamp(page - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    private void addLocation() {
        if (name == null) return;
        String value = name.getValue().strip();
        if (value.isEmpty()) return;
        PacketDistributor.sendToServer(new LocationTeleportActionPacket(
                LocationTeleportActionPacket.ADD, -1, value));
        name.setValue("");
    }

    private int split() {
        return guiLeft + Math.min(LIST_W, Math.max(210, guiWidth - 126));
    }

    private void clampPage() {
        page = Math.clamp(page, 0, Math.max(0, (latest.size() - 1) / ROWS));
    }

    private static String dimensionName(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return raw;
        String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Language.getInstance().has(key) ? Component.translatable(key).getString() : raw;
    }

    public static void accept(List<TeleportLocation> locations) {
        latest = List.copyOf(locations);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LocationTeleportGui gui) gui.clampPage();
    }

    public static void resetClientSession() {
        latest = List.of();
    }

    private record Cost(boolean available, float cp, String error) {}
}
