# CertChain — Blockchain-Based Digital Certificate Verification

A Web3 enterprise application that enables Universities to issue tamper-proof digital certificates and Employers to verify their authenticity against an Ethereum blockchain ledger.

**No PII is ever stored on-chain** — only SHA-256 hashes — ensuring GDPR compliance and minimal gas costs.

---

## Architecture

```
┌──────────────┐       ┌───────────────────┐       ┌──────────────────┐
│   Employer   │       │   Spring Boot     │       │    Ethereum      │
│   (Verify)   │──────▶│   Backend         │──────▶│    Blockchain    │
├──────────────┤       │                   │       │  (Hardhat Node)  │
│   Admin      │──────▶│  ┌─────────────┐  │       │                  │
│   (Issue /   │       │  │ HashService  │  │       │ CertificateReg.  │
│    Revoke)   │       │  │ MerkleTree   │  │       │   .sol           │
└──────────────┘       │  │ Blockchain   │  │       └──────────────────┘
                       │  │ FileStorage  │  │
                       │  └─────────────┘  │       ┌──────────────────┐
                       │                   │──────▶│  H2 / PostgreSQL │
                       │                   │       │  (Metadata)      │
                       └───────────────────┘       └──────────────────┘
```

## Tech Stack

| Layer             | Technology                                      |
|-------------------|-------------------------------------------------|
| Smart Contracts   | Solidity ^0.8.20                                |
| Local Blockchain  | Hardhat (built-in node)                         |
| Backend           | Java 17+, Spring Boot 3.4.x                    |
| Blockchain Bridge | Web3j 4.12.3                                    |
| Database          | H2 (dev) / PostgreSQL (prod)                   |
| File Storage      | Local filesystem (MVP) / S3 (production)        |

---

## Prerequisites

- **Java 17+** (JDK)
- **Node.js 18+** and **npm**
- **Gradle 8+** (or use the Gradle wrapper)

---

## Quick Start

### 1. Clone & Install

```bash
git clone <repository-url>
cd blockchain-based-verification
```

### 2. Set Up the Blockchain

```bash
cd blockchain
npm install
```

### 3. Compile the Smart Contract

```bash
npx hardhat compile
```

### 4. Run Contract Tests

```bash
npx hardhat test
```

Expected output: 12 passing tests covering issuance, batch issuance, verification, revocation, and access control.

### 5. Start the Local Blockchain

In a **dedicated terminal** (keep it running):

```bash
npx hardhat node
```

This starts a local Ethereum node at `http://127.0.0.1:8545` with 20 funded test accounts.

### 6. Deploy the Contract

In another terminal:

```bash
npx hardhat run scripts/deploy.js --network localhost
```

Copy the deployed contract address from the output.

### 7. Configure & Start the Backend

```bash
cd ../backend

# Set environment variables (use one of the Hardhat test account private keys)
# On Windows PowerShell:
$env:CONTRACT_ADDRESS="0x<deployed-address>"
$env:WALLET_PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
$env:BLOCKCHAIN_NODE_URL="http://127.0.0.1:8545"

# On Linux/macOS:
# export CONTRACT_ADDRESS="0x<deployed-address>"
# export WALLET_PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
# export BLOCKCHAIN_NODE_URL="http://127.0.0.1:8545"

# Run the backend
./gradlew bootRun
```

> ⚠️ The private key above is Hardhat Account #0's default key — safe for local development only. **Never use it in production.**

The backend starts at `http://localhost:8080`.

---

## API Reference

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/admin/certificates` | Issue a single certificate (multipart: `file` + `metadata` JSON) |
| `POST` | `/api/admin/certificates/batch` | Batch issue via Merkle tree (multipart: `files` + `metadata` JSON array) |
| `PUT`  | `/api/admin/certificates/{hash}/revoke` | Revoke a certificate |

### Verification Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/verify` | Verify by uploading the PDF (multipart: `file`) |
| `GET`  | `/api/verify/{hash}` | Verify by SHA-256 hash |

### Example: Issue a Certificate

```bash
curl -X POST http://localhost:8080/api/admin/certificates \
  -F "file=@/path/to/certificate.pdf" \
  -F 'metadata={"studentName":"Alice Johnson","universityName":"MIT","courseName":"Computer Science","issueDate":"2024-06-15"};type=application/json'
```

### Example: Verify a Certificate

```bash
curl -X POST http://localhost:8080/api/verify \
  -F "file=@/path/to/certificate.pdf"
```

### Example: Revoke a Certificate

```bash
curl -X PUT http://localhost:8080/api/admin/certificates/<sha256-hash>/revoke
```

---

## Generating the Java Contract Wrapper (Optional)

The project includes a hand-written Java wrapper for the smart contract. To regenerate it from the compiled ABI:

1. Install the Web3j CLI:
   ```bash
   curl -L get.web3j.io | sh && source ~/.web3j/source.sh
   ```

2. Generate the wrapper:
   ```bash
   web3j generate solidity \
     -a blockchain/artifacts/contracts/CertificateRegistry.sol/CertificateRegistry.json \
     -o backend/src/main/java \
     -p com.certchain.contract
   ```

---

## Project Structure

```
blockchain-based-verification/
├── blockchain/                          # Hardhat / Solidity
│   ├── contracts/CertificateRegistry.sol
│   ├── scripts/deploy.js
│   ├── test/CertificateRegistry.test.js
│   ├── hardhat.config.js
│   └── package.json
│
├── backend/                             # Spring Boot
│   ├── build.gradle
│   ├── src/main/java/com/certchain/
│   │   ├── CertChainApplication.java
│   │   ├── config/Web3jConfig.java
│   │   ├── contract/CertificateRegistry.java
│   │   ├── controller/
│   │   │   ├── AdminController.java
│   │   │   └── VerificationController.java
│   │   ├── dto/
│   │   ├── entity/CertificateMetadata.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   ├── BlockchainException.java
│   │   │   └── CertificateNotFoundException.java
│   │   ├── repository/
│   │   │   └── CertificateMetadataRepository.java
│   │   └── service/
│   │       ├── HashService.java
│   │       ├── MerkleTreeService.java
│   │       ├── FileStorageService.java
│   │       └── BlockchainService.java
│   └── src/main/resources/application.yml
│
├── .gitignore
└── README.md
```

---

## Running Tests

### Smart Contract Tests
```bash
cd blockchain && npx hardhat test
```

### Backend Unit Tests
```bash
cd backend && ./gradlew test
```

---

## Security Notes

- **Private keys** are loaded exclusively from environment variables (`WALLET_PRIVATE_KEY`)
- **No PII** is stored on the blockchain — only SHA-256 hashes
- **Path traversal protection** is built into `FileStorageService`
- **Input validation** is applied to all DTOs via Jakarta Validation
- For production, add **Spring Security** with JWT authentication and role-based access control

---

## License

MIT
