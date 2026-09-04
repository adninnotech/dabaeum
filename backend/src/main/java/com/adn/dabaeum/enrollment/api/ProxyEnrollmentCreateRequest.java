package com.adn.dabaeum.enrollment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class ProxyEnrollmentCreateRequest {

    @NotNull
    private UUID userId;

    public ProxyEnrollmentCreateRequest() {
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID userId() {
        return userId;
    }
}
