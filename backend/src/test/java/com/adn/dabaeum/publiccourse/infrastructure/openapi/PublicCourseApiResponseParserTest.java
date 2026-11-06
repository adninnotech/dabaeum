package com.adn.dabaeum.publiccourse.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 설계서 §2.2 — 실제 응답은 header·body 가 최상위, 오류는 type=json 이어도 XML. */
class PublicCourseApiResponseParserTest {

    private final PublicCourseApiResponseParser parser = new PublicCourseApiResponseParser(JsonMapper.builder().build());

    @Test
    void parsesRealResponseWithTopLevelHeaderAndBody() throws IOException {
        PublicCourseSource.Page page = parser.parse(fixture("response-sample.json"));

        assertThat(page.totalCount()).isEqualTo(8);
        assertThat(page.items()).hasSize(8);
        assertThat(page.items().getFirst())
            .containsEntry("insttCode", "3460000")
            .containsEntry("lctreNm", "필라테스 평일");
    }

    @Test
    void acceptsDocumentedResponseWrapper() {
        String body = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":{"lctreNm":"단일","insttCode":"3460000"}},"totalCount":"1"}}}
            """;

        PublicCourseSource.Page page = parser.parse(body);

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item).containsEntry("lctreNm", "단일"));
    }

    @Test
    void noDataResultIsEmptyPage() {
        String body = """
            {"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":{"items":"","totalCount":0}}
            """;

        PublicCourseSource.Page page = parser.parse(body);

        assertThat(page.totalCount()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    void errorResultCodeIsReported() {
        String body = """
            {"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}
            """;

        assertThatThrownBy(() -> parser.parse(body))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_22");
    }

    @Test
    void xmlErrorEnvelopeIsReportedWithReasonCode() throws IOException {
        assertThatThrownBy(() -> parser.parse(fixture("error-key-not-registered.xml")))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_30");
    }

    @Test
    void unreadableBodyIsInvalidResponse() {
        assertThatThrownBy(() -> parser.parse("<html>점검 중</html>"))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_INVALID_RESPONSE");
    }

    @Test
    void nullAndNumericValuesBecomeText() {
        String body = """
            {"header":{"resultCode":"00"},"body":{"items":{"item":[{"lctreNm":"a","psncpa":30,"homepageUrl":null}]},
             "totalCount":1}}
            """;

        assertThat(parser.parse(body).items().getFirst())
            .containsEntry("psncpa", "30")
            .containsEntry("homepageUrl", null);
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = PublicCourseApiResponseParserTest.class
            .getResourceAsStream("/public-courses/2026-10-08/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
