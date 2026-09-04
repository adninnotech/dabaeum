package com.adn.dabaeum.daeguchain.client;

import com.adn.dabaeum.daeguchain.config.DaeguChainProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 대구체인 Storage API 호출. 문서(대구체인-Storage-API-Project-list.md)의 경로·요청·응답 봉투를
 * 그대로 따른다. 에뮬레이터와 실제 대구체인 어느 쪽에도 같은 코드로 붙는다.
 *
 * <p>응답은 트리로 읽는다. 봉투 안의 {@code response}·{@code receipt} 는 필드가 많고 실제
 * 체인이 늘릴 수 있어 레코드 바인딩(알 수 없는 필드 거부)을 쓰지 않는다.
 */
public final class DaeguChainStorageClient {

    public static final String STORAGE_PREFIX = "/daeguchain/v2/mitum/storage";
    private static final Logger LOGGER = LoggerFactory.getLogger(DaeguChainStorageClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DaeguChainProperties properties;

    public DaeguChainStorageClient(
        RestClient restClient,
        ObjectMapper objectMapper,
        DaeguChainProperties properties
    ) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /** Create Data. 반환은 봉투의 {@code data} — {@code tx}, {@code receipt} 등을 담는다. */
    public JsonNode createData(String dataKey, String dataValue) {
        return post("/create_data", Map.of("data_key", dataKey, "data_value", dataValue));
    }

    /** Update Data. */
    public JsonNode updateData(String dataKey, String dataValue) {
        return post("/update_data", Map.of("data_key", dataKey, "data_value", dataValue));
    }

    /** Get Data. {@code data.data_value}, {@code data.deleted}, {@code data.operation}. */
    public JsonNode getData(String dataKey) {
        return post("/get_data", Map.of("data_key", dataKey));
    }

    /**
     * Data History. 문서대로 {@code limit}·{@code offset}·{@code reverse} 를 문자열로 보낸다.
     * {@code offset=0} 은 현재 블록 높이, {@code reverse=true} 는 그보다 낮은 블록(최신 우선)이다.
     */
    public JsonNode dataHistory(String dataKey, int limit, long offset, boolean reverse) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data_key", dataKey);
        body.put("limit", String.valueOf(limit));
        body.put("offset", String.valueOf(offset));
        body.put("reverse", String.valueOf(reverse));
        return post("/data_history", body);
    }

    /**
     * 블록 높이. 대구체인 계약에는 없고 에뮬레이터 {@code /health} 에만 있어 URL 이 설정된 경우만
     * 읽는다. 실패는 empty 로 돌려 모니터링 표시만 비운다.
     */
    public OptionalLong healthHeight() {
        String url = properties.healthUrl();
        if (url == null) {
            return OptionalLong.empty();
        }
        try {
            String body = restClient.get().uri(url).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .body(String.class);
            JsonNode height = objectMapper.readTree(body == null ? "{}" : body).path("fabric").path("height");
            if (height.isMissingNode() || height.isNull()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(height.asString()));
        } catch (RuntimeException exception) {
            LOGGER.warn("대구체인 상태 URL 에서 블록 높이를 읽지 못했다", exception);
            return OptionalLong.empty();
        }
    }

    private JsonNode post(String path, Map<String, ?> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (properties.token() != null) {
            body.put("token", properties.token());
        }
        body.put("chain", properties.chain());
        body.put("project_id", properties.projectId());
        body.putAll(fields);

        String responseBody;
        try {
            responseBody = restClient.post()
                .uri(properties.baseUrl() + STORAGE_PREFIX + path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                // 4xx/5xx 도 봉투를 담고 있으므로 예외로 바꾸지 않고 본문을 읽는다.
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .body(String.class);
        } catch (ResourceAccessException exception) {
            LOGGER.warn("대구체인 Storage API 에 연결하지 못했다: {}", path, exception);
            throw new DaeguChainApiException(DaeguChainApiException.TRANSPORT, 0,
                "Storage API is unreachable", exception);
        } catch (RestClientException exception) {
            LOGGER.warn("대구체인 Storage API 호출 실패: {}", path, exception);
            throw new DaeguChainApiException(DaeguChainApiException.TRANSPORT, 0,
                "Storage API call failed", exception);
        }

        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(responseBody == null ? "" : responseBody);
        } catch (RuntimeException exception) {
            throw new DaeguChainApiException("MALFORMED_RESPONSE", 0, "Storage API response is not JSON");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new DaeguChainApiException("MALFORMED_RESPONSE", 0, "Storage API response is not an object");
        }
        if (!"OK".equals(envelope.path("state").asString())) {
            JsonNode rcode = envelope.path("rcode");
            String code = rcode.path("code").asString();
            int http = rcode.path("http").isNumber() ? rcode.path("http").asInt() : 0;
            LOGGER.warn("대구체인 Storage API 오류 응답: path={} code={} msg={}",
                path, code, envelope.path("msg").asString());
            throw new DaeguChainApiException(code, http, envelope.path("msg").asString());
        }
        return envelope.path("data");
    }
}
