package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/** BitstringStatusListCredential 본문. 비트 배열 외에 개인정보나 이수 정보는 넣지 않는다. */
@Component
public class CredentialStatusListDocumentFactory {

    private static final String W3C_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final List<String> TYPES = List.of(
        "VerifiableCredential", "BitstringStatusListCredential");

    private final ObjectMapper objectMapper;
    private final CredentialIdentifierProvider identifierProvider;

    public CredentialStatusListDocumentFactory(
        ObjectMapper objectMapper,
        CredentialIdentifierProvider identifierProvider
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.identifierProvider = Objects.requireNonNull(identifierProvider, "identifierProvider");
    }

    public CredentialDocument create(
        UUID listId,
        UUID institutionId,
        Instant validFrom,
        String encodedList
    ) {
        Objects.requireNonNull(listId, "listId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(validFrom, "validFrom");
        if (encodedList == null || encodedList.isBlank()) {
            throw new IllegalArgumentException("encodedList is required");
        }
        String listIdentifier = identifierProvider.statusListCredentialIdentifier(listId);

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", listIdentifier + "#list");
        subject.put("type", "BitstringStatusList");
        subject.put("statusPurpose", CredentialStatusList.REVOCATION);
        subject.put("encodedList", encodedList);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", List.of(W3C_CONTEXT));
        document.put("id", listIdentifier);
        document.put("type", TYPES);
        document.put("issuer", identifierProvider.issuerIdentifier(institutionId));
        document.put("validFrom", validFrom.toString());
        document.put("credentialSubject", subject);

        return new CredentialDocument(objectMapper.writer()
            .without(SerializationFeature.INDENT_OUTPUT,
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsString(document));
    }
}
