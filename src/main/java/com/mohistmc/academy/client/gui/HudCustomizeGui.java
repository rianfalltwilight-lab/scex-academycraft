package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

/** Drag editor for the two legacy Academy HUD anchors exposed by the old Settings app. */
@OnlyIn(Dist.CLIENT)
public final class HudCustomizeGui extends AcademyScreen {
    private static final ResourceLocation CP_PREVIEW = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/edit_preview/cpbar.png");
    private static final ResourceLocation KEY_PREVIEW = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/edit_preview/key_hint.png");
    private static final int CP_W = 193;
    private static final int CP_H = 30;
    private static final int KEY_W = 59;
    private static final int KEY_H = 89;

    private DragTarget dragging = DragTarget.NONE;
    private int dragOffsetX;
    private int dragOffsetY;
    private int cpX;
    private int cpY;
    private int keyX;
    private int keyY;

    public HudCustomizeGui() {
        super(Component.translatable("ac.hud_editor.title"));
    }

    @Override
    protected void init() {
        super.init();
        cpX = clampX(width - CP_W - 12 + ACConfig.Client.cpBarX(), CP_W);
        cpY = clampY(ACConfig.Client.cpBarY(), CP_H);
        keyX = clampX(width - KEY_W - 12 + ACConfig.Client.keyHintX(), KEY_W);
        keyY = clampY(height / 2 - KEY_H / 2 + ACConfig.Client.keyHintY(), KEY_H);
        addRenderableWidget(Button.builder(Component.translatable("ac.hud_editor.reset"), ignored -> resetLayout())
                .bounds(width / 2 - 104, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> finish())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    private void resetLayout() {
        cpX = clampX(width - CP_W - 12, CP_W);
        cpY = clampY(12, CP_H);
        keyX = clampX(width - KEY_W - 12, KEY_W);
        keyY = clampY(height / 2 - KEY_H / 2 + 30, KEY_H);
        saveLayout();
    }

    private void finish() {
        saveLayout();
        Minecraft.getInstance().setScreen(new SettingsAppGui());
    }

    private void saveLayout() {
        ACConfig.Client.CP_BAR_X.set(cpX - (width - CP_W - 12));
        ACConfig.Client.CP_BAR_Y.set(cpY);
        ACConfig.Client.KEY_HINT_X.set(keyX - (width - KEY_W - 12));
        ACConfig.Client.KEY_HINT_Y.set(keyY - (height / 2 - KEY_H / 2));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x88030A14);
        graphics.drawCenteredString(font, title, width / 2, 10, AcademyColors.TEXT_ACCENT);
        graphics.drawCenteredString(font, Component.translatable("ac.hud_editor.hint"),
                width / 2, 24, AcademyColors.TEXT_SECONDARY);

        if (ModList.get().isLoaded("jade")) {
            int jadeW = Math.min(240, width / 2);
            graphics.fill(width - jadeW, 38, width, Math.min(height - 34, 108), 0x4438A060);
            graphics.drawString(font, Component.translatable("ac.hud_editor.jade_zone"),
                    width - jadeW + 5, 43, 0xFF8EE6A8);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(cpX, cpY, 0);
        graphics.pose().scale(0.2f, 0.2f, 1.0f);
        graphics.blit(CP_PREVIEW, 0, 0, 0, 0, 964, 147, 964, 147);
        graphics.pose().popPose();
        graphics.renderOutline(cpX, cpY, CP_W, CP_H,
                dragging == DragTarget.CP ? AcademyColors.SUCCESS : AcademyColors.ACCENT);
        graphics.drawString(font, Component.translatable("ac.hud_editor.cpbar"),
                cpX, Math.max(0, cpY - 10), AcademyColors.TEXT);

        graphics.pose().pushPose();
        graphics.pose().translate(keyX, keyY, 0);
        graphics.pose().scale(0.46f, 0.46f, 1.0f);
        graphics.blit(KEY_PREVIEW, 0, 0, 0, 0, 128, 193, 128, 193);
        graphics.pose().popPose();
        graphics.renderOutline(keyX, keyY, KEY_W, KEY_H,
                dragging == DragTarget.KEYS ? AcademyColors.SUCCESS : AcademyColors.ACCENT);
        graphics.drawString(font, Component.translatable("ac.hud_editor.keyhints"),
                keyX, Math.max(0, keyY - 10), AcademyColors.TEXT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(cpX, cpY, CP_W, CP_H, (int) mouseX, (int) mouseY)) {
            beginDrag(DragTarget.CP, (int) mouseX - cpX, (int) mouseY - cpY);
            return true;
        }
        if (button == 0 && isHovered(keyX, keyY, KEY_W, KEY_H, (int) mouseX, (int) mouseY)) {
            beginDrag(DragTarget.KEYS, (int) mouseX - keyX, (int) mouseY - keyY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void beginDrag(DragTarget target, int offsetX, int offsetY) {
        dragging = target;
        dragOffsetX = offsetX;
        dragOffsetY = offsetY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging != DragTarget.NONE) {
            if (dragging == DragTarget.CP) {
                cpX = clampX((int) mouseX - dragOffsetX, CP_W);
                cpY = clampY((int) mouseY - dragOffsetY, CP_H);
            } else {
                keyX = clampX((int) mouseX - dragOffsetX, KEY_W);
                keyY = clampY((int) mouseY - dragOffsetY, KEY_H);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging != DragTarget.NONE) {
            dragging = DragTarget.NONE;
            saveLayout();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        finish();
    }

    private int clampX(int value, int boxWidth) {
        return Math.clamp(value, 0, Math.max(0, width - boxWidth));
    }

    private int clampY(int value, int boxHeight) {
        return Math.clamp(value, 34, Math.max(34, height - boxHeight - 34));
    }

    private enum DragTarget { NONE, CP, KEYS }
}
