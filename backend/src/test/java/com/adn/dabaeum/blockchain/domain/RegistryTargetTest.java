package com.adn.dabaeum.blockchain.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegistryTargetTest {

    @Test
    void daeguchainTargetStillPicksUpFabricPocTransactionsButNeverLegacyOnes() {
        RegistryTarget target = RegistryTarget.DAEGUCHAIN;

        assertThat(target.supportedNetworks()).containsExactly("DAEGUCHAIN", "FABRIC_POC", "DABAEUM_FABRIC");
        assertThat(target.currentNetworks()).containsExactly("DAEGUCHAIN", "FABRIC_POC");
        assertThat(target.accepts("DAEGUCHAIN", false)).isTrue();
        assertThat(target.accepts("FABRIC_POC", false)).isTrue();
        assertThat(target.accepts("DABAEUM_FABRIC", false)).isFalse();
        assertThat(target.accepts("DABAEUM_FABRIC", true)).isTrue();
        assertThat(target.accepts("DAEGUCHAIN", true)).isFalse();
    }

    @Test
    void fabricPocTargetKeepsThePreviousNetworkLists() {
        RegistryTarget target = RegistryTarget.FABRIC_POC;

        assertThat(target.supportedNetworks()).containsExactly("FABRIC_POC", "DABAEUM_FABRIC");
        assertThat(target.currentNetworks()).containsExactly("FABRIC_POC");
    }

    @Test
    void referencesUseTheTargetProviderForStorageKeysAndFabricPocForLegacyNumbers() {
        CredentialRegistryReference current = RegistryTarget.DAEGUCHAIN.reference("K1K2K3K4K5K6K7K8", "CERT-1");
        assertThat(current.provider()).isEqualTo(BlockchainProvider.DAEGUCHAIN);
        assertThat(current.dataKey()).isEqualTo("K1K2K3K4K5K6K7K8");
        assertThat(current.isLegacy()).isFalse();

        CredentialRegistryReference legacy = RegistryTarget.DAEGUCHAIN.reference(null, "CERT-1");
        assertThat(legacy.provider()).isEqualTo(BlockchainProvider.FABRIC_POC);
        assertThat(legacy.legacyCredentialNo()).isEqualTo("CERT-1");
        assertThat(legacy.isLegacy()).isTrue();
    }

    @Test
    void rejectsBlankNetwork() {
        assertThatThrownBy(() -> new RegistryTarget(BlockchainProvider.DAEGUCHAIN, " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
