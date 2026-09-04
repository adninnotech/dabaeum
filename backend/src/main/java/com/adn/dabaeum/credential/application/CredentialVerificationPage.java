package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialVerification;
import java.util.List;

public record CredentialVerificationPage(
    List<CredentialVerification> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public CredentialVerificationPage {
        data = List.copyOf(data);
    }
}
