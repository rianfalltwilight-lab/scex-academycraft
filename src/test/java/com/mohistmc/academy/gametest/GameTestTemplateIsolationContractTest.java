package com.mohistmc.academy.gametest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

final class GameTestTemplateIsolationContractTest {
    @Test
    void emptyTemplateBoundsEveryRealTestFixture() throws Exception {
        Path path = Path.of("src/main/resources/data/academy/structure/empty.nbt");
        try (DataInputStream input = new DataInputStream(
                new GZIPInputStream(Files.newInputStream(path)))) {
            assertTrue(input.readUnsignedByte() == 10 && input.readUTF().isEmpty(),
                    "GameTest template must have an unnamed root compound");
            assertTrue(input.readUnsignedByte() == 9 && input.readUTF().equals("size")
                            && input.readUnsignedByte() == 3 && input.readInt() == 3,
                    "GameTest template must begin with a three-int size list");
            int x = input.readInt();
            int y = input.readInt();
            int z = input.readInt();
            assertTrue(x >= 64 && y >= 16 && z >= 64,
                    "GameTest fixtures must not overlap adjacent test cells");
        }
    }
}
