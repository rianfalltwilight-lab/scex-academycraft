package com.mohistmc.academy.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Local process coordination for explicitly enabled isolated acceptance instances. */
public final class ConcurrentGateState {
    public static final String ENABLED = "academy.concurrentMenuGate";
    private ConcurrentGateState() {}
    public static boolean enabled() { return Boolean.getBoolean(ENABLED); }
    public static Path root() {
        String configured = System.getProperty("academy.concurrentRoot", "");
        if (configured.isBlank()) throw new IllegalStateException("concurrentRoot is required");
        Path root = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("ISOLATED-ACCEPTANCE")))
            throw new IllegalStateException("isolated acceptance marker is required");
        return root;
    }
    public static void write(String name, String value) {
        try { Files.writeString(root().resolve(name), value, StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static String read(String name) {
        try { Path path = root().resolve(name); return Files.isRegularFile(path) ? Files.readString(path).trim() : ""; }
        catch (IOException e) { return ""; }
    }
    public static void evidence(String value) {
        try { Files.writeString(root().resolve("server-evidence.txt"), value + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
}
