package com.mohistmc.academy.client.renderer;

import com.mohistmc.academy.entity.LegacyFieldEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Procedural emissive line geometry replacing the temporary vanilla-particle fallback. */
public class LegacyFieldEffectRenderer extends EntityRenderer<LegacyFieldEffectEntity> {
    private static final ResourceLocation NONE=ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation GLOW_CIRCLE=ResourceLocation.fromNamespaceAndPath(
            "academy","textures/effects/glow_circle.png");
    public LegacyFieldEffectRenderer(EntityRendererProvider.Context c){super(c);}
    @Override public void render(LegacyFieldEffectEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float a=e.alpha(partial);if(a<=.01f)return;int c=e.color();float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
        if(e.kind()==LegacyFieldEffectEntity.TEXTURED_WAVE){
            texturedWave(buffers.getBuffer(RenderType.entityTranslucentEmissive(GLOW_CIRCLE)),
                    pose.last().pose(),e,partial);super.render(e,yaw,partial,pose,buffers,light);return;
        }
        VertexConsumer vc=buffers.getBuffer(RenderType.lines());Matrix4f m=pose.last().pose();
        switch(e.kind()){
            case LegacyFieldEffectEntity.ARC -> arcs(vc,m,e.size(),r,g,b,a,e.getId());
            case LegacyFieldEffectEntity.SHOCKWAVE -> rings(vc,m,e.size(),3,r,g,b,a,false);
            case LegacyFieldEffectEntity.WAVE -> wave(vc,m,e.direction(),e.size(),r,g,b,a);
            case LegacyFieldEffectEntity.WIND -> rings(vc,m,e.size(),4,r,g,b,a,true);
            case LegacyFieldEffectEntity.PSYCHO -> {rings(vc,m,e.size(),3,r,g,b,a,false);vertical(vc,m,e.size(),r,g,b,a);}
            case LegacyFieldEffectEntity.TETHER -> tether(vc,m,e.direction(),e.size(),r,g,b,a,e.getId());
            case LegacyFieldEffectEntity.JET -> jet(vc,m,e.direction(),e.size(),r,g,b,a);
            case LegacyFieldEffectEntity.INTENSIFY -> intensify(vc,m,e.tickCount+partial,r,g,b,e.getId());
        }super.render(e,yaw,partial,pose,buffers,light);
    }
    private static void line(VertexConsumer v,Matrix4f m,Vec3 p,Vec3 q,float r,float g,float b,float a){v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(r,g,b,a).setNormal(0,1,0);v.addVertex(m,(float)q.x,(float)q.y,(float)q.z).setColor(r,g,b,a).setNormal(0,1,0);}
    private static void arcs(VertexConsumer v,Matrix4f m,float s,float r,float g,float b,float a,int seed){for(int arm=0;arm<7;arm++){Vec3 last=Vec3.ZERO;double angle=arm*Math.PI*2/7+(seed%17)*.03;for(int i=1;i<=7;i++){double t=i/7d,j=Math.sin((seed+arm*13+i*7)*12.9898)*.11*s;Vec3 q=new Vec3(Math.cos(angle)*s*t+j,(i%2==0?j:-j)+.15,Math.sin(angle)*s*t-j);line(v,m,last,q,r,g,b,a);last=q;}}}
    private static void rings(VertexConsumer v,Matrix4f m,float s,int n,float r,float g,float b,float a,boolean spiral){for(int ring=0;ring<n;ring++){double radius=s*(.45+.18*ring);Vec3 last=null;for(int i=0;i<=32;i++){double x=i*Math.PI*2/32;Vec3 q=new Vec3(Math.cos(x)*radius,spiral?(i/32d-.5)*s*.45:ring*.035,Math.sin(x)*radius);if(last!=null)line(v,m,last,q,r,g,b,a);last=q;}}}
    private static void vertical(VertexConsumer v,Matrix4f m,float s,float r,float g,float b,float a){for(int axis=0;axis<2;axis++){Vec3 last=null;for(int i=0;i<=32;i++){double x=i*Math.PI*2/32;Vec3 q=axis==0?new Vec3(Math.cos(x)*s,Math.sin(x)*s,0):new Vec3(0,Math.sin(x)*s,Math.cos(x)*s);if(last!=null)line(v,m,last,q,r,g,b,a);last=q;}}}
    private static void wave(VertexConsumer v,Matrix4f m,Vec3 f,float s,float r,float g,float b,float a){Vec3 flat=new Vec3(f.x,0,f.z);if(flat.lengthSqr()<1e-6)flat=new Vec3(0,0,1);flat=flat.normalize();Vec3 side=new Vec3(-flat.z,0,flat.x);for(int band=1;band<=3;band++){Vec3 last=null;for(int i=-8;i<=8;i++){double lateral=i/8d*band*.85,depth=band*s/3d*(1-Math.abs(i)/32d);Vec3 q=flat.scale(depth).add(side.scale(lateral));if(last!=null)line(v,m,last,q,r,g,b,a);last=q;}}}
    private static void tether(VertexConsumer v,Matrix4f m,Vec3 direction,float length,float r,float g,float b,float a,int seed){Vec3 f=direction.normalize(),side=f.cross(Math.abs(f.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),up=side.cross(f).normalize();for(int strand=0;strand<2;strand++){Vec3 last=Vec3.ZERO;for(int i=1;i<=18;i++){double t=i/18d,w=Math.sin((seed+strand*31+i*17)*12.9898)*.035*(1-Math.abs(t-.5));Vec3 q=f.scale(length*t).add(side.scale(w)).add(up.scale(Math.sin(t*Math.PI*8+strand)*.025));line(v,m,last,q,r,g,b,a);last=q;}}}
    private static void jet(VertexConsumer v,Matrix4f m,Vec3 direction,float length,float r,float g,float b,float a){Vec3 f=direction.normalize(),side=f.cross(Math.abs(f.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize(),up=side.cross(f).normalize();for(int rail=0;rail<6;rail++){double angle=rail*Math.PI/3;Vec3 rim=side.scale(Math.cos(angle)*.25).add(up.scale(Math.sin(angle)*.25));line(v,m,rim,f.scale(-length),r,g,b,a);if(rail>0){double prev=(rail-1)*Math.PI/3;Vec3 old=side.scale(Math.cos(prev)*.25).add(up.scale(Math.sin(prev)*.25));line(v,m,old,rim,r,g,b,a);}}}
    private static void texturedWave(VertexConsumer v,Matrix4f m,LegacyFieldEffectEntity e,float partial){
        float age=e.tickCount+partial,global=waveAlpha(age/15f);Vec3 forward=e.direction().normalize();
        Vec3 axis=Math.abs(forward.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0);
        Vec3 side=forward.cross(axis).normalize(),up=side.cross(forward).normalize();
        for(int ring=0;ring<e.count();ring++){
            int hash=e.getId()*31+ring*17;float delay=ring*2+Math.floorMod(hash,2)-1;
            float life=8+Math.floorMod(hash>>>2,4);float alpha=Math.min(global,waveAlpha((age-delay)/life));
            if(alpha<=.001f)continue;float randomScale=.8f+Math.floorMod(hash>>>5,401)/1000f;
            float scale=e.size()*randomScale*waveScale(Math.min(1.62f,age/20f));
            Vec3 center=forward.scale(ring*1.5+(Math.floorMod(hash>>>9,601)-300)/1000d+age/40d);
            waveVertex(v,m,center.subtract(side.scale(scale*.5)).subtract(up.scale(scale*.5)),0,1,alpha);
            waveVertex(v,m,center.subtract(side.scale(scale*.5)).add(up.scale(scale*.5)),0,0,alpha);
            waveVertex(v,m,center.add(side.scale(scale*.5)).add(up.scale(scale*.5)),1,0,alpha);
            waveVertex(v,m,center.add(side.scale(scale*.5)).subtract(up.scale(scale*.5)),1,1,alpha);
        }
    }
    private static float waveAlpha(float t){if(t<=0||t>=1)return 0;if(t<.2f)return t/.2f;if(t<=.8f)return 1;return(1-t)/.2f;}
    private static float waveScale(float t){if(t<=.2f)return .4f+t*2;return .8f+(t-.2f)*(1.5f-.8f)/(2.5f-.2f);}
    private static void waveVertex(VertexConsumer v,Matrix4f m,Vec3 p,float u,float vv,float a){v.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setColor(1f,1f,1f,a*.7f).setUv(u,vv).setOverlay(OverlayTexture.NO_OVERLAY).setUv2(0xF0,0xF0).setNormal(0,0,1);}
    /** Seven scheduled 3-tick arc clusters descending around the caster, as in EntityIntensifyEffect. */
    private static void intensify(VertexConsumer v,Matrix4f m,float age,float r,float g,float b,int seed){
        double[] heights={2,1.8,1.5,1,.5,0,-.1};int[] starts={0,1,3,4,6,7,8};
        for(int layer=0;layer<heights.length;layer++){
            float local=age-starts[layer];if(local<0||local>=3)continue;float alpha=1-local/3f;
            for(int arm=0;arm<4;arm++){
                double theta=(seed%23)*.11+arm*Math.PI*.5+layer*.37;Vec3 last=new Vec3(Math.cos(theta)*.52,heights[layer],Math.sin(theta)*.52);
                for(int i=1;i<=4;i++){double a=theta+(i-2.5)*.15,j=Math.sin((seed+layer*31+arm*13+i*7)*12.9898)*.08;Vec3 q=new Vec3(Math.cos(a)*(.52+j),heights[layer]+j,Math.sin(a)*(.52+j));line(v,m,last,q,r,g,b,alpha);last=q;}
            }
        }
    }
    @Override public ResourceLocation getTextureLocation(LegacyFieldEffectEntity e){return NONE;}
}
