package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Camera-facing animated renderer backed by the official 1.0.7 mdball sprite frames. */
public final class MdBallRenderer extends EntityRenderer<MdBallEntity> {
    private static final ResourceLocation[] NORMAL = frames("mdball");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,
            "textures/effects/mdball/glow.png");
    private static ResourceLocation[] frames(String folder) {
        int n = 5; ResourceLocation[] out = new ResourceLocation[n];
        for (int i=0;i<n;i++) out[i]=ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/effects/"+folder+"/"+i+".png"); return out;
    }
    public MdBallRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    @Override public void render(MdBallEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float age=e.tickCount+partial;
        float alpha=alpha(e,age),size=size(e,age),wiggle=e.clientAlphaWiggle();
        if(alpha<=0)return;
        pose.pushPose();
        pose.translate(e.clientRenderOffsetX(partial),e.clientRenderOffsetY(partial),e.clientRenderOffsetZ(partial));
        pose.mulPose(entityRenderDispatcher.cameraOrientation()); pose.mulPose(Axis.YP.rotationDegrees(180));
        renderQuad(pose,buffers,GLOW,.7f*size,alpha*(.3f+wiggle*.7f));
        renderQuad(pose,buffers,getTextureLocation(e),.5f*size,alpha*(.8f+wiggle*.2f));
        pose.popPose(); super.render(e,yaw,partial,pose,buffers,light);
    }

    private static float alpha(MdBallEntity e,float age){
        float remaining=e.lifetime()-age;
        if(remaining<3)return Math.clamp(remaining/3f,0f,1f);
        if(remaining<8)return .6f+(8f-remaining)/5f*.4f;
        if(age<6)return age/6f*.6f;
        return .6f;
    }
    private static float size(MdBallEntity e,float age){
        float remaining=e.lifetime()-age;
        if(remaining<2)return Math.clamp(remaining/2f,0f,1f)*1.5f;
        if(remaining<6)return 1f+(6f-remaining)/4f*.5f;
        return 1f;
    }
    private static void renderQuad(PoseStack pose,MultiBufferSource buffers,ResourceLocation texture,float size,float alpha){
        pose.pushPose();pose.scale(size,size,size);Matrix4f m=pose.last().pose();
        int a=Math.clamp(Math.round(alpha*255),0,255);
        var vc=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture));
        vc.addVertex(m,-.5f,-.5f,0).setColor(255,255,255,a).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,1,0);
        vc.addVertex(m,.5f,-.5f,0).setColor(255,255,255,a).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,1,0);
        vc.addVertex(m,.5f,.5f,0).setColor(255,255,255,a).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,1,0);
        vc.addVertex(m,-.5f,.5f,0).setColor(255,255,255,a).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,1,0);
        pose.popPose();
    }
    @Override public ResourceLocation getTextureLocation(MdBallEntity e){return NORMAL[e.clientTexture()%NORMAL.length];}
}
