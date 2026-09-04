package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ChargingHudOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Literal 1.0.7 WaveEffectUI used while vector deviation/reflection is held. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class LegacyVectorWaveOverlay {
    private static final ResourceLocation GLOW_CIRCLE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/glow_circle.png");
    private static final List<Ripple> RIPPLES = new ArrayList<>();
    private static long lastFrameNanos;
    private static String activeSkill = "";

    private LegacyVectorWaveOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        String skill = ChargingHudOverlay.isCharging("vec_reflection") ? "vec_reflection"
                : ChargingHudOverlay.isCharging("vec_deviation") ? "vec_deviation" : "";
        if (mc.player == null || mc.screen != null || skill.isEmpty()) {
            reset();
            return;
        }
        if (!skill.equals(activeSkill)) {
            RIPPLES.clear();
            activeSkill = skill;
            lastFrameNanos = System.nanoTime();
        }

        long now = System.nanoTime();
        float delta = lastFrameNanos == 0 ? 0 : Math.min(.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        float maxAlpha = "vec_reflection".equals(skill) ? .4f : .2f;
        float averageSize = "vec_reflection".equals(skill) ? 110f : 100f;
        float intensity = "vec_reflection".equals(skill) ? 1.6f : 1.4f;

        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext();) {
            Ripple ripple = it.next();
            ripple.age += delta;
            if (ripple.age >= 2f) it.remove();
        }
        // WaveEffectUI used one Bernoulli trial per frame, not an accumulated
        // timer; preserve that sparse, irregular cadence.
        if (mc.level.random.nextFloat() < delta * intensity) {
            RIPPLES.add(new Ripple(mc.level.random.nextFloat() * width,
                    mc.level.random.nextFloat() * height,
                    averageSize * (.8f + mc.level.random.nextFloat() * .4f)));
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Ripple ripple : RIPPLES) {
            float progress = ripple.age / 2f;
            float alpha = progress < .2f ? progress / .2f
                    : progress < .5f ? 1f : 1f - (progress - .5f) / .5f;
            float size = ripple.initialSize + ripple.age * 20f;
            int drawSize = Math.max(1, Math.round(size));
            int x = Math.round(ripple.x - size * .5f);
            int y = Math.round(ripple.y - size * .5f);
            RenderSystem.setShaderColor(1, 1, 1, Mth.clamp(maxAlpha * alpha, 0, 1));
            event.getGuiGraphics().blit(GLOW_CIRCLE, x, y, drawSize, drawSize,
                    0, 0, 256, 256, 256, 256);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    public static void reset() {
        RIPPLES.clear();
        activeSkill = "";
        lastFrameNanos = 0;
    }

    private static final class Ripple {
        final float x;
        final float y;
        final float initialSize;
        float age;

        Ripple(float x, float y, float initialSize) {
            this.x = x;
            this.y = y;
            this.initialSize = initialSize;
        }
    }
}
