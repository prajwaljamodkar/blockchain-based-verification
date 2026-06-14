package com.certchain.contract;

import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple3;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Java wrapper for the CertificateRegistry Solidity contract.
 *
 * <p><strong>Note:</strong> This class is hand-written to match the
 * contract ABI. For production, regenerate with the Web3j CLI:</p>
 * <pre>
 * web3j generate solidity \
 *   -a blockchain/artifacts/contracts/CertificateRegistry.sol/CertificateRegistry.json \
 *   -o backend/src/main/java \
 *   -p com.certchain.contract
 * </pre>
 */
public class CertificateRegistry extends Contract {

    // ── Events ───────────────────────────────────────────

    public static final Event CERTIFICATE_ISSUED_EVENT = new Event(
        "CertificateIssued",
        Collections.singletonList(new TypeReference<Bytes32>(true) {})
    );

    public static final Event CERTIFICATE_REVOKED_EVENT = new Event(
        "CertificateRevoked",
        Collections.singletonList(new TypeReference<Bytes32>(true) {})
    );

    public static final Event BATCH_ISSUED_EVENT = new Event(
        "BatchIssued",
        Collections.singletonList(new TypeReference<Bytes32>(true) {})
    );

    // ── Constructor & Factory ────────────────────────────

    protected CertificateRegistry(String contractAddress, Web3j web3j,
                                   Credentials credentials,
                                   ContractGasProvider gasProvider) {
        super("", contractAddress, web3j, credentials, gasProvider);
    }

    public static CertificateRegistry load(String contractAddress, Web3j web3j,
                                            Credentials credentials,
                                            ContractGasProvider gasProvider) {
        return new CertificateRegistry(contractAddress, web3j, credentials, gasProvider);
    }

    // ── Write Functions ──────────────────────────────────

    /**
     * Issue a single certificate hash on-chain.
     */
    public RemoteFunctionCall<TransactionReceipt> issueCertificate(byte[] hash) {
        final Function function = new Function(
            "issueCertificate",
            Collections.singletonList(new Bytes32(hash)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Issue a batch Merkle root on-chain.
     */
    public RemoteFunctionCall<TransactionReceipt> issueBatch(byte[] merkleRoot) {
        final Function function = new Function(
            "issueBatch",
            Collections.singletonList(new Bytes32(merkleRoot)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    /**
     * Revoke a certificate hash on-chain.
     */
    public RemoteFunctionCall<TransactionReceipt> revokeCertificate(byte[] hash) {
        final Function function = new Function(
            "revokeCertificate",
            Collections.singletonList(new Bytes32(hash)),
            Collections.emptyList()
        );
        return executeRemoteCallTransaction(function);
    }

    // ── Read Functions ───────────────────────────────────

    /**
     * Check if a certificate hash is verified (exists and not revoked).
     */
    public RemoteFunctionCall<Boolean> isVerified(byte[] hash) {
        final Function function = new Function(
            "isVerified",
            Collections.singletonList(new Bytes32(hash)),
            Collections.singletonList(new TypeReference<Bool>() {})
        );
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    /**
     * Get full certificate metadata from the contract.
     *
     * @return Tuple3(issuedAt, exists, isValid)
     */
    public RemoteFunctionCall<Tuple3<BigInteger, Boolean, Boolean>> getCertificate(byte[] hash) {
        final Function function = new Function(
            "getCertificate",
            Collections.singletonList(new Bytes32(hash)),
            Arrays.asList(
                new TypeReference<Uint64>() {},
                new TypeReference<Bool>() {},
                new TypeReference<Bool>() {}
            )
        );
        return new RemoteFunctionCall<>(function, () -> {
            List<Type> results = executeCallMultipleValueReturn(function);
            return new Tuple3<>(
                (BigInteger) results.get(0).getValue(),
                (Boolean) results.get(1).getValue(),
                (Boolean) results.get(2).getValue()
            );
        });
    }
}
