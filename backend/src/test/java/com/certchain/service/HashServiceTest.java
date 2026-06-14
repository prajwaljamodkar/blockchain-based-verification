package com.certchain.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashService}.
 */
class HashServiceTest {

    private final HashService hashService = new HashService();

    @Test
    void sha256Hex_shouldProduceDeterministicHash() throws IOException {
        byte[] data = "hello, blockchain".getBytes(StandardCharsets.UTF_8);
        String hash1 = hashService.sha256Hex(new ByteArrayInputStream(data));
        String hash2 = hashService.sha256Hex(new ByteArrayInputStream(data));

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex should be 64 chars");
        assertEquals(hash1, hash2, "Same input must produce the same hash");
    }

    @Test
    void sha256Hex_differentInputs_shouldProduceDifferentHashes() throws IOException {
        String hash1 = hashService.sha256Hex(
            new ByteArrayInputStream("file-a".getBytes(StandardCharsets.UTF_8)));
        String hash2 = hashService.sha256Hex(
            new ByteArrayInputStream("file-b".getBytes(StandardCharsets.UTF_8)));

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashFile_shouldMatchDirectSha256Hex() throws IOException {
        byte[] content = "certificate-pdf-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "cert.pdf", "application/pdf", content);

        String hashFromFile = hashService.hashFile(file);
        String hashFromStream = hashService.sha256Hex(new ByteArrayInputStream(content));

        assertEquals(hashFromStream, hashFromFile);
    }

    @Test
    void toBytes32_shouldConvertValidHex() {
        // 64 hex chars = 32 bytes
        String hex = "a".repeat(64);
        byte[] bytes = hashService.toBytes32(hex);

        assertEquals(32, bytes.length);
        for (byte b : bytes) {
            assertEquals((byte) 0xAA, b);
        }
    }

    @Test
    void toBytes32_shouldHandleHexWithPrefix() {
        String hex = "0x" + "b".repeat(64);
        byte[] bytes = hashService.toBytes32(hex);
        assertEquals(32, bytes.length);
    }

    @Test
    void toBytes32_shouldRejectInvalidLength() {
        assertThrows(IllegalArgumentException.class,
            () -> hashService.toBytes32("abcdef"));
    }

    @Test
    void bytesToHex_shouldRoundTrip() {
        String original = "0123456789abcdef".repeat(4); // 64 chars
        byte[] bytes = hashService.toBytes32(original);
        String result = hashService.bytesToHex(bytes);
        assertEquals(original, result);
    }

    @Test
    void sha256_rawBytes_shouldReturn32Bytes() {
        byte[] result = hashService.sha256("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, result.length);
    }
}
