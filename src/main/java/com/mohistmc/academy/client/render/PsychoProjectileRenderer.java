package com.mohistmc.academy.client.render;

import com.mohistmc.academy.world.entity.PsychoProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;

/** ExtraAcC's spinning one-block stone and crossed grey needle, using modern render buffers. */
public final class PsychoProjectileRenderer extends EntityRenderer<PsychoProjectileEntity> {
    private final BlockRenderDispatcher blocks;
    public PsychoProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        blocks = context.getBlockRenderDispatcher();
    }

    @Override public void render(PsychoProjectileEntity entity, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()) - 90));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
        if (entity.kind() == PsychoProjectileEntity.Kind.STONE) {
            float spin = (entity.tickCount + partialTick) * 6;
            pose.mulPose(Axis.ZP.rotationDegrees(spin)); pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.translate(-.5, -.5, -.5);
            blocks.renderSingleBlock(Blocks.COBBLESTONE.defaultBlockState(), pose, buffers, light,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(45));
            quad(pose, buffers.getBuffer(RenderType.debugQuads()), .1F);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            quad(pose, buffers.getBuffer(RenderType.debugQuads()), .2F);
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, float halfHeight) {
        for (int i = 0; i < 4; i++) {
            float x = i < 2 ? -.25F : .25F;
            float y = i == 0 || i == 3 ? halfHeight : -halfHeight;
            vertices.addVertex(pose.last(), x, y, 0).setColor(.5F, .5F, .5F, .9F);
        }
    }

    @Override public ResourceLocation getTextureLocation(PsychoProjectileEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
