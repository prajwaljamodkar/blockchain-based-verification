#!/bin/bash
set -e

echo "============================================"
echo "  CertChain — Starting Up"
echo "============================================"

# ── Ensure certificate storage directory exists ──
mkdir -p /app/certificate-storage

# ── Mode Selection ──────────────────────────────
# If BLOCKCHAIN_NODE_URL is set, we assume an external blockchain (testnet/mainnet).
# Otherwise, we start an embedded Hardhat node for demo/dev purposes.

if [ -z "$BLOCKCHAIN_NODE_URL" ]; then
    echo ""
    echo ">> No BLOCKCHAIN_NODE_URL set — starting embedded Hardhat node..."
    echo ""

    cd /app/blockchain

    # Install Hardhat dependencies if not already present
    if [ ! -d "node_modules" ]; then
        echo ">> Installing blockchain dependencies..."
        npm install --production=false 2>&1
    fi

    # Start Hardhat node in the background
    echo ">> Starting Hardhat node on port 8545..."
    npx hardhat node &
    HARDHAT_PID=$!

    # Wait for the node to be ready
    echo ">> Waiting for Hardhat node to be ready..."
    MAX_RETRIES=30
    RETRY_COUNT=0
    until curl -s -X POST http://127.0.0.1:8545 \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' > /dev/null 2>&1; do
        RETRY_COUNT=$((RETRY_COUNT + 1))
        if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
            echo ">> ERROR: Hardhat node did not start within ${MAX_RETRIES} seconds"
            exit 1
        fi
        sleep 1
    done
    echo ">> Hardhat node is ready!"

    # Deploy the smart contract if CONTRACT_ADDRESS is not set
    if [ -z "$CONTRACT_ADDRESS" ]; then
        echo ""
        echo ">> Deploying CertificateRegistry smart contract..."
        DEPLOY_OUTPUT=$(npx hardhat run scripts/deploy.js --network localhost 2>&1)
        echo "$DEPLOY_OUTPUT"

        # Extract the contract address from deployment output
        EXTRACTED_ADDRESS=$(echo "$DEPLOY_OUTPUT" | grep -oP 'deployed to: \K0x[a-fA-F0-9]+')
        if [ -n "$EXTRACTED_ADDRESS" ]; then
            export CONTRACT_ADDRESS="$EXTRACTED_ADDRESS"
            echo ">> CONTRACT_ADDRESS set to: $CONTRACT_ADDRESS"
        else
            echo ">> WARNING: Could not extract contract address from deployment output"
        fi
    fi

    # Set the blockchain node URL to local
    export BLOCKCHAIN_NODE_URL="http://127.0.0.1:8545"

    # Set a default private key if not provided (Hardhat Account #1)
    if [ -z "$WALLET_PRIVATE_KEY" ]; then
        export WALLET_PRIVATE_KEY="0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d"
        echo ">> Using default Hardhat Account #1 private key (dev only!)"
    fi

    cd /app
    echo ""
    echo ">> Embedded Hardhat blockchain is running (PID: $HARDHAT_PID)"

else
    echo ""
    echo ">> Using external blockchain: $BLOCKCHAIN_NODE_URL"
    echo ""
fi

# ── Determine Spring Profile ───────────────────
# If DATABASE_URL is set, activate the 'prod' profile (PostgreSQL)
if [ -n "$DATABASE_URL" ]; then
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
    echo ">> DATABASE_URL detected — activating Spring profile: $SPRING_PROFILES_ACTIVE"
fi

# ── Start Spring Boot ──────────────────────────
echo ""
echo "============================================"
echo "  Starting Spring Boot Application"
echo "============================================"
echo "  BLOCKCHAIN_NODE_URL: $BLOCKCHAIN_NODE_URL"
echo "  CONTRACT_ADDRESS:    $CONTRACT_ADDRESS"
echo "  SPRING_PROFILES:     ${SPRING_PROFILES_ACTIVE:-default}"
echo "============================================"
echo ""

exec java \
    -Dserver.port="${PORT:-8080}" \
    -Dblockchain.node-url="$BLOCKCHAIN_NODE_URL" \
    -Dblockchain.contract-address="$CONTRACT_ADDRESS" \
    -Dblockchain.private-key="$WALLET_PRIVATE_KEY" \
    -jar /app/app.jar
