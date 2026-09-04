package com.mohistmc.academy.client.render;

import com.mohistmc.academy.world.entity.OreHighlightEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Client-only renderer kept out of the common entity class for dedicated-server safety. */
public final class OreHighlightRenderer extends EntityRenderer<OreHighlightEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mineview.png");
    private static final RenderType TYPE = RenderType.create(
            "academy_mine_highlight", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            false, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));
    private static final int[][] COLORS = {
            {115, 200, 227}, {161, 181, 188}, {87, 231, 248}, {97, 204, 94}, {235, 109, 84}
    };

    public OreHighlightRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(OreHighlightEntity entity, float yaw, float partialTick,
                       PoseStack stack, MultiBufferSource buffers, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        float alpha = .8f;
        if (player != null) {
            double distance = entity.distanceTo(player);
            alpha = (float) Math.clamp(.3 + (1 - distance / entity.getRange() * 2.2) * .7, 0, 1);
        }
        if (alpha <= .01f) return;
        int[] color = COLORS[Math.clamp(entity.getHarvestLevel(), 0, COLORS.length - 1)];
        stack.pushPose();
        PoseStack.Pose pose = stack.last();
        VertexConsumer vc = buffers.getBuffer(TYPE);
        float r = color[0] / 255f, g = color[1] / 255f, b = color[2] / 255f;
        float lo=.05f, hi=.95f;
        face(vc, pose, r,g,b,alpha, lo,lo,hi, hi,lo,hi, hi,lo,lo, lo,lo,lo);
        face(vc, pose, r,g,b,alpha, lo,hi,lo, hi,hi,lo, hi,hi,hi, lo,hi,hi);
        face(vc, pose, r,g,b,alpha, lo,hi,hi, hi,hi,hi, hi,lo,hi, lo,lo,hi);
        face(vc, pose, r,g,b,alpha, lo,lo,lo, hi,lo,lo, hi,hi,lo, lo,hi,lo);
        face(vc, pose, r,g,b,alpha, lo,lo,lo, lo,hi,lo, lo,hi,hi, lo,lo,hi);
        face(vc, pose, r,g,b,alpha, hi,lo,hi, hi,hi,hi, hi,hi,lo, hi,lo,lo);
        stack.popPose();
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose, float r,float g,float b,float a,
                             float... p) {
        for (int i = 0; i < 4; i++) {
            float u = (i == 1 || i == 2) ? 1 : 0;
            float v = i >= 2 ? 0 : 1;
            vc.addVertex(pose, p[i*3], p[i*3+1], p[i*3+2]).setColor(r,g,b,a).setUv(u,v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, 0, 1, 0);
        }
    }

    @Override public ResourceLocation getTextureLocation(OreHighlightEntity entity) { return TEXTURE; }
}
