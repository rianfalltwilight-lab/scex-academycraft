package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class ChargingHudOverlay {

    private static int currentTicks = 0;
    private static int maxTicks = 0;
    private static String currentSkillId = "";
    private static boolean charging = false;
    private static long lastUpdateTime = 0;

    public static void setChargingState(int ticks, int max, String skillId) {
        currentTicks = ticks;
        maxTicks = max;
        currentSkillId = skillId;
        charging = ticks >= 0;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static void resetClientSession() {
        currentTicks = 0;
        maxTicks = 0;
        currentSkillId = "";
        charging = false;
        lastUpdateTime = 0;
    }

    /** Read-only presentation state; skill authority remains on the server. */
    public static boolean isCharging(String skillId) {
        return charging && currentSkillId.equals(skillId)
                && System.currentTimeMillis() - lastUpdateTime <= 5000;
    }

    public static float chargeProgress() {
        return maxTicks > 0 ? Math.min(1.0f, Math.max(0.0f, (float) currentTicks / maxTicks)) : 0.0f;
    }

    /** Raw server tick for presentation parity; never used to authorize a cast. */
    public static int currentTicks() { return currentTicks; }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!charging) return;
        if (!ACConfig.Client.showHud() || !ACConfig.Client.showChargingHud()) return;

        // 超时保护：超过 5 秒没收到更新，自动清除（防止包丢失导致残留）
        if (System.currentTimeMillis() - lastUpdateTime > 5000) {
            charging = false;
            return;
        }

        if (Minecraft.getInstance().screen != null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barWidth = 120;
        int barHeight = 10;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight - 60;

        event.getGuiGraphics().fill(x, y, x + barWidth, y + barHeight, 0xFF333333);

        float progress = maxTicks > 0 ? Math.min((float) currentTicks / maxTicks, 1.0f) : 0;
        int progressWidth = (int) (barWidth * progress);
        int color = progress >= 1.0f ? 0xFF00FF00 : 0xFFFFFF00;
        if ("railgun".equals(currentSkillId) && progress >= 0.65f) {
            // A deliberately broad, readable QTE window cue. It is presentation only:
            // the server still decides whether a coin and the release timing are valid.
            color = ((System.currentTimeMillis() / 120L) & 1L) == 0L ? 0xFF66EEFF : 0xFFFFFFFF;
        }
        event.getGuiGraphics().fill(x, y, x + progressWidth, y + barHeight, color);

        event.getGuiGraphics().renderOutline(x, y, barWidth, barHeight, 0xFFFFFFFF);

        if (!currentSkillId.isEmpty()) {
            PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
            Skill skill = data.hasAbility()
                    ? SkillRegistry.getSkill(data.getCurrentAbility(), currentSkillId)
                    : null;
            if (skill != null) {
                Component name = Component.translatable(skill.getTranslationKey());
                int textWidth = mc.font.width(name);
                event.getGuiGraphics().drawString(mc.font, name, (screenWidth - textWidth) / 2, y - 12, 0xFFFFFFFF);
                RenderUtils.render(16, 16, x - 18, y - 3,
                        event.getGuiGraphics(), skill.getIconLocation());
                if ("railgun".equals(currentSkillId)) {
                    // The old railgun charge identity: coin/ray art and a server-neutral QTE cue.
                    ResourceLocation railgun = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                            "textures/effects/railgun.png");
                    RenderUtils.render(16, 16, x + barWidth + 3, y - 3,
                            event.getGuiGraphics(), railgun);
                }
                Component hint = chargeHint(currentSkillId, progress);
                int hintWidth = mc.font.width(hint);
                event.getGuiGraphics().drawString(mc.font, hint, (screenWidth - hintWidth) / 2,
                        y + barHeight + 3, 0xFFB8D8FF);
            }
        }
    }

    private static Component chargeHint(String skillId, float progress) {
        if ("railgun".equals(skillId)) {
            return Component.literal(progress >= 0.65f ? "[ QTE ] RELEASE / 松开发射" : "HOLD / 长按蓄力");
        }
        if ("light_shield".equals(skillId)) return Component.literal("SHIELD ACTIVE / 护盾维持中");
        if ("electron_missile".equals(skillId)) return Component.literal("MISSILES LOCKING / 导弹锁定中");
        if ("ray_barrage".equals(skillId)) return Component.literal("TARGETING / 射线定位中");
        if ("storm_wing".equals(skillId)) return Component.literal("FLIGHT ACTIVE / 飞行维持中");
        if ("vec_deviation".equals(skillId)) return Component.literal("VECTOR FIELD / 矢量偏转中");
        return Component.literal(progress >= 1.0f ? "READY / 可释放" : "HOLD / 长按蓄力");
    }
}
