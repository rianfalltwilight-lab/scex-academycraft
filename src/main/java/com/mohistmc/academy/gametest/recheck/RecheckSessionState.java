package com.mohistmc.academy.gametest.recheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Local coordination only. Every process must opt in and share an explicitly marked audit directory. */
public final class RecheckSessionState {
    private RecheckSessionState() {}
    public static boolean enabled() { return Boolean.getBoolean("academy.recheckSessionGate"); }
    public static String role() { return System.getProperty("academy.recheckSessionRole", ""); }
    public static Path root() {
        var value = System.getProperty("academy.recheckSessionRoot", "");
        if (value.isBlank()) throw new IllegalStateException("recheckSessionRoot required");
        var path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path.resolve("ISOLATED-ACCEPTANCE"))) throw new IllegalStateException("isolated marker required");
        return path;
    }
    public static String read(String name) {
        try { var p = root().resolve(name); return Files.isRegularFile(p) ? Files.readString(p).trim() : ""; }
        catch (IOException e) { return ""; }
    }
    public static void write(String name, String value) {
        try {
            var target = root().resolve(name); var pending = root().resolve(name + "." + role() + ".tmp");
            Files.writeString(pending, value, StandardCharsets.UTF_8);
            Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static void append(String name, String value) {
        try { Files.writeString(root().resolve(name), value + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
        catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static boolean ack(String role, int stage) { return read(role + "-ack.txt").equals(Integer.toString(stage)); }
}