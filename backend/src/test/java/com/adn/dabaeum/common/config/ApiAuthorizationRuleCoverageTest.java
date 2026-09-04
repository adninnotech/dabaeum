package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.config.ApiAuthorizationRules.Rule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * OpenAPI 의 모든 operation 에 명시적 인가 규칙이 있는지 검사한다.
 *
 * <p>규칙이 없는 엔드포인트는 {@code anyRequest().authenticated()} 로 떨어져 역할 검사 없이
 * 통과한다. 새 엔드포인트를 추가하면서 {@link ApiAuthorizationRules} 를 빠뜨리면 이 테스트가
 * 실패한다.
 */
class ApiAuthorizationRuleCoverageTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final PathPatternParser PARSER = new PathPatternParser();

    @Test
    void everyContractOperationHasAnExplicitAuthorizationRule() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        OpenAPI openAPI = new OpenAPIV3Parser()
            .readLocation(CONTRACT.toUri().toString(), null, options).getOpenAPI();
        assertThat(openAPI).isNotNull();

        List<String> uncovered = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            // 경로 변수는 실제 요청처럼 임의 세그먼트로 치환해 패턴 매칭한다.
            String requestPath = "/api/v1" + entry.getKey().replaceAll("\\{[^}]+}", "x");
            for (Map.Entry<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> op
                : entry.getValue().readOperationsMap().entrySet()) {
                HttpMethod method = HttpMethod.valueOf(op.getKey().name());
                if (!covered(method, requestPath)) {
                    uncovered.add(method + " " + entry.getKey());
                }
            }
        }
        assertThat(uncovered)
            .as("인가 규칙이 없는 operation — ApiAuthorizationRules 에 추가할 것")
            .isEmpty();
    }

    private static boolean covered(HttpMethod method, String requestPath) {
        PathContainer path = PathContainer.parsePath(requestPath);
        for (Rule rule : ApiAuthorizationRules.rules()) {
            if (rule.method() != null && !rule.method().equals(method)) {
                continue;
            }
            for (String pattern : rule.patterns()) {
                if (PARSER.parse(pattern).matches(path)) {
                    return true;
                }
            }
        }
        return false;
    }
}
