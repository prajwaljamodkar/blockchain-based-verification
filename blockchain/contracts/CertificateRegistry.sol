// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title CertificateRegistry
 * @notice On-chain registry for academic certificate hash verification.
 *         Only SHA-256 hashes (as bytes32) are stored — never PII or files.
 * @dev    Supports single issuance, batch Merkle-root issuance, and revocation.
 */
contract CertificateRegistry {
    // ──────────────────────────────────────────────
    //  Types
    // ──────────────────────────────────────────────

    struct Certificate {
        uint64  issuedAt;   // block.timestamp at issuance
        bool    exists;     // true once issued
        bool    isValid;    // false once revoked
    }

    // ──────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────

    
    address public immutable owner;
    mapping(address => bool) public issuers;

    /// @notice hash → Certificate metadata
    mapping(bytes32 => Certificate) private certificates;

    // ──────────────────────────────────────────────
    //  Events
    // ──────────────────────────────────────────────

    event CertificateIssued(bytes32 indexed hash);
    event CertificateRevoked(bytes32 indexed hash);
    event BatchIssued(bytes32 indexed merkleRoot);

    // ──────────────────────────────────────────────
    //  Custom Errors (gas-efficient)
    // ──────────────────────────────────────────────

    error Unauthorized();
    error AlreadyIssued();
    error NotIssued();
    error AlreadyRevoked();

    // ──────────────────────────────────────────────
    //  Modifiers
    // ──────────────────────────────────────────────


    modifier onlyIssuer() {
    if (!issuers[msg.sender]) revert Unauthorized();
    _;
    }

    // ──────────────────────────────────────────────
    //  Constructor
    // ──────────────────────────────────────────────


    constructor(address backendWallet) {
    owner = msg.sender;

    // Owner can issue certificates
    issuers[msg.sender] = true;

    // Spring Boot backend can also issue certificates
    issuers[backendWallet] = true;
}

    // ──────────────────────────────────────────────
    //  Write Functions
    // ──────────────────────────────────────────────

    /**
     * @notice Issue a single certificate by storing its SHA-256 hash.
     * @param hash The SHA-256 digest of the certificate PDF (as bytes32).
     */
    function issueCertificate(bytes32 hash) external onlyIssuer {
        if (certificates[hash].exists) revert AlreadyIssued();

        certificates[hash] = Certificate({
            issuedAt: uint64(block.timestamp),
            exists:   true,
            isValid:  true
        });

        emit CertificateIssued(hash);
    }

    /**
     * @notice Issue a batch of certificates by storing only the Merkle root.
     * @dev    Individual leaves are verified off-chain using Merkle proofs.
     * @param merkleRoot The root hash of the Merkle tree of certificate hashes.
     */
    function issueBatch(bytes32 merkleRoot) external onlyIssuer {
        if (certificates[merkleRoot].exists) revert AlreadyIssued();

        certificates[merkleRoot] = Certificate({
            issuedAt: uint64(block.timestamp),
            exists:   true,
            isValid:  true
        });

        emit BatchIssued(merkleRoot);
    }

    /**
     * @notice Revoke a previously issued certificate or Merkle root.
     * @param hash The hash (or Merkle root) to revoke.
     */
    function revokeCertificate(bytes32 hash) external onlyIssuer {
        Certificate storage cert = certificates[hash];
        if (!cert.exists) revert NotIssued();
        if (!cert.isValid) revert AlreadyRevoked();

        cert.isValid = false;

        emit CertificateRevoked(hash);
    }
    function addIssuer(address issuer) external {
    if (msg.sender != owner) revert Unauthorized();
    issuers[issuer] = true;
    }

    function removeIssuer(address issuer) external {
        if (msg.sender != owner) revert Unauthorized();
        issuers[issuer] = false;
    }

    // ──────────────────────────────────────────────
    //  Read Functions
    // ──────────────────────────────────────────────

    /**
     * @notice Check whether a certificate hash is valid (issued and not revoked).
     * @param hash The SHA-256 digest to verify.
     * @return True if the certificate exists and has not been revoked.
     */
    function isVerified(bytes32 hash) external view returns (bool) {
        Certificate storage cert = certificates[hash];
        return cert.exists && cert.isValid;
    }

    /**
     * @notice Retrieve full certificate metadata.
     * @param hash The SHA-256 digest to query.
     * @return issuedAt  Timestamp of issuance (0 if not found).
     * @return exists    Whether the hash has been registered.
     * @return isValid   Whether the certificate is still valid.
     */
    function getCertificate(bytes32 hash)
        external
        view
        returns (uint64 issuedAt, bool exists, bool isValid)
    {
        Certificate storage cert = certificates[hash];
        return (cert.issuedAt, cert.exists, cert.isValid);
    }
}
