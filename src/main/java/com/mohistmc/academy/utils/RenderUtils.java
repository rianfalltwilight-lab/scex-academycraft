package com.mohistmc.academy.utils;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

public class RenderUtils {
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int PNG_IHDR = 0x49484452;
    private static final Map<ResourceLocation, TextureSize> TEXTURE_SIZES = new ConcurrentHashMap<>();

    /** Intrinsic resource dimensions used when a high-resolution GUI asset is scaled down. */
    public record TextureSize(int width, int height) {}
    /**
     * 渲染由四个四边形组合成的部件
     */
    public static void renderPart(PoseStack strack, VertexConsumer consumer, float p_112158_, float p_112159_, float p_112160_, float p_112161_, int p_112162_, int p_112163_, float p_112164_, float p_112165_, float p_112166_, float p_112167_, float p_112168_, float p_112169_, float p_112170_, float p_112171_, float p_112172_, float p_112173_, float p_112174_, float p_112175_) {
        PoseStack.Pose pose = strack.last();
        Matrix4f matrix4f = pose.pose();
        renderQuad(matrix4f, pose, consumer, p_112158_, p_112159_, p_112160_, p_112161_, p_112162_, p_112163_, p_112164_, p_112165_, p_112166_, p_112167_, p_112172_, p_112173_, p_112174_, p_112175_);
        renderQuad(matrix4f, pose, consumer, p_112158_, p_112159_, p_112160_, p_112161_, p_112162_, p_112163_, p_112170_, p_112171_, p_112168_, p_112169_, p_112172_, p_112173_, p_112174_, p_112175_);
        renderQuad(matrix4f, pose, consumer, p_112158_, p_112159_, p_112160_, p_112161_, p_112162_, p_112163_, p_112166_, p_112167_, p_112170_, p_112171_, p_112172_, p_112173_, p_112174_, p_112175_);
        renderQuad(matrix4f, pose, consumer, p_112158_, p_112159_, p_112160_, p_112161_, p_112162_, p_112163_, p_112168_, p_112169_, p_112164_, p_112165_, p_112172_, p_112173_, p_112174_, p_112175_);
    }

    public static void renderQuad(Matrix4f p_253960_, PoseStack.Pose p_334620_, VertexConsumer p_112122_, float p_112123_, float p_112124_, float p_112125_, float p_112126_, int p_112127_, int p_112128_, float p_112129_, float p_112130_, float p_112131_, float p_112132_, float p_112133_, float p_112134_, float p_112135_, float p_112136_) {
        addVertex(p_253960_, p_334620_, p_112122_, p_112123_, p_112124_, p_112125_, p_112126_, p_112128_, p_112129_, p_112130_, p_112134_, p_112135_);
        addVertex(p_253960_, p_334620_, p_112122_, p_112123_, p_112124_, p_112125_, p_112126_, p_112127_, p_112129_, p_112130_, p_112134_, p_112136_);
        addVertex(p_253960_, p_334620_, p_112122_, p_112123_, p_112124_, p_112125_, p_112126_, p_112127_, p_112131_, p_112132_, p_112133_, p_112136_);
        addVertex(p_253960_, p_334620_, p_112122_, p_112123_, p_112124_, p_112125_, p_112126_, p_112128_, p_112131_, p_112132_, p_112133_, p_112135_);
    }

    public static void addVertex(Matrix4f p_253955_, PoseStack.Pose p_334620_, VertexConsumer p_253894_, float p_253871_, float p_253841_, float p_254568_, float p_254361_, int p_254357_, float p_254451_, float p_254240_, float p_254117_, float p_253698_) {
        p_253894_.addVertex(p_253955_, p_254451_, (float) p_254357_, p_254240_).setColor(p_253871_, p_253841_, p_254568_, p_254361_).setUv(p_254117_, p_253698_).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(p_334620_, 0.0F, 1.0F, 0.0F);
    }

    public static void renderCenter(int drawWidth, int drawHeight, int width, int height, GuiGraphics poseStack, ResourceLocation resource) {
        renderCenter(0, 0, drawWidth, drawHeight, width, height, poseStack, resource);
    }

    public static void renderCenter(int x, int y, int drawWidth, int drawHeight, int width, int height, GuiGraphics poseStack, ResourceLocation resource) {
        int left = (width - drawWidth) / 2;
        int top = (height - drawHeight) / 2;
        render(drawWidth, drawHeight, left + x, top + y, poseStack, resource);
    }

