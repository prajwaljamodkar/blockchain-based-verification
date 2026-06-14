package com.certchain.controller;

import com.certchain.dto.CertificateRequest;
import com.certchain.dto.CertificateResponse;
import com.certchain.entity.CertificateMetadata;
import com.certchain.entity.CertificateMetadata.CertificateStatus;
import com.certchain.exception.CertificateNotFoundException;
import com.certchain.repository.CertificateMetadataRepository;
import com.certchain.service.BlockchainService;
import com.certchain.service.FileStorageService;
import com.certchain.service.HashService;
import com.certchain.service.MerkleTreeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin (University) endpoints for certificate issuance and revocation.
 */
@RestController
@RequestMapping("/api/admin/certificates")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final HashService hashService;
    private final MerkleTreeService merkleTreeService;
    private final FileStorageService fileStorageService;
    private final BlockchainService blockchainService;
    private final CertificateMetadataRepository repository;
    private final ObjectMapper objectMapper;

    public AdminController(HashService hashService,
                           MerkleTreeService merkleTreeService,
                           FileStorageService fileStorageService,
                           BlockchainService blockchainService,
                           CertificateMetadataRepository repository,
                           ObjectMapper objectMapper) {
        this.hashService = hashService;
        this.merkleTreeService = merkleTreeService;
        this.fileStorageService = fileStorageService;
        this.blockchainService = blockchainService;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    // ── List All Certificates ─────────────────────────────

    /**
     * List all issued certificates (for the dashboard).
     */
    @GetMapping
    public ResponseEntity<List<CertificateResponse>> listCertificates() {
        List<CertificateResponse> responses = repository.findAll().stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }

    // ── Single Certificate Issuance ──────────────────────

    /**
     * Issue a single certificate.
     *
     * <p>Accepts a multipart request with:</p>
     * <ul>
     *   <li>{@code file} — the PDF certificate</li>
     *   <li>{@code metadata} — JSON string of {@link CertificateRequest}</li>
     * </ul>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CertificateResponse> issueCertificate(
            @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") @Valid CertificateRequest metadata) throws IOException {

        log.info("Issuing certificate for student: {}", metadata.studentName());

        // 1. Compute SHA-256 hash
        String hexHash = hashService.hashFile(file);
        byte[] hashBytes = hashService.toBytes32(hexHash);

        // 2. Check for duplicate
        if (repository.existsByFileHash(hexHash)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(null); // Could also return existing record
        }

        // 3. Store the PDF off-chain
        String filePath = fileStorageService.store(file, hexHash);

        // 4. Record hash on the blockchain
        String txHash = blockchainService.issueCertificate(hashBytes);

        // 5. Save metadata to the database
        CertificateMetadata entity = new CertificateMetadata();
        entity.setStudentName(metadata.studentName());
        entity.setUniversityName(metadata.universityName());
        entity.setCourseName(metadata.courseName());
        entity.setIssueDate(metadata.issueDate());
        entity.setFileHash(hexHash);
        entity.setFilePath(filePath);
        entity.setTxHash(txHash);
        entity.setStatus(CertificateStatus.ISSUED);

        CertificateMetadata saved = repository.save(entity);

        log.info("Certificate issued successfully. Hash: {}, TX: {}", hexHash, txHash);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    // ── Batch Certificate Issuance (Merkle Tree) ─────────

    /**
     * Issue a batch of certificates using a Merkle tree.
     *
     * <p>Accepts multiple PDF files and a JSON array of metadata.
     * Only the Merkle root is stored on-chain; individual proofs
     * are persisted off-chain in the database.</p>
     */
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<CertificateResponse>> issueBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart("metadata") String metadataJson) throws IOException {

        // Parse the metadata array
        List<CertificateRequest> metadataList;
        try {
            metadataList = objectMapper.readValue(
                metadataJson,
                objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, CertificateRequest.class));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metadata JSON: " + e.getMessage());
        }

        if (files.size() != metadataList.size()) {
            throw new IllegalArgumentException(
                "Number of files (" + files.size() + ") must match metadata entries ("
                + metadataList.size() + ")");
        }

        log.info("Batch issuing {} certificates", files.size());

        // 1. Hash all files and store them
        List<String> hexHashes = new ArrayList<>();
        List<byte[]> hashBytesList = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String hexHash = hashService.hashFile(file);
            hexHashes.add(hexHash);
            hashBytesList.add(hashService.toBytes32(hexHash));
            filePaths.add(fileStorageService.store(file, hexHash));
        }

        // 2. Build Merkle tree
        byte[] merkleRoot = merkleTreeService.computeRoot(hashBytesList);
        String merkleRootHex = hashService.bytesToHex(merkleRoot);

        // 3. Store Merkle root on-chain (single transaction!)
        String txHash = blockchainService.issueBatch(merkleRoot);

        // 4. Save each certificate with its Merkle proof
        List<CertificateResponse> responses = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            List<byte[]> proof = merkleTreeService.generateProof(hashBytesList, i);
            List<String> proofHex = proof.stream()
                .map(hashService::bytesToHex)
                .toList();

            CertificateMetadata entity = new CertificateMetadata();
            entity.setStudentName(metadataList.get(i).studentName());
            entity.setUniversityName(metadataList.get(i).universityName());
            entity.setCourseName(metadataList.get(i).courseName());
            entity.setIssueDate(metadataList.get(i).issueDate());
            entity.setFileHash(hexHashes.get(i));
            entity.setFilePath(filePaths.get(i));
            entity.setTxHash(txHash);
            entity.setMerkleRoot(merkleRootHex);
            entity.setMerkleProof(objectMapper.writeValueAsString(proofHex));
            entity.setStatus(CertificateStatus.ISSUED);

            CertificateMetadata saved = repository.save(entity);
            responses.add(toResponse(saved));
        }

        log.info("Batch issuance complete. Merkle root: {}, TX: {}", merkleRootHex, txHash);

        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    // ── Revocation ───────────────────────────────────────

    /**
     * Revoke a certificate by its SHA-256 hash.
     */
    @PutMapping("/{hash}/revoke")
    public ResponseEntity<CertificateResponse> revokeCertificate(@PathVariable String hash) {
        log.info("Revoking certificate: {}", hash);

        CertificateMetadata entity = repository.findByFileHash(hash)
            .orElseThrow(() -> new CertificateNotFoundException(hash));

        byte[] hashBytes = hashService.toBytes32(hash);
        String txHash = blockchainService.revokeCertificate(hashBytes);

        entity.setStatus(CertificateStatus.REVOKED);
        entity.setRevokedAt(LocalDateTime.now());
        CertificateMetadata saved = repository.save(entity);

        log.info("Certificate revoked. Hash: {}, TX: {}", hash, txHash);

        return ResponseEntity.ok(toResponse(saved));
    }

    // ── Helper ───────────────────────────────────────────

    private CertificateResponse toResponse(CertificateMetadata entity) {
        return new CertificateResponse(
            entity.getId(),
            entity.getStudentName(),
            entity.getUniversityName(),
            entity.getCourseName(),
            entity.getIssueDate(),
            entity.getFileHash(),
            entity.getTxHash(),
            entity.getStatus().name(),
            entity.getCreatedAt()
        );
    }
}
