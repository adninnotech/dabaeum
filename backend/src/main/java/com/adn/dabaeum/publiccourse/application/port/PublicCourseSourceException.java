package com.adn.dabaeum.publiccourse.application.port;

import java.util.Objects;

/**
 * 공공데이터포털 호출 실패. {@code code} 는 실행 이력에 그대로 남기므로 서비스키·URL 을 담지 않는다.
 * 포털 결과 코드는 {@code PUBLIC_DATA_<코드>}, 그 밖은 {@code PUBLIC_DATA_INVALID_RESPONSE} 등이다.
 */
public final class PublicCourseSourceException extends RuntimeException {

    private final String code;

    public PublicCourseSourceException(String code) {
        super(code);
        this.code = Objects.requireNonNull(code, "code");
    }

    public PublicCourseSourceException(String code, Throwable cause) {
        super(code, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String getCode() {
        return code;
    }
}
