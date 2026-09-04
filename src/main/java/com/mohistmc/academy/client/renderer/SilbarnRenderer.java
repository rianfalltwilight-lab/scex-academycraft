package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.entity.EntitySilbarn;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Exact thin cuboid and official model texture used by the 1.0.7 Silbarn projectile. */
public final class SilbarnRenderer extends EntityRenderer<EntitySilbarn> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/entity/silbarn.png");
    private static final float[][] V = {
            {-0.165897F,-0.004977F, 0.282025F},{ 0.165897F,-0.004977F, 0.282025F},
            {-0.165897F, 0.004977F, 0.282025F},{ 0.165897F, 0.004977F, 0.282025F},
            {-0.165897F, 0.004977F,-0.282025F},{ 0.165897F, 0.004977F,-0.282025F},
            {-0.165897F,-0.004977F,-0.282025F},{ 0.165897F,-0.004977F,-0.282025F}};
    private static final int[][] F = {{0,1,3,2},{2,3,5,4},{4,5,7,6},{6,7,1,0},{1,7,5,3},{6,0,2,4}};
    private static final float[][][] UV = {
            {{.636212F,.014908F},{.636212F,.590182F},{.618954F,.590182F},{.618954F,.014908F}},
            {{.023659F,.014759F},{.598933F,.014759F},{.598933F,.992724F},{.023659F,.992724F}},
            {{.636253F,.014952F},{.636253F,.590226F},{.618994F,.590226F},{.618994F,.014952F}},
            {{.023614F,.014708F},{.598887F,.014708F},{.598887F,.992673F},{.023614F,.992673F}},
            {{.659902F,.588340F},{.659902F,.013066F},{.670054F,.013066F},{.670054F,.588340F}},
            {{.670054F,.013079F},{.670054F,.588352F},{.659903F,.588352F},{.659903F,.013079F}}};
    private static final float[][] N = {{0,0,1},{0,1,0},{0,0,-1},{0,-1,0},{1,0,0},{-1,0,0}};

    public SilbarnRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }

    @Override public void render(EntitySilbarn entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffer, int light) {
        if (entity.isHit()) return;
        pose.pushPose();
        int seed = entity.getId() * 0x9E3779B9;
        float ax = ((seed >>> 1) & 255) / 127.5F - 1.0F;
        float ay = ((seed >>> 9) & 255) / 127.5F - 1.0F;
        float az = ((seed >>> 17) & 255) / 127.5F - 1.0F;
        float norm = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (norm < 0.01F) { ax = 0; ay = 1; az = 0; } else { ax /= norm; ay /= norm; az /= norm; }
        pose.mulPose(new Quaternionf().rotationAxis((entity.tickCount + partialTick) * 0.02617994F, ax, ay, az));
        pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-entity.getYRot())));
        pose.mulPose(new Quaternionf().rotationX((float) Math.PI / 2.0F));
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        for (int face = 0; face < F.length; face++) {
            for (int corner = 0; corner < 4; corner++) {
                float[] vertex = V[F[face][corner]];
                vertices.addVertex(matrix, vertex[0], vertex[1], vertex[2]).setColor(255,255,255,255)
                        .setUv(UV[face][corner][0], UV[face][corner][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        // `light` is already the packed block/sky value. Passing it to the
                        // two-coordinate setUv2 overload made the textured faces render
                        // almost black on a real client.
                        .setLight(light).setNormal(pose.last(), N[face][0], N[face][1], N[face][2]);
            }
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    @Override public ResourceLocation getTextureLocation(EntitySilbarn entity) { return TEXTURE; }
}
