package com.certchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hashing service for certificate files.
 *
 * <p>Streams file data through a {@link MessageDigest} to avoid loading
 * entire files into memory — safe for large PDFs.</p>
 */
@Service
public class HashService {

    private static final Logger log = LoggerFactory.getLogger(HashService.class);
    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * Compute the SHA-256 hash of a multipart file.
     *
     * @param file the uploaded file
     * @return hex-encoded SHA-256 digest (64 characters)
     * @throws IOException if the file cannot be read
     */
    public String hashFile(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return sha256Hex(is);
        }
    }

    /**
     * Compute the SHA-256 hash of raw bytes.
     *
     * @param data the byte array to hash
     * @return raw SHA-256 digest (32 bytes)
     */
    public byte[] sha256(byte[] data) {
        return getDigest().digest(data);
    }

    /**
     * Compute the SHA-256 hash of an input stream (streaming, memory-safe).
     *
     * @param inputStream the data stream
     * @return hex-encoded SHA-256 digest
     * @throws IOException if reading the stream fails
     */
    public String sha256Hex(InputStream inputStream) throws IOException {
        MessageDigest digest = getDigest();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        byte[] hashBytes = digest.digest();
        String hex = bytesToHex(hashBytes);
        log.debug("Computed SHA-256: {}", hex);
        return hex;
    }

    /**
     * Convert a hex-encoded SHA-256 hash to a 32-byte array
     * suitable for Solidity's {@code bytes32} type.
     *
     * @param hexHash 64-character hex string
     * @return 32-byte array
     */
    public byte[] toBytes32(String hexHash) {
        if (hexHash.startsWith("0x")) {
            hexHash = hexHash.substring(2);
        }
        if (hexHash.length() != 64) {
            throw new IllegalArgumentException(
                "Expected 64 hex characters for bytes32, got " + hexHash.length());
        }

        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            int hi = Character.digit(hexHash.charAt(i * 2), 16);
            int lo = Character.digit(hexHash.charAt(i * 2 + 1), 16);
            if (hi == -1 || lo == -1) {
                throw new IllegalArgumentException(
                    "Invalid hex character at position " + (i * 2));
            }
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        return bytes;
    }

    /**
     * Convert a byte array to a lowercase hex string.
     */
    public String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

    private MessageDigest getDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
