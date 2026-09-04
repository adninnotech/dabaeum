package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.api.CredentialReissueRequest;
import com.adn.dabaeum.credential.api.CredentialRevokeRequest;
import com.adn.dabaeum.fabric.application.CredentialFabricReconciler;
import org.junit.jupiter.api.Test;

class CredentialRevocationReissueServiceTest {

    @Test
    void task8ContractsAreAvailable() throws Exception {
        assertThat(CredentialApplicationService.class
            .getMethod("revoke", RevokeCredentialCommand.class)).isNotNull();
        assertThat(CredentialApplicationService.class
            .getMethod("reissue", ReissueCredentialCommand.class)).isNotNull();
        assertThat(CredentialRevokeRequest.class).isNotNull();
        assertThat(CredentialReissueRequest.class).isNotNull();
        assertThat(CredentialFabricReconciler.class).isNotNull();
    }
}
