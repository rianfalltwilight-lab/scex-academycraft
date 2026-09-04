package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyPresentationPhase4ContractTest {
    private static Path root(){ return Path.of("src/main"); }
    private static String source(String relative) throws Exception {
        return Files.readString(root().resolve("java/com/mohistmc/academy/").resolve(relative));
    }

    @Test void worldPresentationIsClientOnlyAndLifecycleBounded() throws Exception {
        String s=source("client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(s.contains("value = Dist.CLIENT"));
        assertTrue(s.contains("Stage.AFTER_PARTICLES"));
        assertTrue(s.contains("isCharging(\"mark_teleport\")"));
        assertFalse(s.contains("isCharging(\"ray_barrage\")"),
                "1.0.7 ray barrage executes on key-down and has no invented held preview");
        assertTrue(s.contains("RenderHandEvent"));
        assertTrue(s.contains("InteractionHand.MAIN_HAND"));
        assertFalse(s.contains("sendToServer"));
        assertFalse(s.contains("hurt("));
    }

    @Test void officialMarkAndBloodSequencesRemainComplete() {
        Path effects=root().resolve("resources/assets/academy/textures/effects");
        for(int i=0;i<8;i++) assertTrue(Files.isRegularFile(effects.resolve("tp_mark/"+i+".png")));
        for(int i=0;i<10;i++) assertTrue(Files.isRegularFile(effects.resolve("blood_splash/"+i+".png")));
        for(String surface:new String[]{"grnd","wall"})for(int i=0;i<3;i++)
            assertTrue(Files.isRegularFile(effects.resolve("blood_spray/"+surface+"/"+i+".png")));
    }

    @Test void replacementMeshesHaveHardParticleBudgets() throws Exception {
        String s=source("world/effect/EffectHelper.java");
        String entity=source("entity/LegacyFieldEffectEntity.java");
        String renderer=source("client/renderer/LegacyFieldEffectRenderer.java");
        String registry=source("world/AcademyEntities.java");
        assertTrue(s.contains("LegacyFieldEffectEntity.JET"));
        assertTrue(s.contains("field(level,current.x,current.y,current.z,LegacyFieldEffectEntity.JET"),
                "jet presentation must use one synchronized bounded field entity, not an unbounded particle fan");
        assertTrue(s.contains("i<18"));
        assertTrue(s.contains("count=0") || s.contains("origin.z,0,"));
        assertTrue(s.contains("LegacyFieldEffectEntity.WAVE"));
        assertTrue(entity.contains("LIFE_TICKS=12"));
        assertTrue(entity.contains("shouldBeSaved(){return false"));
        assertTrue(renderer.contains("band<=3"));
        assertTrue(renderer.contains("i<=32"));
        assertTrue(renderer.contains("rail<6"));
        assertTrue(renderer.contains("i<=18"));
        assertTrue(registry.contains("LEGACY_FIELD_EFFECT"));
        assertTrue(registry.contains("clientTrackingRange(64)"));
    }

    @Test void bloodDecalIsTrackedBoundedNonPersistentAndUsesOfficialSurfaces() throws Exception {
        String entity=source("entity/BloodSprayDecalEntity.java");
        String registry=source("world/AcademyEntities.java");
        String renderer=source("client/renderer/BloodSprayDecalRenderer.java");
        String client=source("listener/ClientModListener.java");
        assertTrue(entity.contains("LIFE_TICKS=40"));
        assertTrue(entity.contains("shouldBeSaved(){return false"));
        assertTrue(entity.contains("tickCount>=LIFE_TICKS"));
        assertTrue(registry.contains("clientTrackingRange(48)"));
        assertTrue(registry.contains("updateInterval(20)"));
        assertTrue(client.contains("BLOOD_SPRAY_DECAL"));
        assertTrue(client.contains("EventBusSubscriber.Bus.MOD"),
                "renderer registration must be subscribed to the mod bus");
        assertTrue(renderer.contains("\"grnd\":\"wall\""));
        assertTrue(renderer.contains("blood_spray/"));
    }
}
