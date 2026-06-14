package com.certchain.service;

import com.certchain.contract.CertificateRegistry;
import com.certchain.exception.BlockchainException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * Service that encapsulates all interactions with the
 * {@link CertificateRegistry} smart contract.
 *
 * <p>Every blockchain call is wrapped in try/catch to translate
 * Web3j exceptions into a uniform {@link BlockchainException}.</p>
 */
@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    private final CertificateRegistry contract;

    public BlockchainService(CertificateRegistry contract) {
        this.contract = contract;
    }

    /**
     * Issue a single certificate hash on the blockchain.
     *
     * @param hash 32-byte SHA-256 digest
     * @return the Ethereum transaction hash
     */
    public String issueCertificate(byte[] hash) {
        try {
            log.info("Issuing certificate on-chain...");
            TransactionReceipt receipt = contract.issueCertificate(hash).send();
            String txHash = receipt.getTransactionHash();
            log.info("Certificate issued. TX: {}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to issue certificate on-chain", e);
            throw new BlockchainException("Failed to issue certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Issue a Merkle root on the blockchain for batch verification.
     *
     * @param merkleRoot 32-byte Merkle root
     * @return the Ethereum transaction hash
     */
    public String issueBatch(byte[] merkleRoot) {
        try {
            log.info("Issuing batch Merkle root on-chain...");
            TransactionReceipt receipt = contract.issueBatch(merkleRoot).send();
            String txHash = receipt.getTransactionHash();
            log.info("Batch issued. TX: {}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to issue batch on-chain", e);
            throw new BlockchainException("Failed to issue batch: " + e.getMessage(), e);
        }
    }

    /**
     * Verify whether a certificate hash exists and is valid on-chain.
     *
     * @param hash 32-byte SHA-256 digest
     * @return true if the blockchain confirms the hash is valid
     */
    public boolean verifyCertificate(byte[] hash) {
        try {
            log.debug("Verifying certificate on-chain...");
            Boolean result = contract.isVerified(hash).send();
            log.debug("Verification result: {}", result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to verify certificate on-chain", e);
            throw new BlockchainException("Failed to verify certificate: " + e.getMessage(), e);
        }
    }

    /**
     * Revoke a certificate hash on the blockchain.
     *
     * @param hash 32-byte SHA-256 digest
     * @return the Ethereum transaction hash
     */
    public String revokeCertificate(byte[] hash) {
        try {
            log.info("Revoking certificate on-chain...");
            TransactionReceipt receipt = contract.revokeCertificate(hash).send();
            String txHash = receipt.getTransactionHash();
            log.info("Certificate revoked. TX: {}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Failed to revoke certificate on-chain", e);
            throw new BlockchainException("Failed to revoke certificate: " + e.getMessage(), e);
        }
    }
}
