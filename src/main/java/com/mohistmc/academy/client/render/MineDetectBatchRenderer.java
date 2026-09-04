package com.mohistmc.academy.client.render;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.ClientPacketBridge;
import com.mohistmc.academy.network.MineDetectResultPacket;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Final-1.12.2 MineDetect renderer. The official handler rebuilt one list and
 * submitted every ore in one tessellator batch; using client entities for the
 * 8400-entry upper bound adds avoidable entity-tick and dispatch overhead.
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MineDetectBatchRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/effects/mineview.png");
    private static final RenderType TYPE = RenderType.create(
            "academy_mine_highlight_batch", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1 << 20, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));
    private static final int[][] COLORS = {
            {115, 200, 227}, {161, 181, 188}, {87, 231, 248},
            {97, 204, 94}, {235, 109, 84}
    };

    private MineDetectBatchRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        MineDetectResultPacket snapshot = ClientPacketBridge.mineDetectSnapshot();
        if (snapshot.entries().isEmpty() || snapshot.range() <= 0) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(TYPE);
        Matrix4f matrix = pose.last().pose();
        double range = snapshot.range();
        for (MineDetectResultPacket.Entry entry : snapshot.entries()) {
            double dx = mc.player.getX() - entry.pos().getX();
            double dy = mc.player.getY() - entry.pos().getY();
            double dz = mc.player.getZ() - entry.pos().getZ();
            float alpha = (float) Math.clamp(.3 + (1 - Math.sqrt(dx*dx + dy*dy + dz*dz) / range * 2.2) * .7, 0, 1);
            if (alpha <= .01f) continue;
            int[] color = COLORS[Math.clamp(entry.harvestLevel(), 0, COLORS.length - 1)];
            box(out, matrix, entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(),
                    color[0] / 255f, color[1] / 255f, color[2] / 255f, alpha);
        }
        buffers.endBatch(TYPE);
        pose.popPose();
    }

    private static void box(VertexConsumer out, Matrix4f matrix, float x, float y, float z,
                            float r, float g, float b, float a) {
        float lo=.05f, hi=.95f;
        face(out,matrix,r,g,b,a, x+lo,y+lo,z+hi, x+hi,y+lo,z+hi, x+hi,y+lo,z+lo, x+lo,y+lo,z+lo);
        face(out,matrix,r,g,b,a, x+lo,y+hi,z+lo, x+hi,y+hi,z+lo, x+hi,y+hi,z+hi, x+lo,y+hi,z+hi);
        face(out,matrix,r,g,b,a, x+lo,y+hi,z+hi, x+hi,y+hi,z+hi, x+hi,y+lo,z+hi, x+lo,y+lo,z+hi);
        face(out,matrix,r,g,b,a, x+lo,y+lo,z+lo, x+hi,y+lo,z+lo, x+hi,y+hi,z+lo, x+lo,y+hi,z+lo);
        face(out,matrix,r,g,b,a, x+lo,y+lo,z+lo, x+lo,y+hi,z+lo, x+lo,y+hi,z+hi, x+lo,y+lo,z+hi);
        face(out,matrix,r,g,b,a, x+hi,y+lo,z+hi, x+hi,y+hi,z+hi, x+hi,y+hi,z+lo, x+hi,y+lo,z+lo);
    }

    private static void face(VertexConsumer out, Matrix4f matrix, float r,float g,float b,float a,
                             float... points) {
        for (int i=0;i<4;i++) {
            float u=(i==1||i==2)?1:0, v=i>=2?0:1;
            out.addVertex(matrix,points[i*3],points[i*3+1],points[i*3+2])
                    .setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0,1,0);
        }
    }
}
