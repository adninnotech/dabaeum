package com.adn.dabaeum.publiccourse.infrastructure.openapi;

import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 공공데이터포털 전국평생학습강좌표준데이터 OpenAPI 클라이언트 (설계서 §3.1).
 *
 * <p>서비스키는 Decoding 키이며 여기서 URL 인코딩한다(이미 인코딩된 키를 넣으면 이중 인코딩되어 30 이 난다).
 * 요청 URL 에 키가 들어가므로 RestClient 예외를 원인으로 붙이지 않고 코드만 남긴다.
 */
public final class PublicCourseOpenApiClient implements PublicCourseSource {

    private final RestClient restClient;
    private final PublicCourseApiResponseParser parser;
    private final String baseUrl;
    private final String serviceKey;

    /** {@code serviceKey} 가 null 이면 호출하지 않고 SERVICE_KEY_MISSING 으로 실패한다. */
    public PublicCourseOpenApiClient(
        RestClient restClient, PublicCourseApiResponseParser parser, String baseUrl, String serviceKey
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.serviceKey = serviceKey == null || serviceKey.isBlank() ? null : serviceKey;
    }

    @Override
    public Page fetch(String insttCode, int pageNo, int numOfRows) {
        Objects.requireNonNull(insttCode, "insttCode");
        if (serviceKey == null) {
            throw new PublicCourseSourceException("SERVICE_KEY_MISSING");
        }
        String body;
        try {
            body = restClient.get()
                .uri(uri(insttCode, pageNo, numOfRows))
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new PublicCourseSourceException("PUBLIC_DATA_HTTP_" + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new PublicCourseSourceException("PUBLIC_DATA_CONNECTION_FAILED");
        }
        return parser.parse(body);
    }

    private URI uri(String insttCode, int pageNo, int numOfRows) {
        return UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("serviceKey", "{serviceKey}")
            .queryParam("pageNo", "{pageNo}")
            .queryParam("numOfRows", "{numOfRows}")
            .queryParam("type", "json")
            .queryParam("insttCode", "{insttCode}")
            .encode()
            .buildAndExpand(Map.of(
                "serviceKey", serviceKey,
                "pageNo", pageNo,
                "numOfRows", numOfRows,
                "insttCode", insttCode))
            .toUri();
    }
}
