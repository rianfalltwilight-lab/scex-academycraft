package com.mohistmc.academy.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** LambdaLib-style textured mote with bounded fade and no vanilla particle substitution. */
public final class AcademyTextureParticle extends TextureSheetParticle {
    private final float initialAlpha;
    private final int fadeInTicks;
    private final int fadeOutTicks;
    private AcademyTextureParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,
                                   SpriteSet sprites,int color,float minScale,float maxScale,int minLife,int maxLife,
                                   float minAlpha,float maxAlpha,int fadeInTicks,int fadeOutTicks){
        super(level,x,y,z,vx,vy,vz);this.xd=vx;this.yd=vy;this.zd=vz;this.friction=.92f;this.gravity=0;
        this.lifetime=minLife+random.nextInt(Math.max(1,maxLife-minLife+1));
        this.quadSize=minScale+random.nextFloat()*Math.max(0,maxScale-minScale);
        this.initialAlpha=minAlpha+random.nextFloat()*Math.max(0,maxAlpha-minAlpha);
        this.fadeInTicks=Math.max(0,fadeInTicks);this.fadeOutTicks=Math.max(0,fadeOutTicks);
        this.rCol=((color>>16)&255)/255f;this.gCol=((color>>8)&255)/255f;this.bCol=(color&255)/255f;
        this.alpha=initialAlpha;pickSprite(sprites);
    }
    @Override public void tick(){super.tick();if(!removed){
        float fadeIn=fadeInTicks==0?1:Math.min(1,(float)age/fadeInTicks);
        int fadeStart=lifetime-fadeOutTicks;
        float fadeOut=fadeOutTicks==0||age<=fadeStart?1:Math.max(0,(float)(lifetime-age)/fadeOutTicks);
        alpha=initialAlpha*Math.min(fadeIn,fadeOut);
    }}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}

    public static final class Provider implements ParticleProvider<SimpleParticleType>{
        private final SpriteSet sprites;private final int color;private final float minScale,maxScale,minAlpha,maxAlpha;
        private final int minLife,maxLife,fadeInTicks,fadeOutTicks;
        public Provider(SpriteSet sprites,int color,float minScale,float maxScale,int minLife,int maxLife,
                        float minAlpha,float maxAlpha,int fadeInTicks,int fadeOutTicks){
            this.sprites=sprites;this.color=color;this.minScale=minScale;this.maxScale=maxScale;
            this.minLife=minLife;this.maxLife=maxLife;this.minAlpha=minAlpha;this.maxAlpha=maxAlpha;
            this.fadeInTicks=fadeInTicks;this.fadeOutTicks=fadeOutTicks;
        }
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double vx,double vy,double vz){
            return new AcademyTextureParticle(level,x,y,z,vx,vy,vz,sprites,color,minScale,maxScale,
                    minLife,maxLife,minAlpha,maxAlpha,fadeInTicks,fadeOutTicks);
        }
    }
}
