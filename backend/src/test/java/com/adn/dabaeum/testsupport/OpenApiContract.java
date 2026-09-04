package com.adn.dabaeum.testsupport;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import java.nio.file.Path;

/**
 * 테스트 전역에서 공유하는 OpenAPI 계약 검증기.
 *
 * <p>{@code openApi().isValid(specUrl)} 형태는 검증할 때마다 계약 문서를 새로 파싱한다.
 * 계약이 커지면서 파싱 1회가 수십 초에 달해 전체 테스트 시간의 대부분을 차지했으므로,
 * 검증기를 한 번만 만들어 재사용한다. {@code OpenApiInteractionValidator}는 생성 후
 * 불변이므로 여러 테스트가 공유해도 안전하다.
 */
public final class OpenApiContract {

    /** 단일 정본 계약 문서 경로. */
    public static final Path PATH =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();

    /** 계약 문서를 한 번만 파싱해 만든 공유 검증기. */
    public static final OpenApiInteractionValidator VALIDATOR =
        OpenApiInteractionValidator.createFor(PATH.toUri().toString()).build();

    private OpenApiContract() {
    }
}
