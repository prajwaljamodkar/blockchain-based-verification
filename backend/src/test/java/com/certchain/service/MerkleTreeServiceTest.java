package com.certchain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MerkleTreeService}.
 */
class MerkleTreeServiceTest {

    private final MerkleTreeService merkleTreeService = new MerkleTreeService();
    private final HashService hashService = new HashService();

    @Test
    void computeRoot_singleLeaf_shouldReturnHashedLeaf() {
        byte[] leaf = hashService.sha256("cert-1".getBytes(StandardCharsets.UTF_8));
        byte[] root = merkleTreeService.computeRoot(List.of(leaf));
        assertNotNull(root);
        assertEquals(32, root.length);
    }

    @Test
    void computeRoot_twoLeaves_shouldBeDeterministic() {
        List<byte[]> leaves = createLeaves("cert-A", "cert-B");
        byte[] root1 = merkleTreeService.computeRoot(leaves);
        byte[] root2 = merkleTreeService.computeRoot(leaves);
        assertArrayEquals(root1, root2, "Same leaves must produce the same root");
    }

    @Test
    void computeRoot_differentLeaves_shouldProduceDifferentRoots() {
        byte[] root1 = merkleTreeService.computeRoot(createLeaves("a", "b"));
        byte[] root2 = merkleTreeService.computeRoot(createLeaves("c", "d"));
        assertFalse(MessageDigest.isEqual(root1, root2));
    }

    @Test
    void computeRoot_oddNumberOfLeaves_shouldHandleDuplication() {
        List<byte[]> leaves = createLeaves("x", "y", "z");
        byte[] root = merkleTreeService.computeRoot(leaves);
        assertNotNull(root);
        assertEquals(32, root.length);
    }

    @Test
    void computeRoot_emptyList_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
            () -> merkleTreeService.computeRoot(List.of()));
    }

    @Test
    void generateAndVerifyProof_shouldSucceedForAllLeaves() {
        List<byte[]> leaves = createLeaves("cert-1", "cert-2", "cert-3", "cert-4");
        byte[] root = merkleTreeService.computeRoot(leaves);

        for (int i = 0; i < leaves.size(); i++) {
            List<byte[]> proof = merkleTreeService.generateProof(leaves, i);
            assertNotNull(proof);
            assertFalse(proof.isEmpty());

            boolean valid = merkleTreeService.verifyProof(leaves.get(i), proof, root, i);
            assertTrue(valid, "Proof should be valid for leaf at index " + i);
        }
    }

    @Test
    void generateAndVerifyProof_oddLeaves_shouldSucceed() {
        List<byte[]> leaves = createLeaves("a", "b", "c", "d", "e");
        byte[] root = merkleTreeService.computeRoot(leaves);

        for (int i = 0; i < leaves.size(); i++) {
            List<byte[]> proof = merkleTreeService.generateProof(leaves, i);
            boolean valid = merkleTreeService.verifyProof(leaves.get(i), proof, root, i);
            assertTrue(valid, "Proof should be valid for leaf at index " + i);
        }
    }

    @Test
    void verifyProof_wrongLeaf_shouldFail() {
        List<byte[]> leaves = createLeaves("cert-1", "cert-2", "cert-3", "cert-4");
        byte[] root = merkleTreeService.computeRoot(leaves);
        List<byte[]> proof = merkleTreeService.generateProof(leaves, 0);

        byte[] fakeLeaf = hashService.sha256("fake-cert".getBytes(StandardCharsets.UTF_8));
        boolean valid = merkleTreeService.verifyProof(fakeLeaf, proof, root, 0);
        assertFalse(valid, "Proof should fail for a non-existent leaf");
    }

    @Test
    void verifyProof_wrongRoot_shouldFail() {
        List<byte[]> leaves = createLeaves("cert-1", "cert-2");
        merkleTreeService.computeRoot(leaves);
        List<byte[]> proof = merkleTreeService.generateProof(leaves, 0);

        byte[] fakeRoot = hashService.sha256("fake-root".getBytes(StandardCharsets.UTF_8));
        boolean valid = merkleTreeService.verifyProof(leaves.get(0), proof, fakeRoot, 0);
        assertFalse(valid, "Proof should fail for an incorrect root");
    }

    @Test
    void generateProof_outOfBounds_shouldThrow() {
        List<byte[]> leaves = createLeaves("a", "b");
        assertThrows(IndexOutOfBoundsException.class,
            () -> merkleTreeService.generateProof(leaves, 5));
    }

    // ── Helpers ──────────────────────────────────────────

    private List<byte[]> createLeaves(String... data) {
        List<byte[]> leaves = new ArrayList<>();
        for (String d : data) {
            leaves.add(hashService.sha256(d.getBytes(StandardCharsets.UTF_8)));
        }
        return leaves;
    }
}
