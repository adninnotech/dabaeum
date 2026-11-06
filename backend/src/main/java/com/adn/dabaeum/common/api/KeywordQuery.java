package com.adn.dabaeum.common.api;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** 목록 API 의 키워드 검색 파라미터(q) 정규화. 모든 목록이 같은 규칙을 쓴다. */
public final class KeywordQuery {

    public static final int MAX_LENGTH = 100;

    private KeywordQuery() {
    }

    /** 조건이 없으면 null. 있으면 소문자·LIKE 이스케이프된 문자열. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("q"));
        }
        return trimmed.toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
