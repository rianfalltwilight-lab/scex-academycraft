package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the fatal distinction between NeoForge runtime and mod registration buses. */
class ClientRegistrationBusContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/listener").resolve(name));
    }

    @Test void everyClientRegistryHookLivesOnTheModBus() throws Exception {
        String mod = source("ClientModListener.java");
        String game = source("ClientListener.java");
        assertTrue(mod.contains("bus = EventBusSubscriber.Bus.MOD"));
        for (String event : List.of("RegisterMenuScreensEvent", "RegisterKeyMappingsEvent",
                "RegisterParticleProvidersEvent", "RegisterClientReloadListenersEvent",
                "EntityRenderersEvent.RegisterLayerDefinitions", "EntityRenderersEvent.RegisterRenderers")) {
            assertTrue(mod.contains(event), () -> "missing mod-bus registration: " + event);
            assertFalse(game.contains(event), () -> "registration left on game bus: " + event);
        }
        for (String menu : List.of("ABILITY_INTERFERER_MENU", "WIND_BASE_MENU", "WIND_MAIN_MENU", "NODE_BASIC",
                "NODE_STANDARD_MENU", "NODE_ADVANCED_MENU", "IMAG_FUSOR_MENU", "SOLAR_GEN_MENU",
                "PHASE_GEN_MENU", "MATRIX_MENU", "METAL_FORMER_MENU", "DEV_NORMAL_MENU",
                "DEV_ADVANCED_MENU")) {
            assertTrue(mod.contains("AcademyMenus." + menu + ".get()"),
                    () -> "machine screen is not registered: " + menu);
        }
    }
}
