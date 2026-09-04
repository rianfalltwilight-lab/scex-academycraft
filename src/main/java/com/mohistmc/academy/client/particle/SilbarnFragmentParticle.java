package com.mohistmc.academy.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** The dedicated spinning shard used by the original Silbarn collision effect. */
public final class SilbarnFragmentParticle extends TextureSheetParticle {
    private final float spin;

    private SilbarnFragmentParticle(ClientLevel level, double x, double y, double z,
                                    double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        xd = vx;
        yd = vy;
        zd = vz;
        gravity = 0.03F;
        friction = 0.985F;
        quadSize = 0.10F;
        lifetime = 30 + random.nextInt(11);
        roll = random.nextFloat() * ((float) Math.PI * 2.0F);
        oRoll = roll;
        spin = (random.nextBoolean() ? 1.0F : -1.0F) * (0.20F + random.nextFloat() * 0.24F);
        hasPhysics = true;
        pickSprite(sprites);
    }

    @Override public void tick() {
        super.tick();
        if (!removed) {
            oRoll = roll;
            roll += spin;
        }
    }

    @Override public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double vx, double vy, double vz) {
            return new SilbarnFragmentParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
