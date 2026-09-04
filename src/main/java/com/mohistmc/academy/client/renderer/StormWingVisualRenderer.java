package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.StormWingVisualEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Four tilted tornado columns matching StormWingEffect's 1.0.7 placement. */
public final class StormWingVisualRenderer extends EntityRenderer<StormWingVisualEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/tornado_ring.png");
    private static final double[][] TRANSFORMS = {
            {-.1, -.3, .1, 45, 45}, {.1, -.3, .1, -45, -45},
            {-.1, -.5, -.1, -45, 45}, {.1, -.5, -.1, 45, -45}
    };

    public StormWingVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override public void render(StormWingVisualEntity entity, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float alpha = entity.alpha(partialTick);
        if (alpha <= .001f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot() * .2f - 70));
        pose.translate(0, .2, -.5);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        float age = entity.tickCount + partialTick;
        for (int index = 0; index < TRANSFORMS.length; index++) {
            double[] transform = TRANSFORMS[index];
            pose.pushPose();
            pose.translate(transform[0], transform[1], transform[2]);
            pose.mulPose(Axis.YP.rotationDegrees((float) transform[3]));
            pose.mulPose(Axis.ZP.rotationDegrees((float) transform[4]));
            tornado(vertices, pose.last().pose(), age, index, alpha);
            pose.popPose();
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void tornado(VertexConsumer vertices, Matrix4f matrix, float age, int seed, float alpha) {
        final int rings = 40, segments = 20;
        for (int ring = 1; ring <= rings; ring++) {
            float ny = ring / (float) rings;
            float y = ny * 2f;
            float width = .09f;
            float wobbleX = (float) Math.sin(age * .08 + ring * .43 + seed * 1.7) * (.03f + ny * ny * .35f);
            float wobbleZ = (float) Math.cos(age * .07 + ring * .37 + seed * 2.1) * (.03f + ny * ny * .35f);
            float radius = (.08f + .28f * ny * ny)
                    * (.9f + .16f * (float) Math.sin(age * .04 + ring * .31 + seed));
            float rotation = age * .004f * (1 + .5f * ny) + seed * .7f;
            for (int segment = 0; segment < segments; segment++) {
                float a0 = (float) (Math.PI * 2 * segment / segments + rotation);
                float a1 = (float) (Math.PI * 2 * (segment + 1) / segments + rotation);
                float u0 = segment / (float) segments;
                float u1 = (segment + 1) / (float) segments;
                vertex(vertices, matrix, wobbleX + Math.sin(a0) * radius, y + width * .5f,
                        wobbleZ + Math.cos(a0) * radius, u0, 0, alpha);
                vertex(vertices, matrix, wobbleX + Math.sin(a0) * radius, y - width * .5f,
                        wobbleZ + Math.cos(a0) * radius, u0, 1, alpha);
                vertex(vertices, matrix, wobbleX + Math.sin(a1) * radius, y - width * .5f,
                        wobbleZ + Math.cos(a1) * radius, u1, 1, alpha);
                vertex(vertices, matrix, wobbleX + Math.sin(a1) * radius, y + width * .5f,
                        wobbleZ + Math.cos(a1) * radius, u1, 0, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, double x, double y, double z,
                               float u, float v, float alpha) {
        out.addVertex(matrix, (float) x, (float) y, (float) z).setColor(1f, 1f, 1f, alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0, 0xF0).setNormal(0, 1, 0);
    }

    @Override public ResourceLocation getTextureLocation(StormWingVisualEntity entity) { return TEXTURE; }
}