    public static void renderCenter(int x, int y, int drawWidth, int drawHeight,int drawTextureWidth, int drawTextureHeight, int width, int height, GuiGraphics poseStack, ResourceLocation resource, int textureStartX, int textureStartY, int textureWidth, int textureHeight) {
        int left = (width - drawWidth) / 2;
        int top = (height - drawHeight) / 2;
        render(drawWidth, drawHeight,drawTextureWidth,drawTextureHeight, left + x, top + y, poseStack, resource, textureStartX, textureStartY, textureWidth, textureHeight);
    }

    public static void renderCenterTop(int x, int y, int drawWidth, int drawHeight, int width, int top, GuiGraphics poseStack, ResourceLocation resource) {
        int left = (width - drawWidth) / 2;
        render(drawWidth, drawHeight, left + x, top + y, poseStack, resource);
    }


    public static void render(int drawWidth, int drawHeight, int left, int top, GuiGraphics poseStack, ResourceLocation resource) {
        TextureSize size = textureSize(resource);
        int textureWidth = size.width() > 0 ? size.width() : drawWidth;
        int textureHeight = size.height() > 0 ? size.height() : drawHeight;
        // Keep the full-image mapping explicit.  Most official AcademyCraft
        // GUI assets are 2x (352x374 -> 176x187), while several icons are
        // scaled to other logical sizes.  Reading the intrinsic dimensions
        // makes that contract clear and remains correct for resource packs
        // whose PNG resolution differs from the requested draw size.
        poseStack.blit(resource, left, top, drawWidth, drawHeight,
                0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    /** Clear intrinsic PNG sizes when resource packs are reloaded. */
    public static void clearTextureSizeCache() {
        TEXTURE_SIZES.clear();
    }

    /**
     * Returns the PNG's intrinsic dimensions, or {@code 0x0} when the
     * resource is unavailable/not a PNG.  Tutorial and machine renderers must
     * use this rather than treating their (usually half-size) destination as
     * the texture-sheet size, which samples only the upper-left portion.
     */
    public static TextureSize textureSize(ResourceLocation resource) {
        return TEXTURE_SIZES.computeIfAbsent(resource, RenderUtils::readTextureSize);
    }

    private static TextureSize readTextureSize(ResourceLocation resource) {
        var found = Minecraft.getInstance().getResourceManager().getResource(resource);
        if (found.isEmpty()) return new TextureSize(0, 0);
        // PNG stores width/height in the fixed IHDR header. Reading only the
        // first 24 bytes avoids decoding the image on the first render frame.
        try (DataInputStream input = new DataInputStream(found.get().open())) {
            if (input.readLong() != PNG_SIGNATURE || input.readInt() != 13
                    || input.readInt() != PNG_IHDR) {
                return new TextureSize(0, 0);
            }
            int width = input.readInt();
            int height = input.readInt();
            if (width <= 0 || height <= 0 || width > 16_384 || height > 16_384) {
                return new TextureSize(0, 0);
            }
            return new TextureSize(width, height);
        } catch (IOException ignored) {
            return new TextureSize(0, 0);
        }
    }

    public static void render(int drawWidth, int drawHeight,int drawTextureWidth, int drawTextureHeight, int left, int top, GuiGraphics poseStack, ResourceLocation resource, int textureStartX, int textureStartY, int textureWidth, int textureHeight) {
        // GuiGraphics.blit expects the U coordinate before V.  The old
        // compatibility wrapper accidentally swapped these two values, so
        // every animated vertical-strip effect sampled the wrong rectangle
        // (and looked like a shifted/blank texture in 1.21.1).
        poseStack.blit(resource, left, top, drawWidth, drawHeight, textureStartX, textureStartY, drawTextureWidth, drawTextureHeight, textureWidth, textureHeight);
    }

    public static void renderText(GuiGraphics stack, String text, int x, int y) {
        renderText(stack, text, x, y, Style.EMPTY);
    }

    public static void renderText(GuiGraphics stack, String text, int x, int y, Style style) {
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
        ClientTooltipComponent.create(FormattedCharSequence.forward(text,
                        style))
                .renderText(Minecraft.getInstance().font,
                        x,
                        y,
                        stack.pose().last().pose(),
                        source);
        source.endBatch();
    }


}
