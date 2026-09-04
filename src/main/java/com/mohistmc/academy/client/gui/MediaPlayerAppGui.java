package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.client.MediaPlayerManager;
import com.mohistmc.academy.client.media.ExternalMediaManager;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.terminal.MediaTrack;
import com.mohistmc.academy.terminal.MediaTrackRegistry;
import com.mohistmc.academy.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class MediaPlayerAppGui extends AcademyScreen {

    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 210;
    private static final int TOP_BAR = 28;
    private static final int BACK_BTN_SIZE = 18;
    private static final int TRACK_HEIGHT = 40;
    private static final int TRACK_GAP = 6;
    private static final int CONTROL_BAR_HEIGHT = 40;
    private static final int IMG_SIZE = 32;

    private static final int COLOR_TRACK_PLAYING = 0xFF0a2a3a;
    private static final int COLOR_TRACK_BORDER = 0xFF1e3a5f;
    private static final int COLOR_TRACK_BORDER_PLAYING = 0xFF00bcd4;
    private static final int COLOR_TRACK_HOVER = 0x2200bcd4;
    private static final int COLOR_PROGRESS_BG = 0xFF1a2a3a;
    private static final int COLOR_PROGRESS_BAR = 0xFF00bcd4;
    private static final int COLOR_CONTROL_BG = 0xFF0a1628;
    private static final ResourceLocation TEX_PLAY = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/guis/apps/media_player/play.png");
    private static final ResourceLocation TEX_PAUSE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/guis/apps/media_player/pause.png");
    private static final ResourceLocation TEX_STOP = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/guis/apps/media_player/stop.png");
    private static final ResourceLocation TEX_EDIT = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/guis/icons/edit.png");

    private boolean hoveredBack = false;
    private boolean hoveredPlayPause = false;
    private boolean hoveredStop = false;
    private boolean draggingVolume = false;
    private int hoveredTrack = -1;
    private String hoveredTrackId = null;
    private String hoveredEditNameId = null;
    private String hoveredEditDescriptionId = null;
    private int animTick = 0;
    private int scrollOffset = 0;
    private EditBox activeEditor = null;
    private String editingTrackId = null;
    private boolean editingName = false;

    private List<MediaTrack> visibleTracks = List.of();

    public MediaPlayerAppGui() {
        super(Component.translatable("item.academy.app_media_player"));
    }

    @Override
    protected void init() {
        super.init();
        centerGui(GUI_WIDTH, GUI_HEIGHT);
        ExternalMediaManager.initialize();
        refreshVisibleTracks();
    }

    @Override
    public void tick() {
        super.tick();
        if (++animTick % 20 == 0) refreshVisibleTracks();
    }

    private void refreshVisibleTracks() {
        List<MediaTrack> refreshed = new ArrayList<>();
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            visibleTracks = List.of();
            hoveredTrack = -1;
            hoveredTrackId = null;
            return;
        }
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        Set<String> loaded = data.getLoadedMedia();
        for (MediaTrack track : MediaTrackRegistry.getAllTracks()) {
            if (loaded.contains(track.trackId())) refreshed.add(track);
        }
        refreshed.addAll(ExternalMediaManager.getTracks());
        visibleTracks = List.copyOf(refreshed);
        int visibleHeight = GUI_HEIGHT - TOP_BAR - CONTROL_BAR_HEIGHT - 12;
        int maxScroll = Math.max(0, visibleTracks.size() * (TRACK_HEIGHT + TRACK_GAP) - visibleHeight);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
        if (hoveredTrack >= visibleTracks.size()) { hoveredTrack = -1; hoveredTrackId = null; }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        pushZ(graphics);

        drawBackground(graphics, AcademyColors.BG);
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + TOP_BAR, AcademyColors.BG_PANEL);
        graphics.fill(guiLeft, guiTop + TOP_BAR, guiLeft + GUI_WIDTH, guiTop + TOP_BAR + 1, AcademyColors.ACCENT);

        int backX = guiLeft + 6, backY = guiTop + 5;
        hoveredBack = drawBackButton(graphics, backX, backY, mouseX, mouseY);

        String title = Component.translatable("item.academy.app_media_player").getString();
        graphics.drawString(this.font, title, backX + BACK_BTN_SIZE + 6, guiTop + 9, AcademyColors.TEXT_ACCENT);

        boolean playing = MediaPlayerManager.isPlaying();
        if (playing) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(animTick * 0.1));
            int dotColor = ((int) (pulse * 255) << 24) | 0x00e5ff;
            graphics.fill(guiLeft + GUI_WIDTH - 16, guiTop + 10, guiLeft + GUI_WIDTH - 12, guiTop + 14, dotColor);
        }

        int tracksStartY = guiTop + TOP_BAR + 8;
        int tracksEndY = guiTop + GUI_HEIGHT - CONTROL_BAR_HEIGHT - 4;
        hoveredTrack = -1;
        hoveredTrackId = null;
        hoveredEditNameId = null;
        hoveredEditDescriptionId = null;
        int textX = guiLeft + 12 + IMG_SIZE + 6;
        int editorScreenY = Integer.MIN_VALUE;

        graphics.enableScissor(guiLeft + 4, tracksStartY, guiLeft + GUI_WIDTH - 4, tracksEndY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        if (visibleTracks.isEmpty()) {
            String emptyMsg = Component.translatable("ac.media.empty").getString();
            graphics.drawString(this.font, emptyMsg, guiLeft + (GUI_WIDTH - this.font.width(emptyMsg)) / 2,
                    tracksStartY + (tracksEndY - tracksStartY) / 2 + scrollOffset, AcademyColors.TEXT_MUTED);
        }

        for (int i = 0; i < visibleTracks.size(); i++) {
            MediaTrack track = visibleTracks.get(i);
            int trackY = tracksStartY + i * (TRACK_HEIGHT + TRACK_GAP);

            int screenTrackY = trackY - scrollOffset;
            boolean isHovered = screenTrackY >= tracksStartY && screenTrackY + TRACK_HEIGHT <= tracksEndY
                    && isHovered(guiLeft + 8, screenTrackY, GUI_WIDTH - 16, TRACK_HEIGHT, mouseX, mouseY);
            if (isHovered) { hoveredTrack = i; hoveredTrackId = track.trackId(); }

            boolean isThisPlaying = MediaPlayerManager.isTrackPlaying(track.trackId());
            int bgColor = isThisPlaying ? COLOR_TRACK_PLAYING : (i % 2 == 0 ? AcademyColors.BG_CARD : AcademyColors.BG_CARD_ALT);
            int borderColor = isThisPlaying ? COLOR_TRACK_BORDER_PLAYING : COLOR_TRACK_BORDER;

            graphics.fill(guiLeft + 8, trackY, guiLeft + GUI_WIDTH - 8, trackY + TRACK_HEIGHT, bgColor);
            drawBorder(graphics, guiLeft + 8, trackY, GUI_WIDTH - 16, TRACK_HEIGHT, borderColor);

            if (isHovered && !isThisPlaying)
                graphics.fill(guiLeft + 9, trackY + 1, guiLeft + GUI_WIDTH - 9, trackY + TRACK_HEIGHT - 1, COLOR_TRACK_HOVER);

            int imgX = guiLeft + 12, imgY = trackY + (TRACK_HEIGHT - IMG_SIZE) / 2;
            RenderUtils.render(IMG_SIZE, IMG_SIZE, imgX, imgY, graphics, track.texture());

            if (isThisPlaying)
                drawBorder(graphics, imgX - 1, imgY - 1, IMG_SIZE + 2, IMG_SIZE + 2, COLOR_TRACK_BORDER_PLAYING);

            String tagLabel = track.tag();
            int tagW = this.font.width(tagLabel) + 6, tagX = guiLeft + GUI_WIDTH - 8 - tagW - 4;
            graphics.fill(tagX, trackY + 4, tagX + tagW, trackY + 16, 0xFF1a3050);
            drawBorder(graphics, tagX, trackY + 4, tagW, 12, AcademyColors.SEPARATOR);
            graphics.drawString(this.font, tagLabel, tagX + 3, trackY + 6, AcademyColors.TEXT_ACCENT);

            int editX = tagX - 13;
            if (track.external()) {
                RenderUtils.render(8, 8, editX, trackY + 4, graphics, TEX_EDIT);
                RenderUtils.render(8, 8, editX, trackY + 18, graphics, TEX_EDIT);
                if (isHovered && isHovered(editX - 1, screenTrackY + 3, 10, 10, mouseX, mouseY)) {
                    hoveredEditNameId = track.trackId();
                }
                if (isHovered && isHovered(editX - 1, screenTrackY + 17, 10, 10, mouseX, mouseY)) {
                    hoveredEditDescriptionId = track.trackId();
                }
            }

            int textWidth = Math.max(30, editX - textX - 3);
            boolean editingThis = track.trackId().equals(editingTrackId) && activeEditor != null;
            String trackName = this.font.plainSubstrByWidth(track.displayName().getString(), textWidth);
            if (!editingThis || !editingName) {
                graphics.drawString(this.font, trackName, textX, trackY + 6,
                        isThisPlaying ? AcademyColors.TEXT_ACCENT : AcademyColors.TEXT);
            }
            if (!editingThis || editingName) {
                String description = this.font.plainSubstrByWidth(
                        track.displayDescription().getString(), textWidth);
                graphics.drawString(this.font, description, textX, trackY + 18,
                        AcademyColors.TEXT_SECONDARY);
            }
            if (editingThis) editorScreenY = screenTrackY + (editingName ? 2 : 15);

            String durStr = MediaPlayerManager.formatTime(track.durationSeconds());
            graphics.drawString(this.font, durStr, guiLeft + GUI_WIDTH - this.font.width(durStr) - 16,
                    trackY + TRACK_HEIGHT - 14, AcademyColors.TEXT_MUTED);

            if (isThisPlaying) {
                int barX = textX, barY = trackY + TRACK_HEIGHT - 6, barW = guiLeft + GUI_WIDTH - 16 - textX;
                graphics.fill(barX, barY, barX + barW, barY + 3, COLOR_PROGRESS_BG);
                graphics.fill(barX, barY, barX + (int) (barW * MediaPlayerManager.getProgress()), barY + 3, COLOR_PROGRESS_BAR);

                String timeStr = MediaPlayerManager.formatTime(MediaPlayerManager.getElapsedSeconds()) + " / " + durStr;
                graphics.drawString(this.font, timeStr, guiLeft + GUI_WIDTH - this.font.width(timeStr) - 16, barY - 8, AcademyColors.TEXT_MUTED);
            } else if (isHovered) {
                graphics.drawString(this.font, Component.translatable("ac.media.click_play").getString(),
                        textX, trackY + TRACK_HEIGHT - 14, AcademyColors.TEXT_MUTED);
            }
        }

        graphics.pose().popPose();
        if (activeEditor != null) {
            boolean visible = editorScreenY >= tracksStartY && editorScreenY + 14 <= tracksEndY;
            activeEditor.setVisible(visible);
            if (visible) {
                activeEditor.setX(textX - 2);
                activeEditor.setY(editorScreenY);
                activeEditor.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        graphics.disableScissor();

        int maxScroll = Math.max(0, visibleTracks.size() * (TRACK_HEIGHT + TRACK_GAP) - (tracksEndY - tracksStartY));
        if (maxScroll > 0) {
            int scrollBarH = tracksEndY - tracksStartY - 4;
            int thumbH = Math.max(10, scrollBarH * scrollBarH / (visibleTracks.size() * (TRACK_HEIGHT + TRACK_GAP)));
            int thumbY = tracksStartY + 2 + (scrollBarH - thumbH) * scrollOffset / maxScroll;
            graphics.fill(guiLeft + GUI_WIDTH - 5, tracksStartY + 2, guiLeft + GUI_WIDTH - 3, tracksStartY + 2 + scrollBarH, 0x44FFFFFF);
            graphics.fill(guiLeft + GUI_WIDTH - 5, thumbY, guiLeft + GUI_WIDTH - 3, thumbY + thumbH, 0x8800bcd4);
        }

        int controlY = guiTop + GUI_HEIGHT - CONTROL_BAR_HEIGHT;
        graphics.fill(guiLeft, controlY, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COLOR_CONTROL_BG);
        graphics.fill(guiLeft, controlY, guiLeft + GUI_WIDTH, controlY + 1, AcademyColors.SEPARATOR);

        int buttonY = controlY + 11;
        hoveredPlayPause = (!visibleTracks.isEmpty() || playing)
                && isHovered(guiLeft + 10, buttonY, 18, 18, mouseX, mouseY);
        hoveredStop = playing && isHovered(guiLeft + 34, buttonY, 18, 18, mouseX, mouseY);
        if (hoveredPlayPause) graphics.fill(guiLeft + 9, buttonY - 1, guiLeft + 29, buttonY + 19, 0x3300e5ff);
        if (hoveredStop) graphics.fill(guiLeft + 33, buttonY - 1, guiLeft + 53, buttonY + 19, 0x33ff5555);
        RenderUtils.render(18, 18, guiLeft + 10, buttonY, graphics,
                playing && !MediaPlayerManager.isPaused() ? TEX_PAUSE : TEX_PLAY);
        RenderUtils.render(18, 18, guiLeft + 34, buttonY, graphics, TEX_STOP);

        int volumeX = guiLeft + 62, volumeY = controlY + 18, volumeW = 68;
        graphics.fill(volumeX, volumeY, volumeX + volumeW, volumeY + 3, COLOR_PROGRESS_BG);
        int volumeFill = Math.round(volumeW * MediaPlayerManager.getVolume());
        graphics.fill(volumeX, volumeY, volumeX + volumeFill, volumeY + 3, COLOR_PROGRESS_BAR);
        graphics.fill(volumeX + volumeFill - 2, volumeY - 4, volumeX + volumeFill + 2, volumeY + 7,
                AcademyColors.TEXT);
        graphics.drawString(this.font, Component.translatable("ac.media.volume").getString(),
                volumeX, controlY + 5, AcademyColors.TEXT_MUTED);

        if (playing) {
            MediaTrack current = MediaPlayerManager.getTrack(MediaPlayerManager.getCurrentTrack());
            String currentTrackName = current == null ? ""
                    : current.displayName().getString();
            String statusKey = MediaPlayerManager.isPaused()
                    ? "ac.media.status.paused" : "ac.media.status.playing";
            String status = Component.translatable(statusKey, currentTrackName).getString();
            status = this.font.plainSubstrByWidth(status, GUI_WIDTH - 148);
            graphics.drawString(this.font, status, guiLeft + 142, controlY + 7, AcademyColors.TEXT_ACCENT);
            graphics.drawString(this.font, MediaPlayerManager.formatTime(MediaPlayerManager.getElapsedSeconds()),
                    guiLeft + 142, controlY + 22, AcademyColors.TEXT_MUTED);
        } else {
            String hint = Component.translatable(visibleTracks.isEmpty()
                    ? "ac.media.load_hint" : "ac.media.select_hint").getString();
            hint = this.font.plainSubstrByWidth(hint, GUI_WIDTH - 148);
            graphics.drawString(this.font, hint, guiLeft + 142, controlY + 16, AcademyColors.TEXT_MUTED);
        }

        popZ(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeEditor != null) {
            if (activeEditor.isVisible() && activeEditor.mouseClicked(mouseX, mouseY, button)) return true;
            finishEditing();
        }
        if (hoveredBack && button == 0) {
            Minecraft.getInstance().setScreen(new DataTerminalGui());
            return true;
        }
        if (button == 0 && hoveredEditNameId != null) {
            beginEditing(hoveredEditNameId, true);
            return true;
        }
        if (button == 0 && hoveredEditDescriptionId != null) {
            beginEditing(hoveredEditDescriptionId, false);
            return true;
        }
        if (hoveredStop && button == 0) {
            MediaPlayerManager.stop();
            return true;
        }
        if (hoveredPlayPause && button == 0) {
            if (MediaPlayerManager.isPlaying()) {
                MediaPlayerManager.togglePause();
            } else {
                String last = MediaPlayerManager.getLastTrack();
                MediaTrack selected = visibleTracks.stream()
                        .filter(track -> track.trackId().equals(last)).findFirst()
                        .orElse(visibleTracks.isEmpty() ? null : visibleTracks.getFirst());
                if (selected != null) MediaPlayerManager.play(selected.trackId());
            }
            return true;
        }
        int volumeX = guiLeft + 62, volumeY = guiTop + GUI_HEIGHT - CONTROL_BAR_HEIGHT + 12;
        if (button == 0 && mouseX >= volumeX - 3 && mouseX < volumeX + 71
                && mouseY >= volumeY && mouseY < volumeY + 17) {
            draggingVolume = true;
            setVolumeFromMouse(mouseX);
            return true;
        }
        List<MediaTrack> tracks = visibleTracks;
        if (hoveredTrack >= 0 && hoveredTrack < tracks.size() && button == 0) {
            String trackId = tracks.get(hoveredTrack).trackId();
            if (!trackId.equals(hoveredTrackId)) {
                hoveredTrack = -1;
                hoveredTrackId = null;
                return true;
            }
            Player player = Minecraft.getInstance().player;
            if (player == null || !player.getData(AcademyAttachments.PLAYER_ABILITY)
                    .getLoadedMedia().contains(trackId)) {
                hoveredTrack = -1;
                hoveredTrackId = null;
                return true;
            }
            // Legacy list selection always starts/restarts the selected track;
            // pause/resume belongs to the dedicated transport control.
            MediaPlayerManager.play(trackId);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        finishEditing();
        int tracksStartY = guiTop + TOP_BAR + 8;
        int tracksEndY = guiTop + GUI_HEIGHT - CONTROL_BAR_HEIGHT - 4;
        int maxScroll = Math.max(0, visibleTracks.size() * (TRACK_HEIGHT + TRACK_GAP) - (tracksEndY - tracksStartY));
        scrollOffset = (int) Math.clamp(scrollOffset - scrollY * 10, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingVolume && button == 0) {
            setVolumeFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingVolume) {
            draggingVolume = false;
            setVolumeFromMouse(mouseX);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void setVolumeFromMouse(double mouseX) {
        int volumeX = guiLeft + 62;
        MediaPlayerManager.setVolume((float) ((mouseX - volumeX) / 68.0));
    }

    private void beginEditing(String trackId, boolean name) {
        MediaTrack track = MediaPlayerManager.getTrack(trackId);
        if (track == null || !track.external()) return;
        editingTrackId = trackId;
        editingName = name;
        activeEditor = new EditBox(this.font, 0, 0, 112, 14,
                Component.translatable(name ? "ac.media.edit.name" : "ac.media.edit.description"));
        activeEditor.setMaxLength(name ? 80 : 160);
        activeEditor.setValue(name ? track.externalName() : track.externalDescription());
        activeEditor.setTextColor(AcademyColors.TEXT);
        activeEditor.setFocused(true);
        activeEditor.moveCursorToEnd(false);
    }

    private void finishEditing() {
        if (activeEditor == null || editingTrackId == null) return;
        MediaTrack track = MediaPlayerManager.getTrack(editingTrackId);
        if (track != null && track.external()) {
            ExternalMediaManager.updateMetadata(editingTrackId,
                    editingName ? activeEditor.getValue() : track.externalName(),
                    editingName ? track.externalDescription() : activeEditor.getValue());
        }
        activeEditor = null;
        editingTrackId = null;
        refreshVisibleTracks();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeEditor != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishEditing();
                return true;
            }
            if (activeEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeEditor != null && activeEditor.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        finishEditing();
        super.removed();
    }
}
