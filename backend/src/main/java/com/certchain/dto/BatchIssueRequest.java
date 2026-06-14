package com.certchain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for batch certificate issuance via Merkle tree.
 */
public record BatchIssueRequest(

    @NotEmpty(message = "At least one certificate is required for batch issuance")
    List<@Valid CertificateRequest> certificates

) {}
