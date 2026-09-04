package com.adn.dabaeum.credential.domain;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"mediaType", "compactJws"})
public record SignedCredentialEnvelope(
    String mediaType,
    String compactJws
) {

    public SignedCredentialEnvelope {
        if (mediaType == null || mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType is required");
        }
        if (compactJws == null || compactJws.isBlank()) {
            throw new IllegalArgumentException("compactJws is required");
        }
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Credential envelope contains an unknown field");
    }
}
