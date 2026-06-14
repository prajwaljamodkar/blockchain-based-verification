package com.certchain.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Outgoing response payload representing an issued certificate.
 */
public record CertificateResponse(
    UUID id,
    String studentName,
    String universityName,
    String courseName,
    LocalDate issueDate,
    String fileHash,
    String txHash,
    String status,
    LocalDateTime createdAt
) {}
