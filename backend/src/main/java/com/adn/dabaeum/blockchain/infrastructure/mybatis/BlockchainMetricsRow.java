package com.adn.dabaeum.blockchain.infrastructure.mybatis;

public record BlockchainMetricsRow(
    Long transactionTotal,
    Long transactionConfirmed,
    Long transactionFailed,
    Long credentialIssuedCount
) {
}
