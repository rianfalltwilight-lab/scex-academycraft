package com.mohistmc.academy.client.media;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Minimal bounded Ogg/Vorbis metadata reader used before accepting external media. */
public final class ExternalOggInspector {
    public static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final int MAX_PACKET_BYTES = 1024 * 1024;

    private ExternalOggInspector() {}

    public record Info(int durationSeconds, int sampleRate, int channels) {}

    public static Optional<Info> inspect(Path path) {
        try {
            if (!Files.isRegularFile(path)) return Optional.empty();
            long size = Files.size(path);
            if (size < 32 || size > MAX_FILE_BYTES) return Optional.empty();
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
                return inspect(input);
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Optional<Info> inspect(InputStream input) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream(256);
        int sampleRate = 0;
        int channels = 0;
        long finalGranule = -1;
        int pages = 0;

        while (true) {
            byte[] header = input.readNBytes(27);
            if (header.length == 0) break;
            if (header.length != 27 || header[0] != 'O' || header[1] != 'g'
                    || header[2] != 'g' || header[3] != 'S' || header[4] != 0) {
                return Optional.empty();
            }
            if (++pages > 1_000_000) return Optional.empty();

            long granule = littleEndianLong(header, 6);
            if (granule >= 0) finalGranule = granule;
            int segmentCount = Byte.toUnsignedInt(header[26]);
            byte[] lacing = input.readNBytes(segmentCount);
            if (lacing.length != segmentCount) return Optional.empty();
            int bodyLength = 0;
            for (byte lace : lacing) bodyLength += Byte.toUnsignedInt(lace);
            byte[] body = input.readNBytes(bodyLength);
            if (body.length != bodyLength) return Optional.empty();

            int cursor = 0;
            for (byte laceByte : lacing) {
                int lace = Byte.toUnsignedInt(laceByte);
                if (packet.size() + lace > MAX_PACKET_BYTES) return Optional.empty();
                packet.write(body, cursor, lace);
                cursor += lace;
                if (lace < 255) {
                    byte[] complete = packet.toByteArray();
                    if (sampleRate == 0 && isIdentificationPacket(complete)) {
                        channels = Byte.toUnsignedInt(complete[11]);
                        sampleRate = littleEndianInt(complete, 12);
                    }
                    packet.reset();
                }
            }
        }

        if (sampleRate <= 0 || channels < 1 || channels > 8 || finalGranule <= 0) {
            return Optional.empty();
        }
        long seconds = Math.max(1L, (finalGranule + sampleRate - 1L) / sampleRate);
        if (seconds > 24L * 60L * 60L) return Optional.empty();
        return Optional.of(new Info((int) seconds, sampleRate, channels));
    }

    private static boolean isIdentificationPacket(byte[] packet) {
        return packet.length >= 16 && packet[0] == 1
                && packet[1] == 'v' && packet[2] == 'o' && packet[3] == 'r'
                && packet[4] == 'b' && packet[5] == 'i' && packet[6] == 's';
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return Byte.toUnsignedInt(bytes[offset])
                | Byte.toUnsignedInt(bytes[offset + 1]) << 8
                | Byte.toUnsignedInt(bytes[offset + 2]) << 16
                | Byte.toUnsignedInt(bytes[offset + 3]) << 24;
    }

    private static long littleEndianLong(byte[] bytes, int offset) {
        long result = 0;
        for (int i = 0; i < 8; i++) result |= (long) Byte.toUnsignedInt(bytes[offset + i]) << (8 * i);
        return result;
    }
}
