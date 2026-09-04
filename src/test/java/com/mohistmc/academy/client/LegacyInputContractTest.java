package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyInputContractTest {
    private static final Path INPUT = Path.of(
            "src/main/java/com/mohistmc/academy/client/KeyInputHandler.java");

    @Test
    void official107DefaultsAndMouseEdgePathAreRestored() throws Exception {
        String source = Files.readString(INPUT).replace("\r\n", "\n");
        assertTrue(source.contains("InputConstants.Type.MOUSE,\n            GLFW.GLFW_MOUSE_BUTTON_LEFT"));
        assertTrue(source.contains("InputConstants.Type.MOUSE,\n            GLFW.GLFW_MOUSE_BUTTON_RIGHT"));
        assertTrue(source.contains("GLFW.GLFW_KEY_R"));
        assertTrue(source.contains("GLFW.GLFW_KEY_F"));
        assertTrue(source.contains("GLFW.GLFW_KEY_V"));
        assertTrue(source.contains("GLFW.GLFW_KEY_C"));
        assertTrue(source.contains("activateOneShot(mc, i, skillId)"),
                "mouse buttons do not emit InputEvent.Key and must use tick edge detection");
        assertTrue(source.contains("held < 300"),
                "legacy V short-release toggle threshold is missing");
        assertTrue(source.contains("overridesMouseButton"));
        assertFalse(source.contains("GLFW.GLFW_KEY_Y"));
        assertFalse(source.contains("GLFW.GLFW_KEY_U"));
    }

    @Test
    void onlyMappedActiveMouseSlotsSuppressVanillaInteractions() throws Exception {
        String input = Files.readString(INPUT);
        String listener = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/listener/ClientListener.java"));
        assertTrue(input.contains("if (!data.isAbilityActive()) return false"));
        assertTrue(input.contains("getCurrentPreset().getSlot(i) != null"));
        assertTrue(listener.contains("overridesMouseButton"));
        assertTrue(listener.contains("event.setCanceled(true)"));
    }
}
