package com.adn.dabaeum.attendance.infrastructure.dadaegu;

import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebGateway;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebSession;
import com.adn.dabaeum.attendance.application.port.DadaeguMobileWebUnavailableException;
import com.adn.dabaeum.attendance.config.DadaeguAttendProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 다대구 모바일웹 로그인을 JDK HttpClient 로 부른다. 새 의존성 없음.
 *
 * <p>흐름: {@code init} 으로 학생 한 명분 sessionKey 를 받아 {@code didapp://} 딥링크를 만들고,
 * 학생이 앱에서 인증할 때까지 {@code chk} 를 폴링한다. 학생마다 init 을 새로 부르므로
 * 같은 QR 을 여러 명이 찍어도 서로 간섭하지 않는다.
 */
@Component
@Profile({"local", "dev"})
public class JdkDadaeguMobileWebGateway implements DadaeguMobileWebGateway {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DadaeguAttendProperties properties;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public JdkDadaeguMobileWebGateway(DadaeguAttendProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Override
    public DadaeguMobileWebSession init(String siteId) {
        requireSiteId(siteId);
        JsonNode json = read(post(properties.initUrl(), fields(
            "siteId", siteId,
            "flag", "dqrp",
            "requiredVC", properties.requiredVc(),
            "subVC", "",
            "origin", properties.siteOrigin()
        )));
        String sessionKey = text(json, "sessionKey");
        if (sessionKey.isBlank()) {
            throw new DadaeguMobileWebUnavailableException(
                "dadaegu init response has no sessionKey", null);
        }
        return new DadaeguMobileWebSession(
            sessionKey,
            deepLink(sessionKey, text(json, "url"), properties.requiredVc())
        );
    }

    @Override
    public Optional<String> poll(String siteId, String sessionKey) {
        requireSiteId(siteId);
        return didOf(post(properties.chkUrl(), fields(
            "sessionKey", sessionKey,
            "siteId", siteId,
            "origin", properties.siteOrigin()
        )));
    }

    private static void requireSiteId(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("siteId is required");
        }
    }

    /** 폰 브라우저가 이 링크를 열면 OS 가 다대구 앱을 띄운다. */
    static String deepLink(String sessionKey, String url, String requiredVc) {
        return "didapp://mobileweblogin?" + form(fields(
            "sessionKey", sessionKey,
            "flag", "dqrp",
            "url", url,
            "requiredVC", requiredVc,
            "subVC", ""
        ));
    }

    /**
     * chk 응답에서 DID 암호문을 꺼낸다. 아직 인증 전이면 empty.
     * returnData 는 객체로도, JSON 문자열로도 온다(실측) — 둘 다 읽는다.
     */
    static Optional<String> didOf(String body) {
        JsonNode json;
        try {
            json = read(body);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (!json.isObject() || !json.hasNonNull("success")
            || !json.get("success").asBoolean()) {
            return Optional.empty();
        }
        if ("Failed".equals(text(json, "returnUrl"))) {
            return Optional.empty();
        }
        JsonNode data = json.get("returnData");
        if (data == null || data.isNull()) {
            return Optional.empty();
        }
        if (data.isTextual()) {
            try {
                data = read(data.asText());
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
        String did = text(data, "did");
        return did.isBlank() ? Optional.empty() : Optional.of(did);
    }

    private String post(String url, Map<String, String> body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", properties.userAgent())
            .header("Origin", properties.siteOrigin())
            .header("Referer", properties.siteOrigin() + "/")
            .POST(HttpRequest.BodyPublishers.ofString(form(body), StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new DadaeguMobileWebUnavailableException(
                    "dadaegu responded " + response.statusCode(), null);
            }
            return response.body();
        } catch (IOException exception) {
            throw new DadaeguMobileWebUnavailableException("dadaegu request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DadaeguMobileWebUnavailableException(
                "dadaegu request interrupted", exception);
        }
    }

    /** 다대구가 파라미터 순서를 보는지 확인하지 않았으므로 넣은 순서를 지킨다. */
    private static Map<String, String> fields(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(keyValues[index], keyValues[index + 1]);
        }
        return map;
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    private static JsonNode read(String body) {
        return JSON.readTree(body);
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : "";
    }
}
