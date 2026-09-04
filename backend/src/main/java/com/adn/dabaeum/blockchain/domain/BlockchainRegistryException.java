package com.adn.dabaeum.blockchain.domain;

/** Provider 고유 오류를 업무 계층 공통 코드로 정규화한다. */
public final class BlockchainRegistryException extends RuntimeException {
    private final String code;

    public BlockchainRegistryException(String code) {
        super("Blockchain registry operation failed");
        this.code = CredentialRegistryState.requireText(code, "code");
    }

    public String code() {
        return code;
    }
}
