package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MachineLoopSoundContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/" + path));
    }

    @Test void bothLegacyMachineLoopsHaveWorkingAndLifecycleOwners() throws Exception {
        String manager = source("client/sound/MachineLoopSoundManager.java");
        String metal = source("world/block/MetalFomer.java");
        String fusor = source("world/block/ImagFusor.java");
        String session = source("client/ClientSessionState.java");

        assertTrue(manager.contains("MACHINE_MACHINE_WORK.value()"));
        assertTrue(manager.contains("MACHINE_IMAG_FUSOR_WORK.value()"));
        assertTrue(manager.contains("this.volume = volume") && manager.contains("0.6f"));
        assertTrue(manager.contains("this.looping = true"));
        assertTrue(manager.contains("this.attenuation = Attenuation.LINEAR"));
        assertTrue(manager.contains("if (!alive.getAsBoolean()) stop()"));
        assertTrue(metal.contains("lvl.isClientSide") && metal.contains("tickMetalFormer(mfbe)"));
        assertTrue(fusor.contains("lvl.isClientSide") && fusor.contains("tickImagFusor(fusor)"));
        assertTrue(session.contains("MachineLoopSoundManager.resetClientSession()"));
    }
}
