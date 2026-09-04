package com.adn.dabaeum.correction.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 정정 사유 검증. 사유 없는 정정은 감사 이력이 의미를 잃으므로 허용하지 않는다. */
public final class CorrectionReasons {

    private CorrectionReasons() {
    }

    public static String require(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.VALIDATION_FAILED, "Request validation failed", List.of("reason"));
        }
        return reason.trim();
    }
}
