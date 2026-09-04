package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialView;
import java.util.List;

public record CredentialPage(
    List<CredentialView> data,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

    public CredentialPage {
        data = List.copyOf(data);
    }
}
