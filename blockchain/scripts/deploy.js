    console.log("DEPLOY SCRIPT UPDATED");
    const hre = require("hardhat");

async function main() {


  console.log("Deploying CertificateRegistry...");

  const CertificateRegistry = await hre.ethers.getContractFactory("CertificateRegistry");

  // The constructor now requires a backendWallet address.
  // We use the second Hardhat test account so the backend signs with a different key than the owner.
  const signers = await hre.ethers.getSigners();
  const owner = signers[0];
  const backendWallet = signers[1]; // Hardhat Account #1

  const registry = await CertificateRegistry.deploy(backendWallet.address);
  await registry.waitForDeployment();

  const address = await registry.getAddress();
  console.log(`CertificateRegistry deployed to: ${address}`);
  console.log(`Owner (Account #0):          ${owner.address}`);
  console.log(`Backend Wallet (Account #1): ${backendWallet.address}`);
  console.log("");
  console.log("── Next Steps ──────────────────────────────────────────");
  console.log(`1. Set CONTRACT_ADDRESS=${address} in your backend environment`);
  console.log("2. Copy ABI from blockchain/artifacts/contracts/CertificateRegistry.sol/CertificateRegistry.json");
  console.log("3. Generate Java wrapper: web3j generate solidity ...");
  console.log("────────────────────────────────────────────────────────");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
