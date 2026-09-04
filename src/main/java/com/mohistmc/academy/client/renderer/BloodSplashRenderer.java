package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.BloodSplashEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Official 1.0.7 blood_splash/0..9 billboard sequence. */
public final class BloodSplashRenderer extends EntityRenderer<BloodSplashEntity> {
    private static final ResourceLocation[] FRAMES = new ResourceLocation[BloodSplashEntity.FRAME_COUNT];
    static {
        for (int i = 0; i < FRAMES.length; i++) FRAMES[i] = ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MODID, "textures/effects/blood_splash/" + i + ".png");
    }

    public BloodSplashRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(BloodSplashEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float size = entity.scale();
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(size, size, size);
        Matrix4f matrix = pose.last().pose();
        var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(entity)));
        vertex(out, matrix, -.5f, -.5f, 0, 1);
        vertex(out, matrix,  .5f, -.5f, 1, 1);
        vertex(out, matrix,  .5f,  .5f, 1, 0);
        vertex(out, matrix, -.5f,  .5f, 0, 0);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out, Matrix4f matrix,
                               float x, float y, float u, float v) {
        out.addVertex(matrix, x, y, 0).setColor(213, 29, 29, 200).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(BloodSplashEntity entity) {
        return FRAMES[Math.clamp(entity.tickCount, 0, FRAMES.length - 1)];
    }
}
