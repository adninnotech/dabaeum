package com.adn.dabaeum.correction.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 정정 API 공통 요청. 사유는 감사 이력에 남으므로 필수다. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CorrectionReasonRequest(
    @NotBlank @Size(max = 1000) String reason
) {
}
