package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** PlasmaBodyEffect plus the official tornado-ring column, without the glowstone-item fallback. */
public final class PlasmaOrbRenderer extends EntityRenderer<PlasmaOrbEntity> {
    private static final ResourceLocation TORNADO_RING = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/tornado_ring.png");

    public PlasmaOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(PlasmaOrbEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float startup = Mth.clamp(age / 20f, 0, 1);
        float pulse = 1 + .08f * Mth.sin(age * .25f);
        RenderType type = RenderType.entityTranslucentEmissive(TORNADO_RING);
        VertexConsumer vertices = buffers.getBuffer(type);

        // Layered camera-facing rings approximate the old shader-driven plasma body.
        for (int layer = 0; layer < 3; layer++) {
            poseStack.pushPose();
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * (layer % 2 == 0 ? 1.8f : -1.3f) + layer * 37));
            float scale = (2.8f + layer * .65f) * startup * pulse;
            poseStack.scale(scale, scale, scale);
            drawQuad(poseStack.last().pose(), vertices, 210 - layer * 45);
            poseStack.popPose();
        }

        if (!entity.isArmed()) renderTornado(entity, age, startup, poseStack, vertices);
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void renderTornado(PlasmaOrbEntity entity, float age, float startup,
                                      PoseStack poseStack, VertexConsumer vertices) {
        Vec3 start = entity.position();
        Vec3 end = start.add(0, -20, 0);
        HitResult hit = entity.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        double height = hit.getType() == HitResult.Type.MISS ? 20 : start.y - hit.getLocation().y;

        for (int index = 0; index < 12; index++) {
            float progress = index / 11f;
            poseStack.pushPose();
            poseStack.translate(0, -height * progress, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * (3.5f + progress * 2) + index * 29));
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            float radius = Mth.lerp(progress, 4.0f, .65f) * startup;
            poseStack.scale(radius, radius, radius);
            drawQuad(poseStack.last().pose(), vertices, (int) (115 * (1 - progress * .45f)));
            poseStack.popPose();
        }
    }

    private static void drawQuad(Matrix4f matrix, VertexConsumer vertices, int alpha) {
        vertices.addVertex(matrix, -.5f, -.5f, 0).setColor(190, 225, 255, alpha).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
        vertices.addVertex(matrix, .5f, -.5f, 0).setColor(190, 225, 255, alpha).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
        vertices.addVertex(matrix, .5f, .5f, 0).setColor(255, 255, 255, alpha).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
        vertices.addVertex(matrix, -.5f, .5f, 0).setColor(255, 255, 255, alpha).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
    }

    @Override public ResourceLocation getTextureLocation(PlasmaOrbEntity entity) { return TORNADO_RING; }
}
