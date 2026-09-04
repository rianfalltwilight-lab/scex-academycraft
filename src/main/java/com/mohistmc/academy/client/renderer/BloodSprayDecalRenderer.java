package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.BloodSprayDecalEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Renders the official three-frame ground/wall blood spray sequence. */
public final class BloodSprayDecalRenderer extends EntityRenderer<BloodSprayDecalEntity>{
    public BloodSprayDecalRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=0;}
    public void render(BloodSprayDecalEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float alpha=Math.min(1f,(BloodSprayDecalEntity.LIFE_TICKS-e.tickCount-partial)/10f);if(alpha<=0)return;
        Direction face=e.face();pose.pushPose();orient(pose,face);
        ResourceLocation texture=getTextureLocation(e);VertexConsumer vc=buffers.getBuffer(RenderType.entityTranslucent(texture));
        Matrix4f m=pose.last().pose();float h=.56f;
        vertex(vc,m,-h,-h,0,1,alpha);vertex(vc,m,-h,h,0,0,alpha);vertex(vc,m,h,h,1,0,alpha);vertex(vc,m,h,-h,1,1,alpha);
        pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
    }
    private static void orient(PoseStack p,Direction face){
        if(face==Direction.UP)p.mulPose(Axis.XP.rotationDegrees(90));
        else if(face==Direction.DOWN)p.mulPose(Axis.XP.rotationDegrees(-90));
        else {p.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));}
    }
    private static void vertex(VertexConsumer v,Matrix4f m,float x,float y,float u,float w,float a){v.addVertex(m,x,y,0).setColor(1f,1f,1f,a).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,0,1);}
    public ResourceLocation getTextureLocation(BloodSprayDecalEntity e){String surface=e.face().getAxis()==Direction.Axis.Y?"grnd":"wall";return ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"textures/effects/blood_spray/"+surface+"/"+e.variant()+".png");}
}
