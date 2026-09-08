package com.mohistmc.academy.gametest;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Only coordinates explicitly enabled isolated acceptance processes. */
public final class NodeConfigGateState {
    private NodeConfigGateState() {}
    public static boolean enabled() { return Boolean.getBoolean("academy.nodeConfigGate"); }
    public static Path root() {
        String setting = System.getProperty("academy.nodeConfigRoot", "");
        if (setting.isBlank()) throw new IllegalStateException("node config gate root required");
        Path root = Path.of(setting).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("ISOLATED-ACCEPTANCE")))
            throw new IllegalStateException("isolated acceptance marker required");
        return root;
    }
    public static String read(String name) {
        try { Path file=root().resolve(name); return Files.isRegularFile(file) ? Files.readString(file).trim() : ""; }
        catch(IOException failure) { return ""; }
    }
    public static void write(String name, String value) {
        try {
            Path file=root().resolve(name), temp=root().resolve(name+".tmp");
            Files.writeString(temp,value,StandardCharsets.UTF_8);
            Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
        } catch(IOException failure) { throw new IllegalStateException(failure); }
    }
    public static void append(String name, String value) {
        try { Files.writeString(root().resolve(name),value+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(IOException failure) { throw new IllegalStateException(failure); }
    }
}