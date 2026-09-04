package com.mohistmc.academy.client.entity;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.entity.CoinEntity;
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
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class CoinRenderer extends EntityRenderer<CoinEntity> {
    private static final ResourceLocation COIN_FRONT =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/item/coin_front.png");
    private static final ResourceLocation COIN_BACK =
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "textures/item/coin_back.png");

    public CoinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CoinEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        if (!entity.hasCustomName()) {
            // 1.0.7 rotates the thin coin around a randomized three-dimensional
            // axis.  The entity keeps all three deterministic angles so the
            // official front and back genuinely alternate while it is airborne.
            float rotationY = entity.getRotationY() + partialTicks * entity.getSpinSpeed();
            float rotationX = entity.getRotationX() + partialTicks * entity.getSpinSpeed() * 0.7F;
            float rotationZ = entity.getRotationZ() + partialTicks * entity.getSpinSpeed() * 0.3F;
            poseStack.mulPose(Axis.XP.rotationDegrees(rotationX));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotationZ));
        } else if (entity.isReturning()) {
            // Face the player when returning and has custom name
            Entity cameraEntity = entity.getThrower();
            if (cameraEntity != null) {
                double dx = cameraEntity.getX() - entity.getX();
                double dz = cameraEntity.getZ() - entity.getZ();
                float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / Math.PI)) - 90.0F;
                poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
            }
        }

        // 如果正在返回，添加一些视觉效果
        if (entity.isReturning()) {
            float scale = 1.0F + Mth.sin((entity.tickCount + partialTicks) * 0.5F) * 0.1F;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.scale(0.30F, 0.30F, 0.30F);
        drawFace(bufferSource.getBuffer(RenderType.entityCutoutNoCull(COIN_FRONT)),
                poseStack.last().pose(), 0.025F, false, packedLight);
        drawFace(bufferSource.getBuffer(RenderType.entityCutoutNoCull(COIN_BACK)),
                poseStack.last().pose(), -0.025F, true, packedLight);

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CoinEntity entity) {
        return COIN_FRONT;
    }

    private static void drawFace(VertexConsumer out, Matrix4f pose, float z,
                                 boolean reverse, int packedLight) {
        if (reverse) {
            vertex(out, pose, -0.5F, -0.5F, z, 1, 1, packedLight, -1);
            vertex(out, pose, -0.5F,  0.5F, z, 1, 0, packedLight, -1);
            vertex(out, pose,  0.5F,  0.5F, z, 0, 0, packedLight, -1);
            vertex(out, pose,  0.5F, -0.5F, z, 0, 1, packedLight, -1);
        } else {
            vertex(out, pose, -0.5F, -0.5F, z, 0, 1, packedLight, 1);
            vertex(out, pose,  0.5F, -0.5F, z, 1, 1, packedLight, 1);
            vertex(out, pose,  0.5F,  0.5F, z, 1, 0, packedLight, 1);
            vertex(out, pose, -0.5F,  0.5F, z, 0, 0, packedLight, 1);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
                               float u, float v, int packedLight, float normalZ) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight)
                .setNormal(0, 0, normalZ);
    }
}
