package com.adn.dabaeum.credential.domain;

import java.util.UUID;

public interface CredentialIdentifierProvider {

    String issuerIdentifier(UUID institutionId);

    String subjectIdentifier(UUID userId);

    String credentialIdentifier(UUID credentialId);

    String contextIdentifier();

    String statusListCredentialIdentifier(UUID listId);

    String statusListEntryIdentifier(CredentialStatusListEntry entry);
}
