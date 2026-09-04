package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.MeltdownBarrageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** One render batch reproducing EntityMdRayBarrage's 25..30 randomized sub-rays. */
public final class MeltdownBarrageRenderer extends EntityRenderer<MeltdownBarrageEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/mdray_small/tile.png");

    public MeltdownBarrageRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MeltdownBarrageEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float alpha = entity.alpha();
        float width = entity.width();
        double length = MeltdownBarrageEntity.RAY_LENGTH * entity.lengthFactor();
        if (alpha <= 0 || width <= 0 || length <= 0) return;

        RandomSource random = RandomSource.create(entity.barrageSeed());
        float range = 50.0F + random.nextFloat() * 10.0F;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 origin = entity.position();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        for (int i = 0; i < entity.rayCount(); i++) {
            float yawOffset = (random.nextFloat() * 2.0F - 1.0F) * range;
            float pitchOffset = (random.nextFloat() * 2.0F - 1.0F) * range * 0.5F;
            Vec3 direction = direction(entity.barrageYaw() + yawOffset, entity.barragePitch() + pitchOffset);
            Vec3 right = direction.cross(camera.subtract(origin).normalize()).normalize();
            if (right.lengthSqr() < 1.0E-6D) right = direction.cross(new Vec3(0, 1, 0)).normalize();
            ray(matrix, vertices, direction, right, length, 0.30F * width,
                    0.42F, 0.95F, 0.42F, 0.50F * alpha, packedLight);
            ray(matrix, vertices, direction, right, length, 0.045F * width,
                    0.42F, 0.95F, 0.42F, 0.20F * alpha, packedLight);
            ray(matrix, vertices, direction, right, length, 0.030F * width,
                    0.85F, 0.97F, 0.85F, 0.90F * alpha, packedLight);
        }
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    private static Vec3 direction(float yaw, float pitch) {
        float yawRad = -yaw * Mth.DEG_TO_RAD - Mth.PI;
        float pitchRad = -pitch * Mth.DEG_TO_RAD;
        float horizontal = -Mth.cos(pitchRad);
        return new Vec3(Mth.sin(yawRad) * horizontal, Mth.sin(pitchRad),
                Mth.cos(yawRad) * horizontal).normalize();
    }

    private static void ray(Matrix4f matrix, VertexConsumer vertices, Vec3 direction, Vec3 right,
                            double length, float width, float red, float green, float blue,
                            float alpha, int light) {
        float rx = (float) right.x * width;
        float ry = (float) right.y * width;
        float rz = (float) right.z * width;
        float ex = (float) (direction.x * length);
        float ey = (float) (direction.y * length);
        float ez = (float) (direction.z * length);
        float tile = (float) length / 2.0F;
        vertices.addVertex(matrix, rx, ry, rz).setColor(red, green, blue, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vertices.addVertex(matrix, -rx, -ry, -rz).setColor(red, green, blue, alpha).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vertices.addVertex(matrix, ex - rx, ey - ry, ez - rz).setColor(red, green, blue, alpha).setUv(tile, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        vertices.addVertex(matrix, ex + rx, ey + ry, ez + rz).setColor(red, green, blue, alpha).setUv(tile, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    @Override public ResourceLocation getTextureLocation(MeltdownBarrageEntity entity) { return TEXTURE; }
}
