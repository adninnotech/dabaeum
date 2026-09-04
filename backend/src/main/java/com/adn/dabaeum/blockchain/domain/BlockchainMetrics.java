package com.adn.dabaeum.blockchain.domain;

public record BlockchainMetrics(
    Long blockHeight,
    long transactionTotal,
    long transactionConfirmed,
    long transactionFailed,
    Double successRate,
    long credentialIssuedCount
) {
}
