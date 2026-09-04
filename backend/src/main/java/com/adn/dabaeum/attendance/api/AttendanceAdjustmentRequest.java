package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AttendanceAdjustmentRequest(
    @NotNull AttendanceStatus status,
    @NotBlank @Size(max = 1000) String reason
) {
}
