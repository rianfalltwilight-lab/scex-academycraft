package com.mohistmc.academy.client.media;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.terminal.MediaTrack;
import com.mohistmc.academy.terminal.MediaTrackRegistry;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/** Restores the 1.0.7 {@code .minecraft/acmedia} external-media contract. */
@OnlyIn(Dist.CLIENT)
public final class ExternalMediaManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocation MISSING_COVER = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "textures/guis/icons/icon_nomedia.png");
    private static final ResourceLocation README_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            AcademyCraft.MODID, "media/readme_template.txt");
    private static final int MAX_TRACKS = 256;
    private static final long MAX_COVER_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_COVER_DIMENSION = 2048;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_DESCRIPTION_LENGTH = 160;

    private static final Map<String, MediaTrack> TRACKS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> DYNAMIC_COVERS = new LinkedHashSet<>();
    private static boolean initialized;

    private ExternalMediaManager() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        Path root = rootFolder();
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("source"));
            Files.createDirectories(root.resolve("cover"));
            copyLegacyWorkspaceFiles(root);
            reloadTracks(root);
        } catch (IOException | RuntimeException ex) {
            TRACKS.clear();
            LOGGER.error("Unable to initialize AcademyCraft external media at {}", root, ex);
        }
    }

    public static synchronized List<MediaTrack> getTracks() {
        initialize();
        return List.copyOf(TRACKS.values());
    }

    public static synchronized MediaTrack getTrack(String trackId) {
        initialize();
        return TRACKS.get(trackId);
    }

    public static synchronized void updateMetadata(String trackId, String name, String description) {
        initialize();
        MediaTrack current = TRACKS.get(trackId);
        if (current == null || !current.external()) return;
        String fallback = legacyId(current.externalSource());
        String safeName = sanitize(name, MAX_NAME_LENGTH, fallback);
        String safeDescription = sanitize(description, MAX_DESCRIPTION_LENGTH, fallback);
        TRACKS.put(trackId, current.withExternalMetadata(safeName, safeDescription));
        saveMetadata(rootFolder().resolve("metadata.json"));
    }

    /** Dynamic textures must be recreated after a resource reload. */
    public static synchronized void invalidateForResourceReload() {
        TRACKS.clear();
        DYNAMIC_COVERS.clear();
        initialized = false;
    }

    private static void reloadTracks(Path root) throws IOException {
        releaseDynamicCovers();
        TRACKS.clear();
        Map<String, Metadata> metadata = loadMetadata(root.resolve("metadata.json"));
        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root.resolve("source"), "*.ogg")) {
            for (Path path : stream) {
                if (sources.size() >= MAX_TRACKS) {
                    LOGGER.warn("Ignoring external media beyond the {}-track safety limit", MAX_TRACKS);
                    break;
                }
                if (Files.isRegularFile(path)) sources.add(path.toAbsolutePath().normalize());
            }
        }
        sources.sort(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));

        for (Path source : sources) {
            var info = ExternalOggInspector.inspect(source);
            if (info.isEmpty()) {
                LOGGER.warn("Ignoring invalid or unsupported external Ogg file {}", source.getFileName());
                continue;
            }
            String legacyId = legacyId(source);
            if (legacyId.isBlank()) continue;
            String hash = shortHash(source.getFileName().toString());
            String trackId = "external/" + hash;
            Metadata custom = metadata.getOrDefault(trackId, new Metadata(legacyId, legacyId));
            String name = sanitize(custom.name(), MAX_NAME_LENGTH, legacyId);
            String description = sanitize(custom.description(), MAX_DESCRIPTION_LENGTH, legacyId);
            ResourceLocation texture = loadCover(root.resolve("cover").resolve(legacyId + ".png"), hash);
            ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath(
                    AcademyCraft.MODID, "external_media/" + hash);
            TRACKS.put(trackId, new MediaTrack(trackId, "", "", "EXT", texture, soundId,
                    info.get().durationSeconds(), source, name, description));
        }
        LOGGER.info("Loaded {} AcademyCraft external media track(s) from {}", TRACKS.size(), root.resolve("source"));
    }

    private static void copyLegacyWorkspaceFiles(Path root) {
        copyResource(README_RESOURCE, root.resolve("README.txt"));
        for (MediaTrack track : MediaTrackRegistry.getAllTracks()) {
            ResourceLocation source = ResourceLocation.fromNamespaceAndPath(
                    AcademyCraft.MODID, "sounds/media/" + track.trackId() + ".ogg");
            copyResource(source, root.resolve(track.trackId() + ".ogg"));
        }
    }

    private static void copyResource(ResourceLocation source, Path destination) {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(source).orElse(null);
            if (resource == null) return;
            try (InputStream input = resource.open()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOGGER.warn("Unable to copy legacy media workspace file {}", destination, ex);
        }
    }

    private static ResourceLocation loadCover(Path cover, String hash) {
        if (!isSafeCover(cover)) return MISSING_COVER;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                AcademyCraft.MODID, "acmedia_cover/" + hash);
        try (InputStream input = Files.newInputStream(cover)) {
            NativeImage image = NativeImage.read(input);
            if (image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > MAX_COVER_DIMENSION || image.getHeight() > MAX_COVER_DIMENSION) {
                image.close();
                return MISSING_COVER;
            }
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            DYNAMIC_COVERS.add(id);
            return id;
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Unable to load external media cover {}", cover.getFileName(), ex);
            return MISSING_COVER;
        }
    }

    private static boolean isSafeCover(Path cover) {
        try {
            if (!Files.isRegularFile(cover) || Files.size(cover) > MAX_COVER_BYTES) return false;
            try (ImageInputStream input = ImageIO.createImageInputStream(cover.toFile())) {
                if (input == null) return false;
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return false;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    return width > 0 && height > 0
                            && width <= MAX_COVER_DIMENSION && height <= MAX_COVER_DIMENSION;
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static void releaseDynamicCovers() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation cover : DYNAMIC_COVERS) textures.release(cover);
        DYNAMIC_COVERS.clear();
    }

    private static Map<String, Metadata> loadMetadata(Path file) {
        Map<String, Metadata> result = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) return result;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return result;
            JsonObject tracks = parsed.getAsJsonObject().getAsJsonObject("tracks");
            if (tracks == null) return result;
            for (Map.Entry<String, JsonElement> entry : tracks.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject value = entry.getValue().getAsJsonObject();
                String name = stringValue(value, "name");
                String description = stringValue(value, "description");
                result.put(entry.getKey(), new Metadata(name, description));
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Unable to read AcademyCraft external media metadata {}", file, ex);
        }
        return result;
    }

    private static void saveMetadata(Path file) {
        JsonObject tracks = new JsonObject();
        for (MediaTrack track : TRACKS.values()) {
            JsonObject value = new JsonObject();
            value.addProperty("name", track.externalName());
            value.addProperty("description", track.externalDescription());
            tracks.add(track.trackId(), value);
        }
        JsonObject root = new JsonObject();
        root.add("tracks", tracks);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOGGER.warn("Unable to save AcademyCraft external media metadata {}", file, ex);
        }
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : "";
    }

    private static String sanitize(String input, int maxLength, String fallback) {
        String source = input == null ? "" : input;
        StringBuilder result = new StringBuilder(Math.min(source.length(), maxLength));
        source.codePoints().filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(maxLength).forEach(result::appendCodePoint);
        String trimmed = result.toString().trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String legacyId(Path source) {
        String name = source.getFileName().toString();
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Path rootFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("acmedia")
                .toAbsolutePath().normalize();
    }

    private record Metadata(String name, String description) {}
}
