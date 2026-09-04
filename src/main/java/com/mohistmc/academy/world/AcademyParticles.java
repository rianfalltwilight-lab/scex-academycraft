package com.mohistmc.academy.world;

import com.mohistmc.academy.AcademyCraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Official AcademyCraft particle families, kept separate from vanilla fallback particles. */
public final class AcademyParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES=DeferredRegister.create(Registries.PARTICLE_TYPE,AcademyCraft.MODID);
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> TELEPORT=PARTICLES.register("teleport",()->new SimpleParticleType(false));
    /** Ten-frame formula glyph used by the 1.0.7 Teleporter critical-hit cue. */
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> FORMULA=PARTICLES.register("formula",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> MELTDOWN=PARTICLES.register("meltdown",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> MELTDOWN_LUCK=PARTICLES.register("meltdown_luck",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> SILBARN_FRAGMENT=PARTICLES.register("silbarn_fragment",()->new SimpleParticleType(false));
    private AcademyParticles(){}
}
