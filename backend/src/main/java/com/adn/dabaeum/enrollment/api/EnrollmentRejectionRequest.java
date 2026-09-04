package com.adn.dabaeum.enrollment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class EnrollmentRejectionRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;

    public EnrollmentRejectionRequest() {
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
