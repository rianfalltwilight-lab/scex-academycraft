package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Prevents restored 1.0.7 presentation assets from silently becoming dead resources. */
class LegacyPresentationContractTest {
    private static String java(String relative) throws Exception {return Files.readString(Path.of("src/main/java").resolve(relative));}
    private static Path asset(String relative){return Path.of("src/main/resources/assets/academy").resolve(relative);}

    @Test void markAndFlashingCuesReferenceEveryOfficialFrame() throws Exception {
        String hud=java("com/mohistmc/academy/client/gui/AbilityHudOverlay.java");
        String world=java("com/mohistmc/academy/client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(hud.contains("textures/effects/tp_mark/"));assertTrue(hud.contains("textures/abilities/teleporter/flashing/"));
        for(String skill:new String[]{"mark_teleport","penetrate_teleport","flesh_ripping","threatening_teleport","shift_tp"})
            assertTrue(world.contains("isCharging(\""+skill+"\")"));
        assertTrue(world.contains("renderCornerMarker")&&world.contains("renderFlashingMark"));
        for(int i=0;i<8;i++)assertTrue(Files.isRegularFile(asset("textures/effects/tp_mark/"+i+".png")));
        for(String key:new String[]{"a","d","w","s"})assertTrue(Files.isRegularFile(asset("textures/abilities/teleporter/flashing/"+key+".png")));
    }

    @Test void teleporterCriticalUsesTheOfficialFormulaFamilyAndCasterOnlySync() throws Exception {
        String particles=java("com/mohistmc/academy/world/AcademyParticles.java");
        String listener=java("com/mohistmc/academy/listener/ClientModListener.java");
        String passive=java("com/mohistmc/academy/skill/passive/PassiveDamageHelper.java");
        String packet=java("com/mohistmc/academy/network/TeleporterCriticalPacket.java");
        assertTrue(particles.contains("FORMULA")&&listener.contains("AcademyParticles.FORMULA"));
        assertTrue(passive.contains("PacketDistributor.sendToPlayer(player")&&passive.contains("crithit"));
        assertTrue(packet.contains("targetEntityId")&&packet.contains("tier >= 0")&&packet.contains("tier < 3"));
        for(int i=0;i<10;i++)assertTrue(Files.isRegularFile(asset("textures/effects/formula/"+i+".png")));
        assertTrue(Files.isRegularFile(asset("particles/formula.json")));
        String particleAtlas=Files.readString(Path.of("src/main/resources/assets/minecraft/atlases/particles.json"));
        assertTrue(particleAtlas.contains("effects/formula"),
                "formula frames live outside textures/particle and must be added to the particle atlas");
    }

    @Test void projectileAndBeamRenderersUseOfficialResources() throws Exception {
        String balls=java("com/mohistmc/academy/client/renderer/MdBallRenderer.java");
        String rail=java("com/mohistmc/academy/client/renderer/RailgunBeamRenderer.java");
        String melt=java("com/mohistmc/academy/client/renderer/MeltdownBeamRenderer.java");
        assertTrue(balls.contains("textures/effects/"));assertTrue(rail.contains("textures/effects/railgun/tile.png"));
        assertTrue(melt.contains("texture(\"mdray\")"));
        for(String family:new String[]{"mdray","mdray_luck","mdray_expert","mdray_small"}) {
            assertTrue(melt.contains("texture(\""+family+"\")"));
            assertTrue(Files.isRegularFile(asset("textures/effects/"+family+"/tile.png")));
        }
        for(int i=0;i<5;i++)assertTrue(Files.isRegularFile(asset("textures/effects/mdball/"+i+".png")));
        for(int i=0;i<4;i++)assertTrue(Files.isRegularFile(asset("textures/effects/mdball_active/"+i+".png")));
        assertTrue(Files.isRegularFile(asset("textures/effects/railgun/tile.png")));
    }

    @Test void railgunCoinAndItemChargeUseEveryOfficialArcBurstFrame() throws Exception {
        String presentation=java("com/mohistmc/academy/client/render/LegacyAbilityPresentationRenderer.java");
        String entities=java("com/mohistmc/academy/world/AcademyEntities.java");
        assertTrue(presentation.contains("RenderHandEvent")&&presentation.contains("RenderPlayerEvent.Post"));
        assertTrue(presentation.contains("age * 1.25f")&&presentation.contains("age >= 32"));
        assertTrue(presentation.contains("textures/effects/arc_burst/"));
        assertTrue(entities.contains("COIN_ENTITY")&&entities.contains(".updateInterval(1)"));
        for(int i=0;i<40;i++)assertTrue(Files.isRegularFile(asset("textures/effects/arc_burst/"+i+".png")));
    }

    @Test void vectorCuesHaveBothVisualLifetimeAndOfficialAudio() throws Exception {
        String input=java("com/mohistmc/academy/client/KeyInputHandler.java");String overlay=java("com/mohistmc/academy/client/gui/LegacyVectorWaveOverlay.java");String deviation=java("com/mohistmc/academy/skill/ability/vecmanip/VecDeviationEffect.java");String reflection=java("com/mohistmc/academy/skill/ability/vecmanip/VecReflectionEffect.java");String passive=java("com/mohistmc/academy/skill/passive/PassiveSkillEventHandler.java");
        assertTrue(overlay.contains("glow_circle.png")&&overlay.contains("averageSize")&&overlay.contains("initialSize + ripple.age * 20f"));
        assertTrue(!deviation.contains("shockwaveRing")&&!reflection.contains("shockwaveRing"));
        assertTrue(!input.contains("ParticleTypes.ELECTRIC_SPARK")&&!input.contains("ParticleTypes.END_ROD"));
        assertTrue(passive.contains("VM_VEC_DEVIATION"));assertTrue(passive.contains("VM_VEC_REFLECTION"));
        assertTrue(Files.isRegularFile(asset("sounds/vecmanip/vec_deviation.ogg")));assertTrue(Files.isRegularFile(asset("sounds/vecmanip/vec_reflection.ogg")));
    }

    @Test void teleporterAndMeltdownerUseOfficialCustomParticlesNotVanillaFallbacks() throws Exception {
        String effects=java("com/mohistmc/academy/world/effect/EffectHelper.java");
        String registry=java("com/mohistmc/academy/world/AcademyParticles.java");
        String provider=java("com/mohistmc/academy/client/particle/AcademyTextureParticle.java");
        assertTrue(registry.contains("TELEPORT")&&registry.contains("MELTDOWN"));
        assertTrue(effects.contains("AcademyParticles.TELEPORT")&&effects.contains("AcademyParticles.MELTDOWN"));
        assertTrue(provider.contains("PARTICLE_SHEET_TRANSLUCENT")&&provider.contains("minLife")
                &&provider.contains("fadeOutTicks"));
        assertTrue(Files.isRegularFile(asset("textures/particle/teleport.png")));
        assertTrue(Files.isRegularFile(asset("textures/particle/meltdown.png")));
        assertTrue(Files.isRegularFile(asset("particles/teleport.json")));
        assertTrue(Files.isRegularFile(asset("particles/meltdown.json")));
    }

    @Test void movementAndJetKeepTheirDistinctLegacyVisualIdentities() throws Exception {
        String effects=java("com/mohistmc/academy/world/effect/EffectHelper.java");
        String movement=java("com/mohistmc/academy/skill/ability/electromaster/MagMovementEffect.java");
        String presentation=java("com/mohistmc/academy/client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(movement.contains("electricTether")&&!movement.contains("EffectHelper.mdRay"));
        assertTrue(effects.contains("LegacyFieldEffectEntity.TETHER")&&effects.contains("LegacyFieldEffectEntity.JET"));
        assertTrue(presentation.contains("jet_engine")&&presentation.contains("textures/effects/ripple.png"));
        assertTrue(Files.isRegularFile(asset("textures/effects/ripple.png")));
    }

    @Test void thunderSkillsKeepTheirLegacyChargeAndArcPresentation() throws Exception {
        String clap=java("com/mohistmc/academy/skill/ability/electromaster/ThunderClapEffect.java");
        String bolt=java("com/mohistmc/academy/skill/ability/electromaster/ThunderBoltEffect.java");
        String movement=java("com/mohistmc/academy/client/ThunderClapMovementController.java");
        String presentation=java("com/mohistmc/academy/client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(clap.contains("releasesOnKeyUp() { return false; }")
                &&clap.contains("thunderClapDamageAtDistance"));
        assertTrue(movement.contains("getWalkingSpeed")&&movement.contains("setWalkingSpeed")
                &&movement.contains("restore()"));
        assertTrue(presentation.contains("renderThunderClap")&&presentation.contains("textures/effects/ripple.png"));
        assertTrue(bolt.contains("for(int i=0;i<3;i++)")&&bolt.contains("fullArcEnd")
                &&bolt.contains("e -> e != player && e.isAlive() && e.isPickable()"));
    }

    @Test void bodyIntensifyUsesItsDescendingArcEntityInsteadOfGenericParticles() throws Exception {
        String effect=java("com/mohistmc/academy/skill/ability/electromaster/BodyIntensifyEffect.java");
        String entity=java("com/mohistmc/academy/entity/LegacyFieldEffectEntity.java");
        String renderer=java("com/mohistmc/academy/client/renderer/LegacyFieldEffectRenderer.java");
        assertTrue(effect.contains("intensifyActivation")&&!effect.contains("lightningBurst"));
        assertTrue(entity.contains("INTENSIFY=7")&&renderer.contains("case LegacyFieldEffectEntity.INTENSIFY"));
        assertTrue(renderer.contains("double[] heights={2,1.8,1.5,1,.5,0,-.1}"));
    }

    @Test void plasmaUsesItsLegacyBodyAndTornadoInsteadOfAnItemRenderer() throws Exception {
        String renderer=java("com/mohistmc/academy/client/renderer/PlasmaOrbRenderer.java");
        String registration=java("com/mohistmc/academy/listener/ClientModListener.java");
        String effect=java("com/mohistmc/academy/skill/ability/vecmanip/PlasmaCannonEffect.java");
        assertTrue(renderer.contains("textures/effects/tornado_ring.png")&&renderer.contains("renderTornado"));
        assertTrue(registration.contains("PlasmaOrbRenderer::new"));
        assertTrue(effect.contains("configureCharging")&&effect.contains("Integer.MAX_VALUE"));
        assertTrue(Files.isRegularFile(asset("textures/effects/tornado_ring.png")));
    }

    @Test void meltdownerRaysAndSustainedSkillsConsumeTheirLegacyAudioVisuals() throws Exception {
        String bomb=java("com/mohistmc/academy/skill/ability/meltdowner/ElectronBombEffect.java");
        String missile=java("com/mohistmc/academy/skill/ability/meltdowner/ElectronMissileEffect.java");
        String scatter=java("com/mohistmc/academy/skill/ability/meltdowner/ScatterBombEffect.java");
        String barrage=java("com/mohistmc/academy/skill/ability/meltdowner/RayBarrageEffect.java");
        String barrageEntity=java("com/mohistmc/academy/entity/MeltdownBarrageEntity.java");
        String silbarn=java("com/mohistmc/academy/world/entity/EntitySilbarn.java");
        String meltdowner=java("com/mohistmc/academy/skill/ability/meltdowner/MeltdownerEffect.java");
        String shield=java("com/mohistmc/academy/skill/ability/meltdowner/LightShieldEffect.java");
        String mine=java("com/mohistmc/academy/skill/ability/meltdowner/AbstractMineRayEffect.java");
        assertTrue(bomb.contains("EffectHelper.mdRay")&&bomb.contains("MD_RAY_SMALL"),
                "electron bomb must show its legacy ball-to-target ray, not only a particle burst");
        assertTrue(missile.contains("EffectHelper.mdRay")&&missile.contains("MD_RAY_SMALL"));
        assertTrue(scatter.contains("EffectHelper.mdRay")&&scatter.contains("MD_RAY_SMALL"));
        assertTrue(barrage.contains("EffectHelper.barrageFan")&&barrage.contains("EffectHelper.barragePreRay")
                &&barrage.contains("breakByRayBarrage")&&barrage.contains("MD_RAY_SMALL"));
        assertTrue(barrageEntity.contains("25..29")&&barrageEntity.contains("LIFE_TICKS = 50"));
        assertTrue(silbarn.contains("SILBARN_FRAGMENT")&&Files.isRegularFile(asset("textures/particle/silbarn_fragment.png"))
                &&Files.isRegularFile(asset("textures/entity/silbarn.png")));
        assertTrue(meltdowner.contains("MD_MELTDOWNER"));
        assertTrue(shield.contains("MD_SHIELD_STARTUP"));
        assertTrue(mine.contains("MD_MINE_BASIC_STARTUP")&&mine.contains("MD_MINE_LUCK_STARTUP")
                &&mine.contains("MD_MINE_EXPERT_STARTUP"));
    }
}
