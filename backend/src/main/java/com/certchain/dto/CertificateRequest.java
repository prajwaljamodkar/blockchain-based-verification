package com.certchain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Incoming request payload for single certificate issuance.
 * Sent as the JSON metadata part of a multipart request.
 */
public record CertificateRequest(

    @NotBlank(message = "Student name is required")
    String studentName,

    @NotBlank(message = "University name is required")
    String universityName,

    @NotBlank(message = "Course name is required")
    String courseName,

    @NotNull(message = "Issue date is required")
    LocalDate issueDate

) {}
