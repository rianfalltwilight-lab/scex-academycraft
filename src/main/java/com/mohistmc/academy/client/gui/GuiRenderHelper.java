package com.mohistmc.academy.client.gui;

import com.mohistmc.academy.utils.RenderUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * GUI 半透明纹理绘制 —— 基于已验证正常的 g.blit + 内部自包含 enableBlend。
 */
public final class GuiRenderHelper {

    private GuiRenderHelper() {}

    /** 绘制带 alpha 混合的纹理(全图采样,平铺到 x,y,w,h) */
    public static void blitTranslucent(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        blitTranslucent(g, tex, x, y, w, h, 0, 0, 1, 1);
    }

    /** 绘制带 alpha 混合的纹理(指定 UV 区域采样) */
    public static void blitTranslucent(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                                       float u0, float v0, float u1, float v1) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var intrinsic = RenderUtils.textureSize(tex);
        float du = Math.max(0.0001f, u1 - u0);
        float dv = Math.max(0.0001f, v1 - v0);
        int texW = intrinsic.width() > 0 ? intrinsic.width() : Math.max(1, Math.round(w / du));
        int texH = intrinsic.height() > 0 ? intrinsic.height() : Math.max(1, Math.round(h / dv));
        int uOff = Math.clamp(Math.round(u0 * texW), 0, texW - 1);
        int vOff = Math.clamp(Math.round(v0 * texH), 0, texH - 1);
        int srcW = Math.clamp(Math.round(du * texW), 1, texW - uOff);
        int srcH = Math.clamp(Math.round(dv * texH), 1, texH - vOff);
        g.blit(tex, x, y, w, h, uOff, vOff, srcW, srcH, texW, texH);
        RenderSystem.disableBlend();
    }
}
