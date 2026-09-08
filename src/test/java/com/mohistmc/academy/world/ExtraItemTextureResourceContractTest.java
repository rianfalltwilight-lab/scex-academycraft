package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Fixed-upstream resource integrity, separate from the real client renderer gate. */
class ExtraItemTextureResourceContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path MODELS = RESOURCES.resolve("assets/academy/models/item");
    private static final List<String> ITEMS = List.of("optical_chip", "lasor_component", "etched_cobblestone",
            "ray_twister", "energy_unit_group", "electricalibur", "avalon", "cp_potion", "lasor_gun",
            "air_jet", "teleporter", "paper_plane", "drop_item_magnet",
            "reso_helmet", "reso_chestplate", "reso_leggings", "reso_boots",
            "imag_helmet", "imag_chestplate", "imag_leggings", "imag_boots",
            "paper_helmet", "paper_chestplate", "paper_leggings", "paper_boots");

    @Test void allFortyTwoOriginalPngsRemainByteIdenticalAndDecodable() throws Exception {
        JsonObject manifest = manifest();
        assertEquals("d66a190e3ae00d9ca8c154b47fca0509d4c171f7", manifest.get("commit").getAsString());
        JsonArray assets = manifest.getAsJsonArray("assets");
        assertEquals(42, assets.size());
        Set<String> paths = new HashSet<>();
        int sprites = 0, armorLayers = 0;
        for (var entry : assets) {
            var asset = entry.getAsJsonObject();
            String destination = asset.get("destination").getAsString();
            assertTrue(paths.add(destination), "duplicate imported texture " + destination);
            Path path = RESOURCES.resolve(destination).normalize();
            assertTrue(path.startsWith(RESOURCES), "resource path escaped root");
            assertTrue(Files.isRegularFile(path), destination);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            assertEquals(asset.get("sha256").getAsString().toLowerCase(java.util.Locale.ROOT), actual, destination);
            var image = ImageIO.read(path.toFile());
            assertNotNull(image, "unreadable PNG " + destination);
            assertEquals(asset.get("width").getAsInt(), image.getWidth(), destination);
            assertEquals(asset.get("height").getAsInt(), image.getHeight(), destination);
            if (destination.contains("/models/armor/")) {
                armorLayers++;
                assertEquals(2 * image.getHeight(), image.getWidth(), "legacy humanoid UV layout " + destination);
            } else {
                sprites++;
                assertEquals(image.getHeight(), image.getWidth(), "unintended animation strip " + destination);
                assertFalse(Files.exists(Path.of(path + ".mcmeta")), "static upstream sprite acquired animation metadata");
            }
        }
        assertEquals(34, sprites);
        assertEquals(8, armorLayers);
    }

    @Test void allRegisteredExtraItemsAndStateModelsUseTheirOriginalSprites() throws Exception {
        Set<String> expected = new HashSet<>(ITEMS);
        expected.addAll(List.of("energy_unit_group_1", "energy_unit_group_2", "energy_unit_group_3",
                "energy_unit_group_4", "energy_unit_group_5", "ray_twister_active", "teleporter_bound",
                "factor_aerohand", "factor_telekinesis"));
        Set<String> actual = new HashSet<>();
        for (var value : manifest().getAsJsonArray("adaptedModels")) {
            var mapping = value.getAsJsonObject();
            Path path = RESOURCES.resolve(mapping.get("destination").getAsString());
            String name = path.getFileName().toString().replace(".json", "");
            assertTrue(actual.add(name), "duplicate model " + name);
            var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), name);
            String sprite = model.getAsJsonObject("textures").get("layer0").getAsString();
            assertEquals(mapping.get("texture").getAsString(), sprite, name);
            String originalName = name.equals("energy_unit_group") ? "energy_unit_group_0"
                    : name.equals("teleporter_bound") ? "teleporter_pos" : name;
            assertEquals("academy:item/" + (name.startsWith("factor_") ? "" : "extraacc/") + originalName,
                    sprite, "borrowed placeholder remains " + name);
            String texturePath = sprite.substring("academy:".length());
            assertTrue(Files.isRegularFile(RESOURCES.resolve("assets/academy/textures/" + texturePath + ".png")), name);
        }
        assertEquals(expected, actual);
    }

    @Test void existingStatePredicatesRetainAllSixChargeAndTwoToggleAppearances() throws Exception {
        var charge = model("energy_unit_group").getAsJsonArray("overrides");
        assertEquals(5, charge.size());
        for (int i = 0; i < 5; i++) {
            var override = charge.get(i).getAsJsonObject();
            assertEquals(i + 1, override.getAsJsonObject("predicate").get("academy:charge").getAsInt());
            assertEquals("academy:item/energy_unit_group_" + (i + 1), override.get("model").getAsString());
        }
        assertOverride("ray_twister", "academy:active", "academy:item/ray_twister_active");
        assertOverride("teleporter", "academy:bound", "academy:item/teleporter_bound");
    }

    @Test void electricaliburAndLaserRetainOriginalFirstAndThirdPersonTransforms() throws Exception {
        for (String name : List.of("electricalibur", "lasor_gun")) {
            var display = model(name).getAsJsonObject("display");
            assertEquals(4, display.size(), name);
            float scale = name.equals("electricalibur") ? 1.25F : .85F;
            for (boolean first : new boolean[]{false, true}) for (boolean left : new boolean[]{false, true}) {
                String key = (first ? "firstperson" : "thirdperson") + (left ? "_lefthand" : "_righthand");
                var transform = display.getAsJsonObject(key);
                assertVector(transform.getAsJsonArray("rotation"), 0, left ? 90 : -90,
                        (left ? -1 : 1) * (first ? 25 : 45));
                if (first) assertVector(transform.getAsJsonArray("translation"), 1, 3, 1);
                else if (name.equals("electricalibur")) assertVector(transform.getAsJsonArray("translation"), 0, 10, 2);
                else assertVector(transform.getAsJsonArray("translation"), 0, 0, -3);
                assertVector(transform.getAsJsonArray("scale"), scale, scale, scale);
            }
        }
    }

    private static void assertVector(JsonArray actual, float x, float y, float z) {
        assertEquals(3, actual.size());
        assertEquals(x, actual.get(0).getAsFloat(), .00001F);
        assertEquals(y, actual.get(1).getAsFloat(), .00001F);
        assertEquals(z, actual.get(2).getAsFloat(), .00001F);
    }
    private static void assertOverride(String name, String predicate, String target) throws Exception {
        var overrides = model(name).getAsJsonArray("overrides");
        assertEquals(1, overrides.size());
        var override = overrides.get(0).getAsJsonObject();
        assertEquals(1F, override.getAsJsonObject("predicate").get(predicate).getAsFloat());
        assertEquals(target, override.get("model").getAsString());
    }
    private static JsonObject model(String name) throws Exception {
        return JsonParser.parseString(Files.readString(MODELS.resolve(name + ".json"))).getAsJsonObject();
    }
    private static JsonObject manifest() throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve("assets/academy/extraacc-provenance.json"))).getAsJsonObject();
    }
}

