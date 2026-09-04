package com.adn.dabaeum.blockchain.api;

public record BlockchainMetricsResponse(
    Long blockHeight,
    long transactionTotal,
    long transactionConfirmed,
    long transactionFailed,
    Double successRate,
    long credentialIssuedCount
) {
}
