package com.adn.dabaeum.blockchain.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 현재 구성이 가리키는 원장 Provider 와 그 트랜잭션 network 이름이다.
 *
 * <p>워커·리컨실러·검증 서비스가 Credential 의 원장 참조를 만들 때 이 값을 쓴다. Provider 를
 * 바꾸면 여기 한 곳만 바뀌고, 업무 코드에 Provider 이름을 박아두지 않는다.
 *
 * <p>레거시 {@code CERT:} 경로는 FABRIC_POC 에만 있으므로 network {@code DABAEUM_FABRIC} 은
 * 항상 레거시 참조로 본다. {@code FABRIC_POC} 와 {@code DAEGUCHAIN} 은 같은 Storage 값 형식을
 * 쓰고 전환기에는 같은 원장을 바라보므로, DAEGUCHAIN 구성에서도 FABRIC_POC 로 기록된 대기
 * 트랜잭션을 이어서 처리한다.
 */
public record RegistryTarget(BlockchainProvider provider, String network) {

    public static final String LEGACY_NETWORK = "DABAEUM_FABRIC";
    private static final String FABRIC_POC_NETWORK = "FABRIC_POC";

    public static final RegistryTarget FABRIC_POC =
        new RegistryTarget(BlockchainProvider.FABRIC_POC, FABRIC_POC_NETWORK);
    public static final RegistryTarget DAEGUCHAIN =
        new RegistryTarget(BlockchainProvider.DAEGUCHAIN, "DAEGUCHAIN");

    public RegistryTarget {
        Objects.requireNonNull(provider, "provider");
        if (network == null || network.isBlank()) {
            throw new IllegalArgumentException("network is required");
        }
    }

    /** 이 구성의 워커가 집어가는 network 목록. 현재 값이 먼저 온다. */
    public List<String> supportedNetworks() {
        return Stream.of(network, FABRIC_POC_NETWORK, LEGACY_NETWORK).distinct().toList();
    }

    /** 신규 Storage 경로 Credential 이 가질 수 있는 network. */
    public List<String> currentNetworks() {
        return Stream.of(network, FABRIC_POC_NETWORK).distinct().toList();
    }

    public boolean accepts(String transactionNetwork, boolean legacyCredential) {
        return legacyCredential
            ? LEGACY_NETWORK.equals(transactionNetwork)
            : currentNetworks().contains(transactionNetwork);
    }

    /** Credential 의 원장 참조. 레거시(chainKey 없음)는 FABRIC_POC 의 CERT 경로다. */
    public CredentialRegistryReference reference(String chainKey, String credentialNo) {
        return chainKey == null
            ? new CredentialRegistryReference(BlockchainProvider.FABRIC_POC, null, credentialNo)
            : new CredentialRegistryReference(provider, chainKey, null);
    }
}
