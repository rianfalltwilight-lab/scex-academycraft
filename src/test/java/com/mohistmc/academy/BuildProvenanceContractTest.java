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
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) { properties.load(reader); }
        assertTrue(info.contains("Mod-Version=" + properties.getProperty("mod_version")),
                "BUILD-INFO version must match the actual Gradle artifact version");
        assertTrue(info.contains("JUnit-Tests="));
        assertTrue(info.contains("GameTests="));
        assertTrue(info.contains("Client-Machine-Gate="));
        assertTrue(info.contains("Packaged-Server-Gate="));
        assertTrue(info.contains("Packaged-Client-Gate="));
        assertFalse(info.contains("frozen 0.0.15 candidate"));
        assertTrue(info.contains("Source-Tree-Inventory=scripts/source-files.ps1"));
        String inventory = Files.readString(Path.of("scripts/source-files.ps1"));
        assertTrue(inventory.contains("@('gradle', 'src', 'scripts', 'docs', '.github')"),
                "hash and archive must share the explicit rebuild-input allowlist");
        assertTrue(inventory.contains("'build', 'audit', 'net', 'run'"));
        assertTrue(inventory.contains("$segment -like 'run-*'"),
                "all runtime evidence directories must stay outside source hashing");
        assertTrue(notice.contains("final Minecraft 1.12.2 master"));
        assertTrue(notice.contains("1.1.3-2-g7b1401cd"));
        assertTrue(generator.contains(". (Join-Path $PSScriptRoot 'source-files.ps1')"));
        assertTrue(generator.contains("Get-AcademyDirectoryTreeDigest -ProjectRoot $projectRoot"));
        String packager = Files.readString(Path.of("scripts/package-source.ps1"));
        assertTrue(packager.contains("source-files.ps1"));
        assertTrue(generator.contains("\"Mod-Version=$modVersion\""));
    }
}
