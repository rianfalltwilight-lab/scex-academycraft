package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 熔毁光束渲染器 — 绿色能量射线，类似 Railgun 但颜色不同。
 */
public class MeltdownBeamRenderer extends EntityRenderer<MeltdownBeamEntity> {

    private static final ResourceLocation MAIN_TEXTURE = texture("mdray");
    private static final ResourceLocation LUCK_TEXTURE = texture("mdray_luck");
    private static final ResourceLocation EXPERT_TEXTURE = texture("mdray_expert");
    private static final ResourceLocation SMALL_TEXTURE = texture("mdray_small");

    private static ResourceLocation texture(String family) {
        return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
                "textures/effects/" + family + "/tile.png");
    }

    private static ResourceLocation textureFor(MeltdownBeamEntity entity) {
        return switch (entity.getVariant()) {
            case MeltdownBeamEntity.LUCK -> LUCK_TEXTURE;
            case MeltdownBeamEntity.EXPERT -> EXPERT_TEXTURE;
            case MeltdownBeamEntity.SMALL -> SMALL_TEXTURE;
            case MeltdownBeamEntity.BARRAGE_PRE -> SMALL_TEXTURE;
            default -> MAIN_TEXTURE;
        };
    }

    public MeltdownBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MeltdownBeamEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity.getBeamLength() <= 0) return;

        Vec3 dir = entity.getBeamDirection().normalize();
        float age = entity.tickCount + partialTick;
        float remaining = entity.getLifetime() - age;
        // Every 1.0.7 Meltdowner ray grew to full length over 200 ms. Alpha
        // stayed opaque until its variant-specific blend-out window.
        double length = entity.getBeamLength() * Mth.clamp(age / 4f, 0f, 1f);
        float blendOutTicks = entity.getVariant() == MeltdownBeamEntity.MAIN ? 14f : 8f;
        float alpha = Mth.clamp(remaining / blendOutTicks, 0f, 1f);
        float shrinkTicks = entity.getVariant() == MeltdownBeamEntity.SMALL
                || entity.getVariant() == MeltdownBeamEntity.BARRAGE_PRE ? 10f : 6f;
        float widthScale = Mth.clamp(remaining / shrinkTicks, 0f, 1f);

        Vec3 startPos = entity.getStartPos();
        poseStack.pushPose();
        poseStack.translate(startPos.x - entity.getX(), startPos.y - entity.getY(), startPos.z - entity.getZ());

        Matrix4f matrix = poseStack.last().pose();

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 toCamera = cameraPos.subtract(startPos).normalize();
        Vec3 billboardRight = dir.cross(toCamera).normalize();
        if (billboardRight.length() < 0.001) {
            billboardRight = dir.cross(new Vec3(0, 1, 0)).normalize();
            if (billboardRight.length() < 0.001) {
                billboardRight = dir.cross(new Vec3(1, 0, 0)).normalize();
            }
        }

        RenderType type = RenderType.entityTranslucentEmissive(textureFor(entity));
        VertexConsumer vc = buffer.getBuffer(type);

        LayerSpec spec = LayerSpec.forVariant(entity.getVariant());
        renderBillboardBeam(matrix, vc, dir, length, billboardRight,
                spec.glowWidth * widthScale, 1f, 1f, 1f, alpha * spec.glowAlpha, packedLight);
        renderBillboardBeam(matrix, vc, dir, length, billboardRight,
                spec.outerWidth * widthScale, spec.outerR, spec.outerG, spec.outerB,
                alpha * spec.outerA, packedLight);
        renderBillboardBeam(matrix, vc, dir, length, billboardRight,
                spec.innerWidth * widthScale, spec.innerR, spec.innerG, spec.innerB,
                alpha * spec.innerA, packedLight);

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }

    /** Exact widths/tints from the four 1.0.7 RendererRayComposite subclasses. */
    private record LayerSpec(float glowWidth, float glowAlpha,
                             float outerWidth, float outerR, float outerG, float outerB, float outerA,
                             float innerWidth, float innerR, float innerG, float innerB, float innerA) {
        private static float c(int value) { return value / 255f; }
        static LayerSpec forVariant(int variant) {
            return switch (variant) {
                case MeltdownBeamEntity.LUCK -> new LayerSpec(.45f, .6f,
                        .05f, c(205), c(166), c(232), c(50),
                        .04f, c(241), c(229), c(247), c(230));
                case MeltdownBeamEntity.EXPERT -> new LayerSpec(.5f, .5f,
                        .056f, c(106), c(242), c(106), c(50),
                        .045f, c(216), c(248), c(216), c(180));
                case MeltdownBeamEntity.SMALL -> new LayerSpec(.3f, .5f,
                        .045f, c(106), c(242), c(106), c(50),
                        .03f, c(216), c(248), c(216), c(230));
                case MeltdownBeamEntity.BARRAGE_PRE -> new LayerSpec(.4f, .5f,
                        .052f, c(106), c(242), c(106), c(50),
                        .045f, c(216), c(248), c(216), c(230));
                default -> new LayerSpec(1.5f, .8f,
                        .22f, c(106), c(242), c(106), c(50),
                        .17f, c(216), c(248), c(216), c(230));
            };
        }
    }

    private void renderBillboardBeam(Matrix4f matrix, VertexConsumer vc,
                                      Vec3 dir, double length, Vec3 right,
                                      float width, float r, float g, float b, float alpha, int light) {
        int segments = Math.max(2, (int) (length / 0.5));
        float bx = (float)(right.x * width), by = (float)(right.y * width), bz = (float)(right.z * width);

        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments, t1 = (float) (i + 1) / segments;
            float s0 = (float)(dir.x * length * t0), s1 = (float)(dir.y * length * t0), s2 = (float)(dir.z * length * t0);
            float e0 = (float)(dir.x * length * t1), e1 = (float)(dir.y * length * t1), e2 = (float)(dir.z * length * t1);

            vc.addVertex(matrix, s0 + bx, s1 + by, s2 + bz).setColor(r, g, b, alpha).setUv(t0, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(matrix, s0 - bx, s1 - by, s2 - bz).setColor(r, g, b, alpha).setUv(t0, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(matrix, e0 - bx, e1 - by, e2 - bz).setColor(r, g, b, alpha).setUv(t1, 1)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
            vc.addVertex(matrix, e0 + bx, e1 + by, e2 + bz).setColor(r, g, b, alpha).setUv(t1, 0)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MeltdownBeamEntity entity) {
        return textureFor(entity);
    }
}

