package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.ShieldEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Single rotating billboard used by RenderMdShield in 1.0.7. */
public final class ShieldEffectRenderer extends EntityRenderer<ShieldEffectEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/mdshield.png");

    public ShieldEffectRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }

    @Override public void render(ShieldEffectEntity entity, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float age = entity.tickCount + partialTick;
        float size = ShieldEffectEntity.SIZE * (.2f + .8f * Math.min(age / 15f, 1f));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-entity.shieldYaw()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.shieldPitch()));
        pose.mulPose(Axis.ZP.rotationDegrees(spin(age)));
        pose.scale(size, size, 1);
        Matrix4f matrix = pose.last().pose();
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        vertices.addVertex(matrix,-.5f,-.5f,0).setColor(255,255,255,255).setUv(0,1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0,0,1);
        vertices.addVertex(matrix,.5f,-.5f,0).setColor(255,255,255,255).setUv(1,1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0,0,1);
        vertices.addVertex(matrix,.5f,.5f,0).setColor(255,255,255,255).setUv(1,0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0,0,1);
        vertices.addVertex(matrix,-.5f,.5f,0).setColor(255,255,255,255).setUv(0,0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0,0,1);
        pose.popPose();
        super.render(entity,yaw,partialTick,pose,buffers,packedLight);
    }

    private static float spin(float age) {
        if (age <= 30f) return (40f * age + age * age) % 360f;
        return (2100f + (age - 30f) * 100f) % 360f;
    }

    @Override public ResourceLocation getTextureLocation(ShieldEffectEntity entity) { return TEXTURE; }
}
