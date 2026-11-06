package com.adn.dabaeum.publiccourse.infrastructure.openapi;

import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource.Page;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 전국평생학습강좌표준데이터 응답을 해석한다 (설계서 §2.2).
 *
 * <p>실제 응답은 문서와 달리 {@code response} 래퍼 없이 {@code header}·{@code body} 가 최상위에 온다.
 * 두 형태를 모두 받는다. 키·트래픽 오류는 {@code type=json} 이어도 XML 로 온다.
 */
public final class PublicCourseApiResponseParser {

    static final String NORMAL = "00";
    static final String NO_DATA = "03";
    private static final Pattern XML_REASON = Pattern.compile("<returnReasonCode>\\s*(\\d+)\\s*</returnReasonCode>");
    private static final Pattern RESULT_CODE = Pattern.compile("\\d{1,3}");

    private final ObjectMapper objectMapper;

    public PublicCourseApiResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Page parse(String body) {
        if (body == null || body.isBlank()) {
            throw new PublicCourseSourceException("PUBLIC_DATA_INVALID_RESPONSE");
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("<")) {
            throw xmlError(trimmed);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (JacksonException exception) {
            throw new PublicCourseSourceException("PUBLIC_DATA_INVALID_RESPONSE", exception);
        }
        JsonNode envelope = root.has("response") ? root.get("response") : root;
        String code = envelope.path("header").path("resultCode").asString("");
        if (NO_DATA.equals(code)) {
            return new Page(List.of(), 0);
        }
        if (!NORMAL.equals(code)) {
            throw new PublicCourseSourceException(resultCode(code));
        }
        JsonNode bodyNode = envelope.path("body");
        return new Page(items(bodyNode.path("items")), totalCount(bodyNode.path("totalCount")));
    }

    private static PublicCourseSourceException xmlError(String body) {
        Matcher matcher = XML_REASON.matcher(body);
        return new PublicCourseSourceException(
            matcher.find() ? resultCode(matcher.group(1)) : "PUBLIC_DATA_INVALID_RESPONSE");
    }

    private static String resultCode(String code) {
        return RESULT_CODE.matcher(code).matches() ? "PUBLIC_DATA_" + code : "PUBLIC_DATA_INVALID_RESPONSE";
    }

    private static List<Map<String, String>> items(JsonNode items) {
        JsonNode item = items.path("item");
        if (item.isObject()) {
            return List.of(row(item));
        }
        if (!item.isArray()) {
            return List.of();
        }
        List<Map<String, String>> rows = new ArrayList<>(item.size());
        for (JsonNode node : item) {
            if (node.isObject()) {
                rows.add(row(node));
            }
        }
        return rows;
    }

    private static Map<String, String> row(JsonNode node) {
        Map<String, String> row = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            JsonNode value = field.getValue();
            row.put(field.getKey(), value == null || value.isNull() ? null : value.asString());
        }
        return Collections.unmodifiableMap(row);
    }

    private static int totalCount(JsonNode node) {
        if (node.isNumber()) {
            return node.asInt();
        }
        String text = node.asString("").strip();
        if (text.matches("\\d{1,9}")) {
            return Integer.parseInt(text);
        }
        throw new PublicCourseSourceException("PUBLIC_DATA_INVALID_RESPONSE");
    }
}
