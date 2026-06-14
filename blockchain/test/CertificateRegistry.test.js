const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("CertificateRegistry", function () {
  let registry;
  let owner;
  let nonOwner;

  // A deterministic test hash (SHA-256 of "test-certificate")
  const testHash = ethers.keccak256(ethers.toUtf8Bytes("test-certificate"));
  const testHash2 = ethers.keccak256(ethers.toUtf8Bytes("test-certificate-2"));
  const merkleRoot = ethers.keccak256(ethers.toUtf8Bytes("merkle-root-batch-1"));

  beforeEach(async function () {
    [owner, nonOwner] = await ethers.getSigners();
    const CertificateRegistry = await ethers.getContractFactory("CertificateRegistry");
    registry = await CertificateRegistry.deploy();
    await registry.waitForDeployment();
  });

  // ── Deployment ──────────────────────────────────────────

  describe("Deployment", function () {
    it("should set the deployer as owner", async function () {
      expect(await registry.owner()).to.equal(owner.address);
    });
  });

  // ── Issuance ────────────────────────────────────────────

  describe("issueCertificate", function () {
    it("should issue a certificate and emit CertificateIssued", async function () {
      await expect(registry.issueCertificate(testHash))
        .to.emit(registry, "CertificateIssued")
        .withArgs(testHash);

      const [issuedAt, exists, isValid] = await registry.getCertificate(testHash);
      expect(exists).to.be.true;
      expect(isValid).to.be.true;
      expect(issuedAt).to.be.greaterThan(0);
    });

    it("should revert if the same hash is issued twice", async function () {
      await registry.issueCertificate(testHash);
      await expect(registry.issueCertificate(testHash))
        .to.be.revertedWithCustomError(registry, "AlreadyIssued");
    });

    it("should revert if called by a non-owner", async function () {
      await expect(registry.connect(nonOwner).issueCertificate(testHash))
        .to.be.revertedWithCustomError(registry, "Unauthorized");
    });
  });

  // ── Batch Issuance ──────────────────────────────────────

  describe("issueBatch", function () {
    it("should issue a Merkle root and emit BatchIssued", async function () {
      await expect(registry.issueBatch(merkleRoot))
        .to.emit(registry, "BatchIssued")
        .withArgs(merkleRoot);

      const [, exists, isValid] = await registry.getCertificate(merkleRoot);
      expect(exists).to.be.true;
      expect(isValid).to.be.true;
    });

    it("should revert on duplicate Merkle root", async function () {
      await registry.issueBatch(merkleRoot);
      await expect(registry.issueBatch(merkleRoot))
        .to.be.revertedWithCustomError(registry, "AlreadyIssued");
    });
  });

  // ── Verification ────────────────────────────────────────

  describe("isVerified", function () {
    it("should return true for an issued certificate", async function () {
      await registry.issueCertificate(testHash);
      expect(await registry.isVerified(testHash)).to.be.true;
    });

    it("should return false for an unknown hash", async function () {
      expect(await registry.isVerified(testHash)).to.be.false;
    });

    it("should return false after revocation", async function () {
      await registry.issueCertificate(testHash);
      await registry.revokeCertificate(testHash);
      expect(await registry.isVerified(testHash)).to.be.false;
    });
  });

  // ── Revocation ──────────────────────────────────────────

  describe("revokeCertificate", function () {
    it("should revoke and emit CertificateRevoked", async function () {
      await registry.issueCertificate(testHash);
      await expect(registry.revokeCertificate(testHash))
        .to.emit(registry, "CertificateRevoked")
        .withArgs(testHash);

      const [, , isValid] = await registry.getCertificate(testHash);
      expect(isValid).to.be.false;
    });

    it("should revert when revoking a non-existent hash", async function () {
      await expect(registry.revokeCertificate(testHash))
        .to.be.revertedWithCustomError(registry, "NotIssued");
    });

    it("should revert when revoking an already-revoked hash", async function () {
      await registry.issueCertificate(testHash);
      await registry.revokeCertificate(testHash);
      await expect(registry.revokeCertificate(testHash))
        .to.be.revertedWithCustomError(registry, "AlreadyRevoked");
    });

    it("should revert if called by a non-owner", async function () {
      await registry.issueCertificate(testHash);
      await expect(registry.connect(nonOwner).revokeCertificate(testHash))
        .to.be.revertedWithCustomError(registry, "Unauthorized");
    });
  });

  // ── getCertificate ──────────────────────────────────────

  describe("getCertificate", function () {
    it("should return zeroed struct for unknown hash", async function () {
      const [issuedAt, exists, isValid] = await registry.getCertificate(testHash);
      expect(issuedAt).to.equal(0);
      expect(exists).to.be.false;
      expect(isValid).to.be.false;
    });
  });
});
