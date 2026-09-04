package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class EnrollmentCreateRequest {

    @NotNull
    private UUID userId;
    private EnrollmentApplicationType applicationType;

    public EnrollmentCreateRequest() {
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setApplicationType(EnrollmentApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public UUID userId() {
        return userId;
    }

    public EnrollmentApplicationType applicationType() {
        return applicationType;
    }
}
