package com.certchain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Off-chain metadata for an issued certificate.
 *
 * <p>The blockchain stores only the SHA-256 hash; all descriptive
 * information (student name, course, etc.) lives here in the
 * relational database.</p>
 */
@Entity
@Table(name = "certificate_metadata", indexes = {
    @Index(name = "idx_file_hash", columnList = "fileHash", unique = true)
})
public class CertificateMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String studentName;

    @Column(nullable = false)
    private String universityName;

    @Column(nullable = false)
    private String courseName;

    @Column(nullable = false)
    private LocalDate issueDate;

    /** Hex-encoded SHA-256 hash of the certificate PDF. */
    @Column(nullable = false, unique = true, length = 64)
    private String fileHash;

    /** Relative path to the stored PDF file. */
    @Column(nullable = false)
    private String filePath;

    /** Ethereum transaction hash from issuance. */
    private String txHash;

    /** If this cert was part of a batch, the Merkle root (hex). */
    private String merkleRoot;

    /** JSON-encoded Merkle proof siblings (if batch-issued). */
    @Column(columnDefinition = "TEXT")
    private String merkleProof;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CertificateStatus status = CertificateStatus.ISSUED;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Enum ─────────────────────────────────────────────

    public enum CertificateStatus {
        ISSUED,
        REVOKED
    }

    // ── Getters & Setters ────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getUniversityName() {
        return universityName;
    }

    public void setUniversityName(String universityName) {
        this.universityName = universityName;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(String merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public String getMerkleProof() {
        return merkleProof;
    }

    public void setMerkleProof(String merkleProof) {
        this.merkleProof = merkleProof;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
