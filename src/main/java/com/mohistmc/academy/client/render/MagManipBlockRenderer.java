package com.mohistmc.academy.client.render;

import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Rotating carried material plus the persistent thin surround arcs from 1.0.7. */
public final class MagManipBlockRenderer extends EntityRenderer<MagManipBlockEntity> {
    private final BlockRenderDispatcher blocks;

    public MagManipBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
        shadowRadius = .5f;
    }

    @Override
    public void render(MagManipBlockEntity entity, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partial;
        float yawSpeed = 1f + (entity.getId() & 31) / 31f * 2f;
        float pitchSpeed = 1f + ((entity.getId() >> 5) & 31) / 31f * 2f;

        pose.pushPose();
        pose.translate(0, .5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * yawSpeed));
        pose.mulPose(Axis.XP.rotationDegrees(age * pitchSpeed));
        pose.translate(-.5, -.5, -.5);
        blocks.renderSingleBlock(entity.blockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        pose.pushPose();
        Matrix4f matrix = pose.last().pose();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int seed = entity.getId();
        for (int arc = 0; arc < 7; arc++) {
            double base = age * .12 + arc * Math.PI * 2 / 7;
            Vec3 last = null;
            for (int i = 0; i <= 6; i++) {
                double t = i / 6d;
                double angle = base + t * .65;
                double jitter = Math.sin((seed + arc * 17 + i * 29) * 12.9898) * .08;
                Vec3 point = new Vec3(Math.cos(angle) * (.66 + jitter), .08 + t * .92,
                        Math.sin(angle) * (.66 + jitter));
                if (last != null) line(lines, matrix, last, point, .60f, .86f, 1f, .9f);
                last = point;
            }
        }
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }

    private static void line(VertexConsumer out, Matrix4f matrix, Vec3 from, Vec3 to,
                             float red, float green, float blue, float alpha) {
        out.addVertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .setColor(red, green, blue, alpha).setNormal(0, 1, 0);
        out.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .setColor(red, green, blue, alpha).setNormal(0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(MagManipBlockEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
    }
}
