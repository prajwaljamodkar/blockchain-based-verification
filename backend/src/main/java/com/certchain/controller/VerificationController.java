package com.certchain.controller;

import com.certchain.dto.VerificationResponse;
import com.certchain.entity.CertificateMetadata;
import com.certchain.repository.CertificateMetadataRepository;
import com.certchain.service.BlockchainService;
import com.certchain.service.HashService;
import com.certchain.service.MerkleTreeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Public endpoints for certificate verification.
 *
 * <p>Employers upload a PDF (or provide a hash) and receive a
 * verified/fraudulent/revoked status based on blockchain records.</p>
 */
@RestController
@RequestMapping("/api/verify")
public class VerificationController {

    private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

    private final HashService hashService;
    private final MerkleTreeService merkleTreeService;
    private final BlockchainService blockchainService;
    private final CertificateMetadataRepository repository;
    private final ObjectMapper objectMapper;

    public VerificationController(HashService hashService,
                                   MerkleTreeService merkleTreeService,
                                   BlockchainService blockchainService,
                                   CertificateMetadataRepository repository,
                                   ObjectMapper objectMapper) {
        this.hashService = hashService;
        this.merkleTreeService = merkleTreeService;
        this.blockchainService = blockchainService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Verify a certificate by uploading the original PDF.
     *
     * <p>The system hashes the file, checks the blockchain, and
     * also verifies any Merkle proof if the cert was batch-issued.</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> verifyByFile(
            @RequestPart("file") MultipartFile file) throws IOException {

        String hexHash = hashService.hashFile(file);
        log.info("Verifying certificate by file upload. Hash: {}", hexHash);

        return verify(hexHash);
    }

    /**
     * Verify a certificate directly by its SHA-256 hash.
     */
    @GetMapping("/{hash}")
    public ResponseEntity<VerificationResponse> verifyByHash(@PathVariable String hash) {
        log.info("Verifying certificate by hash: {}", hash);
        return verify(hash);
    }

    // ── Core Verification Logic ──────────────────────────

    private ResponseEntity<VerificationResponse> verify(String hexHash) {
        byte[] hashBytes = hashService.toBytes32(hexHash);

        // Strategy 1: Direct on-chain verification (single-issued certs)
        boolean directlyVerified = blockchainService.verifyCertificate(hashBytes);
        if (directlyVerified) {
            return ResponseEntity.ok(VerificationResponse.verified(hexHash));
        }

        // Strategy 2: Merkle proof verification (batch-issued certs)
        Optional<CertificateMetadata> metadataOpt = repository.findByFileHash(hexHash);
        if (metadataOpt.isPresent()) {
            CertificateMetadata metadata = metadataOpt.get();

            // Check if the cert was revoked in the database
            if (metadata.getStatus() == CertificateMetadata.CertificateStatus.REVOKED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(VerificationResponse.revoked(hexHash));
            }

            // If it has a Merkle proof, verify via the root
            if (metadata.getMerkleRoot() != null && metadata.getMerkleProof() != null) {
                boolean merkleVerified = verifyMerkleProof(metadata, hashBytes);
                if (merkleVerified) {
                    return ResponseEntity.ok(VerificationResponse.verified(hexHash));
                }
            }
        }

        // Not found on-chain and no valid Merkle proof
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(VerificationResponse.fraudulent(hexHash));
    }

    /**
     * Verify a batch-issued certificate using its stored Merkle proof
     * against the on-chain Merkle root.
     */
    private boolean verifyMerkleProof(CertificateMetadata metadata, byte[] leafHash) {
        try {
            // Check if the Merkle root is still valid on-chain
            byte[] merkleRoot = hashService.toBytes32(metadata.getMerkleRoot());
            boolean rootValid = blockchainService.verifyCertificate(merkleRoot);
            if (!rootValid) {
                log.warn("Merkle root {} is no longer valid on-chain",
                    metadata.getMerkleRoot());
                return false;
            }

            // Deserialize the proof
            List<String> proofHex = objectMapper.readValue(
                metadata.getMerkleProof(),
                new TypeReference<List<String>>() {});
            List<byte[]> proof = proofHex.stream()
                .map(hashService::toBytes32)
                .toList();

            // We need the leaf index for verification, but we stored the proof
            // in order. We can try verification with index 0 first, then brute
            // force the index if needed. For correctness, we try all possible
            // indices up to 2^proofLength.
            int maxIndex = 1 << proof.size();
            for (int i = 0; i < maxIndex; i++) {
                if (merkleTreeService.verifyProof(leafHash, proof, merkleRoot, i)) {
                    log.debug("Merkle proof verified at index {}", i);
                    return true;
                }
            }

            log.warn("Merkle proof verification failed for hash {}",
                hashService.bytesToHex(leafHash));
            return false;

        } catch (Exception e) {
            log.error("Error during Merkle proof verification", e);
            return false;
        }
    }
}
