package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DabaeumUrnCredentialIdentifierProvider implements CredentialIdentifierProvider {

    private final CredentialUriProvider uriProvider;

    public DabaeumUrnCredentialIdentifierProvider(CredentialUriProvider uriProvider) {
        this.uriProvider = Objects.requireNonNull(uriProvider, "uriProvider");
    }

    @Override
    public String issuerIdentifier(UUID institutionId) {
        return uriProvider.issuerUrl(Objects.requireNonNull(institutionId, "institutionId"));
    }

    @Override
    public String subjectIdentifier(UUID userId) {
        return "urn:dabaeum:user:" + Objects.requireNonNull(userId, "userId");
    }

    @Override
    public String credentialIdentifier(UUID credentialId) {
        return "urn:uuid:" + Objects.requireNonNull(credentialId, "credentialId");
    }

    @Override
    public String contextIdentifier() {
        return uriProvider.contextUrl();
    }

    @Override
    public String statusListCredentialIdentifier(UUID listId) {
        return uriProvider.statusListUrl(listId);
    }

    @Override
    public String statusListEntryIdentifier(CredentialStatusListEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return uriProvider.statusListUrl(entry.listId()) + "#" + entry.index();
    }
}
