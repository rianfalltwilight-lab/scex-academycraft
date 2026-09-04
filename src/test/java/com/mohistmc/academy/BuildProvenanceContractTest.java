package com.mohistmc.academy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Prevents a runnable release from silently carrying stale audit provenance. */
final class BuildProvenanceContractTest {
    @Test
    void releaseMetadataPinsTheAuditedFinal112AndCurrentModernSnapshots() throws IOException {
        String info = Files.readString(Path.of("BUILD-INFO.txt"));
        String notice = Files.readString(Path.of("NOTICE"));
        String generator = Files.readString(Path.of("scripts/generate-build-info.ps1"));

        assertTrue(info.contains("MohistMC-Upstream-SHA=00e9cf09fc4c52d2f9b3b3af7d4cda140a4ccf1c"));
        assertTrue(info.contains("Legacy-Upstream-SHA=7b1401cd420bd6888a2b9d8db5cd8a69fe314bb9"));
        assertTrue(info.contains("Mod-Version=0.0.17"));
        assertTrue(info.contains("JUnit-Tests="));
        assertTrue(info.contains("GameTests="));
        assertTrue(info.contains("Client-Machine-Gate="));
        assertTrue(info.contains("Packaged-Server-Gate="));
        assertTrue(info.contains("Packaged-Client-Gate="));
        assertFalse(info.contains("frozen 0.0.15 candidate"));
        assertTrue(info.contains("run-machine-gate/**"),
                "runtime worlds, logs and screenshots must not influence the source-tree hash");
        assertTrue(info.contains("run-packaged-*/**"),
                "packaged-JAR black-box installs must not influence the source-tree hash");
        assertTrue(info.contains("net/**"),
                "reference-only decompiled files outside src must not influence the release hash");
        assertTrue(notice.contains("final Minecraft 1.12.2 master"));
        assertTrue(notice.contains("1.1.3-2-g7b1401cd"));
        assertTrue(generator.contains("'run-machine-gate'"),
                "the generator exclusion, not only its displayed metadata, must be fixed");
        assertTrue(generator.contains("StartsWith('run-packaged-'"));
        assertTrue(generator.contains("'net'"));
        assertTrue(generator.contains("\"Mod-Version=$modVersion\""));
    }
}
