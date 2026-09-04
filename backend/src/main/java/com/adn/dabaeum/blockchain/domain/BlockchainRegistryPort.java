package com.adn.dabaeum.blockchain.domain;

import java.util.List;
import java.util.OptionalLong;

/** Credential 업무가 사용하는 Provider 중립 원장 경계이다. */
public interface BlockchainRegistryPort {

    BlockchainReceipt createCredentialState(CredentialRegistryCreate command);

    CredentialRegistryState getCredentialState(CredentialRegistryReference reference);

    BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command);

    BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command);

    List<BlockchainHistoryEntry> getCredentialHistory(
        CredentialRegistryReference reference,
        BlockchainHistoryQuery query
    );

    /** 원장의 현재 블록 높이. 제공자가 모르면 empty. 모니터링 표시용이며 업무 판단에 쓰지 않는다. */
    default OptionalLong ledgerHeight() {
        return OptionalLong.empty();
    }
}
