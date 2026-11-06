package com.adn.dabaeum.publiccourse.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 설계서 §3.1 — Decoding 키를 클라이언트가 인코딩, 오류에 키를 남기지 않음. */
class PublicCourseOpenApiClientTest {

    private static final String BASE = "https://api.test/openapi/tn_pubr_public_lftm_lrn_lctre_api";
    private static final String DECODED_KEY = "ab+cd/ef==";

    private MockRestServiceServer server;
    private PublicCourseOpenApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PublicCourseOpenApiClient(builder.build(),
            new PublicCourseApiResponseParser(JsonMapper.builder().build()), BASE, DECODED_KEY);
    }

    @Test
    void encodesDecodingKeyAndSendsInstitutionPaging() {
        server.expect(requestTo(BASE + "?serviceKey=ab%2Bcd%2Fef%3D%3D&pageNo=2&numOfRows=500&type=json"
                + "&insttCode=3460000"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"header":{"resultCode":"00"},"body":{"items":{"item":[{"lctreNm":"요가"}]},"totalCount":501}}
                """, MediaType.APPLICATION_JSON));

        PublicCourseSource.Page page = client.fetch("3460000", 2, 500);

        assertThat(page.totalCount()).isEqualTo(501);
        assertThat(page.items()).singleElement().satisfies(row -> assertThat(row).containsEntry("lctreNm", "요가"));
        server.verify();
    }

    @Test
    void xmlKeyErrorIsReportedAsResultCode() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("""
            <OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader>
            </OpenAPI_ServiceResponse>
            """, MediaType.TEXT_XML));

        assertThatThrownBy(() -> client.fetch("3460000", 1, 1000))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_30");
    }

    @Test
    void httpErrorStatusIsReportedWithoutKey() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.fetch("3460000", 1, 1000))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_HTTP_503")
            .satisfies(PublicCourseOpenApiClientTest::doesNotLeakKey);
    }

    @Test
    void connectionFailureIsReportedWithoutKey() {
        server.expect(method(HttpMethod.GET)).andRespond(withException(new SocketTimeoutException("timeout")));

        assertThatThrownBy(() -> client.fetch("3460000", 1, 1000))
            .isInstanceOf(PublicCourseSourceException.class)
            .hasFieldOrPropertyWithValue("code", "PUBLIC_DATA_CONNECTION_FAILED")
            .satisfies(PublicCourseOpenApiClientTest::doesNotLeakKey);
    }

    @Test
    void missingServiceKeyFailsWithoutCalling() {
        PublicCourseOpenApiClient noKey = new PublicCourseOpenApiClient(RestClient.builder().build(),
            new PublicCourseApiResponseParser(JsonMapper.builder().build()), BASE, null);

        assertThatThrownBy(() -> noKey.fetch("3460000", 1, 1000))
            .hasFieldOrPropertyWithValue("code", "SERVICE_KEY_MISSING");
    }

    private static void doesNotLeakKey(Throwable thrown) {
        Throwable current = thrown;
        while (current != null) {
            assertThat(String.valueOf(current.getMessage())).doesNotContain("ab+cd", "ab%2Bcd", "serviceKey");
            current = current.getCause();
        }
    }
}
