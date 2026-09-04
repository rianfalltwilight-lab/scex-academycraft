package com.mohistmc.academy.client.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExternalOggInspectorTest {
    @Test
    void readsVorbisIdentificationAndFinalGranuleWithoutDecodingTheTrack() throws Exception {
        byte[] identification = new byte[16];
        identification[0] = 1;
        System.arraycopy("vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, identification, 1, 6);
        identification[11] = 2;
        putLittleEndianInt(identification, 12, 48_000);

        byte[] file = concatenate(page(0, identification), page(96_000, new byte[]{0}));
        var result = ExternalOggInspector.inspect(new ByteArrayInputStream(file));
        assertTrue(result.isPresent());
        assertEquals(2, result.get().durationSeconds());
        assertEquals(48_000, result.get().sampleRate());
        assertEquals(2, result.get().channels());
    }

    @Test
    void malformedCaptureAndHostileDurationFailClosed() throws Exception {
        assertTrue(ExternalOggInspector.inspect(new ByteArrayInputStream(new byte[64])).isEmpty());

        byte[] identification = new byte[16];
        identification[0] = 1;
        System.arraycopy("vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, identification, 1, 6);
        identification[11] = 2;
        putLittleEndianInt(identification, 12, 48_000);
        long hostileGranule = 48_000L * 24L * 60L * 60L + 1;
        byte[] file = concatenate(page(0, identification), page(hostileGranule, new byte[]{0}));
        assertTrue(ExternalOggInspector.inspect(new ByteArrayInputStream(file)).isEmpty());
    }

    private static byte[] page(long granule, byte[] body) throws IOException {
        if (body.length > 254) throw new IllegalArgumentException("test helper uses one segment");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = new byte[27];
        header[0] = 'O'; header[1] = 'g'; header[2] = 'g'; header[3] = 'S';
        putLittleEndianLong(header, 6, granule);
        header[26] = 1;
        output.write(header);
        output.write(body.length);
        output.write(body);
        return output.toByteArray();
    }

    private static byte[] concatenate(byte[] first, byte[] second) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(first);
        output.write(second);
        return output.toByteArray();
    }

    private static void putLittleEndianInt(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }

    private static void putLittleEndianLong(byte[] bytes, int offset, long value) {
        for (int i = 0; i < 8; i++) bytes[offset + i] = (byte) (value >>> (8 * i));
    }
}
