package com.certchain.exception;

/**
 * Thrown when a blockchain operation (issue, verify, revoke) fails.
 *
 * <p>Wraps Web3j-specific exceptions like {@code ContractCallException},
 * {@code TransactionException}, and {@code IOException} into a single,
 * domain-specific exception that the global handler maps to HTTP 502.</p>
 */
public class BlockchainException extends RuntimeException {

    public BlockchainException(String message) {
        super(message);
    }

    public BlockchainException(String message, Throwable cause) {
        super(message, cause);
    }
}
