package com.mohistmc.academy.client.block.entity.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatrixRenderContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    void convertedObjRetainsTheOfficial1122RootGroupsAndFaceBoundaries() throws Exception {
        Path obj = MAIN.resolve("resources/assets/academy/models/matrix.obj");
        Map<String, Integer> faces = new LinkedHashMap<>();
        String current = null;
        for (String line : Files.readAllLines(obj)) {
            if (line.startsWith("o ")) {
                throw new AssertionError("OBJ objects would nest subsequent groups and break visibility: " + line);
            }
            if (line.startsWith("g ")) {
                current = line.substring(2).trim();
                faces.putIfAbsent(current, 0);
            } else if (line.startsWith("f ")) {
                assertNotNull(current, "every face must belong to an explicit legacy root group");
                faces.compute(current, (ignored, count) -> count + 1);
            }
        }

        assertEquals(List.of("Main", "Core", "Shield", "Duplicate01", "Duplicate02"),
                new ArrayList<>(faces.keySet()));
        assertEquals(Map.of(
                "Main", 212,
                "Core", 4,
                "Shield", 22,
                "Duplicate01", 22,
                "Duplicate02", 22), faces);
    }

    @Test
    void staticAndDynamicModelsCannotRenderTheSamePlateGeometry() throws Exception {
        String base = Files.readString(MAIN.resolve(
                "resources/assets/academy/models/block/matrix.json"));
        String shield = Files.readString(MAIN.resolve(
                "resources/assets/academy/models/block/matrix_shield.json"));

        assertTrue(base.contains("\"Main\": true"));
        assertTrue(base.contains("\"Core\": true"));
        assertTrue(base.contains("\"Shield\": false"));
        assertTrue(base.contains("\"Duplicate01\": false"));
        assertTrue(base.contains("\"Duplicate02\": false"));

        assertTrue(shield.contains("\"Main\": false"));
        assertTrue(shield.contains("\"Core\": false"));
        assertTrue(shield.contains("\"Shield\": true"));
        assertTrue(shield.contains("\"Duplicate01\": false"));
        assertTrue(shield.contains("\"Duplicate02\": false"));
    }

    @Test
    void rendererMatchesFinal1122CoreAndThreePlateStateWithoutInitializationFlagCoupling() throws Exception {
        String renderer = Files.readString(MAIN.resolve(
                "java/com/mohistmc/academy/client/block/entity/render/MatrixRender.java"));
        assertTrue(renderer.contains("PLATE_COUNT = 3"));
        assertTrue(renderer.contains("items.get(MatrixBlockEntity.PLATE_SLOT_0).is(AcademyItems.CONSTRAINT_PLATE.get())"));
        assertTrue(renderer.contains("items.get(MatrixBlockEntity.PLATE_SLOT_1).is(AcademyItems.CONSTRAINT_PLATE.get())"));
        assertTrue(renderer.contains("items.get(MatrixBlockEntity.PLATE_SLOT_2).is(AcademyItems.CONSTRAINT_PLATE.get())"));
        assertTrue(renderer.contains("matrix.initializationCoreLevel() >= 0"));
        assertFalse(renderer.contains("hasInitializationMaterials()"));
        assertFalse(renderer.contains("isInitialized()"));
        assertTrue(renderer.contains("timeMillis / 20.0"));
        assertTrue(renderer.contains("timeMillis / 900.0"));
        assertTrue(renderer.contains("120.0f * plate"));
        assertTrue(renderer.contains("Math.sin"));
    }

    @Test
    void sideLoadedShieldModelAndBlockEntityRendererAreBothRegistered() throws Exception {
        String listener = Files.readString(MAIN.resolve(
                "java/com/mohistmc/academy/listener/ClientModListener.java"));
        assertTrue(listener.contains("event.register(MatrixRender.SHIELD_MODEL)"));
        assertTrue(listener.contains(
                "registerBlockEntityRenderer(AcademyBlockEntities.MATRIX.get(), MatrixRender::new)"));

        String matrix = Files.readString(MAIN.resolve(
                "java/com/mohistmc/academy/world/block/entity/MatrixBlockEntity.java"));
        assertTrue(matrix.contains("CompoundTag tag = super.getUpdateTag(provider)"),
                "client plate animation requires the live inventory in the block-entity update tag");
    }
}
