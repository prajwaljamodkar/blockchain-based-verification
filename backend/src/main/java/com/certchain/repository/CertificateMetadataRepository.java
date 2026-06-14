package com.certchain.repository;

import com.certchain.entity.CertificateMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CertificateMetadata}.
 */
@Repository
public interface CertificateMetadataRepository extends JpaRepository<CertificateMetadata, UUID> {

    /**
     * Find a certificate by its SHA-256 file hash.
     *
     * @param fileHash hex-encoded SHA-256 hash
     * @return the certificate metadata, if found
     */
    Optional<CertificateMetadata> findByFileHash(String fileHash);

    /**
     * Check if a certificate with the given hash already exists.
     */
    boolean existsByFileHash(String fileHash);
}
