package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 技能 HUD — 参照旧版 KeyHintUI，仅显示按键+图标+冷却遮罩。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class AbilityHudOverlay {

    private static final ResourceLocation TEX_ICON_BACK =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/guis/key_hint/icon_back.png");

    private static final int ICON_SIZE = 18;
    private static final int SLOT_SPACING = 6;
    private static final int MARGIN = 12;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        if (!ACConfig.Client.showHud() || !ACConfig.Client.showKeyHints()) return;

        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility() || !data.isAbilityActive()) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        SkillPreset preset = data.getCurrentPreset();
        KeyMapping[] keys = KeyInputHandler.getSkillKeys();

        // 收集有技能的槽位
        List<Integer> activeSlots = new ArrayList<>();
        for (int i = 0; i < SkillPreset.SLOT_COUNT; i++) {
            if (preset.getSlot(i) != null) activeSlots.add(i);
        }
        if (activeSlots.isEmpty()) return;

        int totalH = activeSlots.size() * (ICON_SIZE + 6) - 6;
        int x = Math.clamp(screenW - ICON_SIZE - MARGIN + ACConfig.Client.keyHintX(),
                0, Math.max(0, screenW - ICON_SIZE));
        int y = Math.clamp(screenH / 2 - totalH / 2 + ACConfig.Client.keyHintY(),
                0, Math.max(0, screenH - totalH));

        for (int idx = 0; idx < activeSlots.size(); idx++) {
            int slot = activeSlots.get(idx);
            int iy = y + idx * (ICON_SIZE + 6 + SLOT_SPACING);
            String skillId = preset.getSlot(slot);

            // 按键标签（左侧小字）
            String keyLabel = slot < keys.length
                    ? keys[slot].getTranslatedKeyMessage().getString() : "?";
            int labelW = mc.font.width(keyLabel);
            int labelX = x - labelW - 6;
            int labelY = iy + (ICON_SIZE - mc.font.lineHeight) / 2;
            g.drawString(mc.font, keyLabel, labelX, labelY, 0xFF4488CC, false);

            // Old 1.0.7 identity: blue key-hint frame plus the actual per-skill icon.
            g.fill(x, iy, x + ICON_SIZE, iy + ICON_SIZE, 0xCC10283A);
            g.renderOutline(x, iy, ICON_SIZE, ICON_SIZE, 0xFF4488CC);

            // 图标 + 冷却遮罩
            if (skillId != null) {
                Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
                if (skill != null) {
                    RenderUtils.render(ICON_SIZE - 2, ICON_SIZE - 2, x + 1, iy + 1,
                            g, skill.getIconLocation());

                    // Immediate held/toggled feedback. These highlights never predict a
                    // successful activation; synchronized data/cooldown remains authoritative.
                    if (KeyInputHandler.isSkillHeld(slot)) {
                        int pulse = ((System.currentTimeMillis() / 140L) & 1L) == 0L
                                ? 0xFFFFFFFF : 0xFF55CCFF;
                        g.renderOutline(x - 1, iy - 1, ICON_SIZE + 2, ICON_SIZE + 2, pulse);
                        if ("mark_teleport".equals(skillId)) {
                            int frame = (int) ((System.currentTimeMillis() / 70L) & 7L);
                            ResourceLocation mark = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                                    "textures/effects/tp_mark/" + frame + ".png");
                            int markSize = 32;
                            RenderUtils.render(markSize, markSize,
                                    screenW / 2 - markSize / 2, screenH / 2 - markSize / 2, g, mark);
                        }
                    } else if ("flashing".equals(skillId) && KeyInputHandler.isFlashingActive()) {
                        g.renderOutline(x - 1, iy - 1, ICON_SIZE + 2, ICON_SIZE + 2, 0xFFBB66FF);
                        int direction=KeyInputHandler.getFlashingHeldDirection();
                        if(direction>=0){String key=switch(direction){case 0->"a";case 1->"d";case 2->"w";default->"s";};
                            ResourceLocation cue=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/abilities/teleporter/flashing/"+key+".png");int size=28;
                            RenderUtils.render(size, size, screenW / 2 - size / 2,
                                    screenH / 2 + 18, g, cue);
                        } else g.drawString(mc.font, "WASD", x - mc.font.width("WASD") - 6,
                                iy + ICON_SIZE - mc.font.lineHeight, 0xFFCC99FF, false);
                    }

                    // 冷却遮罩 — 原版风格：从上往下覆盖，随冷却减少向上收缩
                    int cdTicks = data.getCooldownTicks(skillId);
                    if (cdTicks > 0) {
                        int maxCd = data.getMaxCooldownTicks(skillId);
                        float cdProgress = maxCd > 0 ? (float) cdTicks / maxCd : 0;
                        // cdProgress: 0=冷却完毕, 1=冷却刚开始
                        // 遮罩从顶部向下覆盖，高度 = 剩余冷却比例
                        int cdH = (int) (ICON_SIZE * cdProgress);
                        if (cdH > 0) {
                            g.fill(x, iy, x + ICON_SIZE, iy + cdH, 0xAA000000);
                        }
                    }
                }
            }
        }
    }
}
