package com.certchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CertChain — Blockchain-Based Digital Certificate Verification System.
 *
 * <p>This application allows universities to issue tamper-proof digital
 * certificates and employers to verify their authenticity against an
 * Ethereum blockchain ledger.</p>
 */
@SpringBootApplication
public class CertChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(CertChainApplication.class, args);
    }
}
