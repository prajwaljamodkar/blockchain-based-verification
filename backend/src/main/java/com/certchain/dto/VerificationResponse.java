package com.certchain.dto;

import java.time.LocalDateTime;

/**
 * Outgoing response payload for certificate verification.
 */
public record VerificationResponse(
    boolean verified,
    String message,
    String fileHash,
    LocalDateTime timestamp
) {

    /** Factory for a successful verification. */
    public static VerificationResponse verified(String fileHash) {
        return new VerificationResponse(
            true,
            "Certificate is valid and has been verified on the blockchain.",
            fileHash,
            LocalDateTime.now()
        );
    }

    /** Factory for a failed verification. */
    public static VerificationResponse fraudulent(String fileHash) {
        return new VerificationResponse(
            false,
            "Certificate could not be verified. It may be fraudulent or altered.",
            fileHash,
            LocalDateTime.now()
        );
    }

    /** Factory for a revoked certificate. */
    public static VerificationResponse revoked(String fileHash) {
        return new VerificationResponse(
            false,
            "Certificate was previously valid but has been revoked by the issuer.",
            fileHash,
            LocalDateTime.now()
        );
    }
}
