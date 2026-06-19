    const hre = require("hardhat");

async function main() {
  console.log("Deploying CertificateRegistry...");
  console.log(`Network: ${hre.network.name}`);

  const CertificateRegistry = await hre.ethers.getContractFactory("CertificateRegistry");

  const signers = await hre.ethers.getSigners();
  const deployer = signers[0];

  console.log(`Deployer address: ${deployer.address}`);

  // On Sepolia (single account) — the deployer IS the backend wallet.
  // On localhost (multiple accounts) — use Account #1 as backend wallet.
  const backendWallet = signers.length > 1 ? signers[1] : deployer;

  const registry = await CertificateRegistry.deploy(backendWallet.address);
  await registry.waitForDeployment();

  const address = await registry.getAddress();

  console.log("");
  console.log("════════════════════════════════════════════════════════");
  console.log(`  CertificateRegistry deployed to: ${address}`);
  console.log(`  Owner / Deployer:    ${deployer.address}`);
  console.log(`  Backend Wallet:      ${backendWallet.address}`);
  console.log("════════════════════════════════════════════════════════");
  console.log("");
  console.log("Set these on Render:");
  console.log(`  CONTRACT_ADDRESS=${address}`);
  console.log(`  WALLET_PRIVATE_KEY=<your-deployer-private-key>`);
  console.log(`  BLOCKCHAIN_NODE_URL=<your-sepolia-rpc-url>`);
  console.log("");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
