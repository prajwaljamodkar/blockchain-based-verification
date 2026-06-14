package com.certchain.exception;

/**
 * Thrown when a certificate metadata record cannot be found in the database.
 */
public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String hash) {
        super("Certificate not found for hash: " + hash);
    }

    public CertificateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
