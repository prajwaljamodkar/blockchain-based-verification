package com.certchain.controller;

import com.certchain.dto.VerificationResponse;
import com.certchain.repository.CertificateMetadataRepository;
import com.certchain.service.BlockchainService;
import com.certchain.service.HashService;
import com.certchain.service.MerkleTreeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link VerificationController}.
 * Uses MockMvc with mocked service dependencies.
 */
@WebMvcTest(VerificationController.class)
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HashService hashService;

    @MockitoBean
    private MerkleTreeService merkleTreeService;

    @MockitoBean
    private BlockchainService blockchainService;

    @MockitoBean
    private CertificateMetadataRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_HASH = "a".repeat(64);

    @Test
    void verifyByFile_whenVerified_shouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "cert.pdf", "application/pdf", "pdf-content".getBytes());

        when(hashService.hashFile(any())).thenReturn(VALID_HASH);
        when(hashService.toBytes32(VALID_HASH)).thenReturn(new byte[32]);
        when(blockchainService.verifyCertificate(any())).thenReturn(true);

        mockMvc.perform(multipart("/api/verify").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.fileHash").value(VALID_HASH));
    }

    @Test
    void verifyByFile_whenFraudulent_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "fake.pdf", "application/pdf", "fake-content".getBytes());

        when(hashService.hashFile(any())).thenReturn(VALID_HASH);
        when(hashService.toBytes32(VALID_HASH)).thenReturn(new byte[32]);
        when(blockchainService.verifyCertificate(any())).thenReturn(false);
        when(repository.findByFileHash(VALID_HASH)).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/verify").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.verified").value(false))
            .andExpect(jsonPath("$.message").value(
                "Certificate could not be verified. It may be fraudulent or altered."));
    }

    @Test
    void verifyByHash_whenVerified_shouldReturn200() throws Exception {
        when(hashService.toBytes32(VALID_HASH)).thenReturn(new byte[32]);
        when(blockchainService.verifyCertificate(any())).thenReturn(true);

        mockMvc.perform(get("/api/verify/" + VALID_HASH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void verifyByHash_whenNotFound_shouldReturn400() throws Exception {
        when(hashService.toBytes32(VALID_HASH)).thenReturn(new byte[32]);
        when(blockchainService.verifyCertificate(any())).thenReturn(false);
        when(repository.findByFileHash(VALID_HASH)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/verify/" + VALID_HASH))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.verified").value(false));
    }
}
