package com.certchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Merkle Tree service for batch certificate issuance.
 *
 * <p>Builds a binary hash tree from a list of SHA-256 leaf hashes,
 * computes the Merkle root, generates inclusion proofs, and
 * verifies proofs.</p>
 *
 * <h3>Security: Domain Separation</h3>
 * <p>To prevent second-preimage attacks, leaf hashes are prefixed
 * with {@code 0x00} and internal node hashes with {@code 0x01}
 * before hashing.</p>
 */
@Service
public class MerkleTreeService {

    private static final Logger log = LoggerFactory.getLogger(MerkleTreeService.class);

    private static final byte LEAF_PREFIX = 0x00;
    private static final byte NODE_PREFIX = 0x01;

    /**
     * Compute the Merkle root from a list of leaf hashes.
     *
     * @param leafHashes list of raw 32-byte SHA-256 hashes
     * @return the 32-byte Merkle root
     * @throws IllegalArgumentException if the list is empty
     */
    public byte[] computeRoot(List<byte[]> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute Merkle root from an empty list");
        }
        if (leafHashes.size() == 1) {
            return hashLeaf(leafHashes.get(0));
        }

        // Hash all leaves with domain-separation prefix
        List<byte[]> currentLevel = new ArrayList<>();
        for (byte[] leaf : leafHashes) {
            currentLevel.add(hashLeaf(leaf));
        }

        // Build tree bottom-up
        while (currentLevel.size() > 1) {
            currentLevel = computeParentLevel(currentLevel);
        }

        log.debug("Computed Merkle root from {} leaves", leafHashes.size());
        return currentLevel.get(0);
    }

    /**
     * Generate a Merkle proof (audit path) for a leaf at the given index.
     *
     * @param leafHashes list of raw leaf hashes (before domain-prefix hashing)
     * @param leafIndex  zero-based index of the target leaf
     * @return list of sibling hashes from leaf to root
     */
    public List<byte[]> generateProof(List<byte[]> leafHashes, int leafIndex) {
        if (leafIndex < 0 || leafIndex >= leafHashes.size()) {
            throw new IndexOutOfBoundsException(
                "Leaf index " + leafIndex + " is out of bounds for " + leafHashes.size() + " leaves");
        }

        // Hash leaves with domain prefix
        List<byte[]> currentLevel = new ArrayList<>();
        for (byte[] leaf : leafHashes) {
            currentLevel.add(hashLeaf(leaf));
        }

        List<byte[]> proof = new ArrayList<>();
        int index = leafIndex;

        while (currentLevel.size() > 1) {
            // If odd count, duplicate the last node
            if (currentLevel.size() % 2 != 0) {
                currentLevel.add(currentLevel.get(currentLevel.size() - 1));
            }

            int siblingIndex = (index % 2 == 0) ? index + 1 : index - 1;
            proof.add(currentLevel.get(siblingIndex));

            // Move up: compute parents and update the index
            currentLevel = computeParentLevel(currentLevel);
            index /= 2;
        }

        return proof;
    }

    /**
     * Verify that a leaf is included in a Merkle tree with the given root.
     *
     * @param leafHash raw leaf hash (before domain-prefix hashing)
     * @param proof    list of sibling hashes from leaf to root
     * @param root     expected Merkle root
     * @param leafIndex zero-based index of the leaf (determines left/right pairing)
     * @return true if the proof is valid
     */
    public boolean verifyProof(byte[] leafHash, List<byte[]> proof, byte[] root, int leafIndex) {
        byte[] computed = hashLeaf(leafHash);
        int index = leafIndex;

        for (byte[] sibling : proof) {
            if (index % 2 == 0) {
                computed = hashNodes(computed, sibling);
            } else {
                computed = hashNodes(sibling, computed);
            }
            index /= 2;
        }

        return MessageDigest.isEqual(computed, root);
    }

    // ── Internal helpers ─────────────────────────────────

    private List<byte[]> computeParentLevel(List<byte[]> nodes) {
        List<byte[]> parents = new ArrayList<>();

        // Duplicate last if odd
        if (nodes.size() % 2 != 0) {
            nodes = new ArrayList<>(nodes);
            nodes.add(nodes.get(nodes.size() - 1));
        }

        for (int i = 0; i < nodes.size(); i += 2) {
            parents.add(hashNodes(nodes.get(i), nodes.get(i + 1)));
        }
        return parents;
    }

    /**
     * Domain-separated leaf hash: SHA-256(0x00 || data).
     */
    private byte[] hashLeaf(byte[] data) {
        MessageDigest digest = getSha256();
        digest.update(LEAF_PREFIX);
        return digest.digest(data);
    }

    /**
     * Domain-separated internal node hash: SHA-256(0x01 || left || right).
     */
    private byte[] hashNodes(byte[] left, byte[] right) {
        MessageDigest digest = getSha256();
        digest.update(NODE_PREFIX);
        digest.update(left);
        return digest.digest(right);
    }

    private MessageDigest getSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
