package com.certchain.config;

import com.certchain.contract.CertificateRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;

/**
 * Web3j configuration — creates the Web3j client, wallet credentials,
 * and a pre-loaded contract instance as Spring beans.
 *
 * <p><strong>Security:</strong> The private key is loaded exclusively
 * from the {@code WALLET_PRIVATE_KEY} environment variable.</p>
 */
@Configuration
public class Web3jConfig {

    private static final Logger log = LoggerFactory.getLogger(Web3jConfig.class);

    // Gas parameters for local Hardhat network (generous defaults)
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(20_000_000_000L); // 20 Gwei
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(6_721_975L);

    @Value("${blockchain.node-url}")
    private String nodeUrl;

    @Value("${blockchain.contract-address}")
    private String contractAddress;

    @Value("${blockchain.private-key}")
    private String privateKey;

    @Bean
    public Web3j web3j() {
        log.info("Connecting to Ethereum node at: {}", nodeUrl);
        return Web3j.build(new HttpService(nodeUrl));
    }

    @Bean
    public Credentials credentials() {
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException(
                "WALLET_PRIVATE_KEY environment variable is not set. "
                + "Cannot sign transactions without a private key.");
        }
        Credentials creds = Credentials.create(privateKey);
        log.info("Wallet address: {}", creds.getAddress());
        return creds;
    }

    @Bean
    public CertificateRegistry certificateRegistry(Web3j web3j, Credentials credentials) {
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalStateException(
                "CONTRACT_ADDRESS environment variable is not set. "
                + "Deploy the smart contract first and set the address.");
        }
        log.info("Loading CertificateRegistry at: {}", contractAddress);
        return CertificateRegistry.load(
            contractAddress,
            web3j,
            credentials,
            new StaticGasProvider(GAS_PRICE, GAS_LIMIT)
        );
    }
}
