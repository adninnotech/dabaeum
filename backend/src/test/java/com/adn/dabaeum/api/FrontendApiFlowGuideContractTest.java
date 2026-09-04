package com.adn.dabaeum.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 프론트엔드 호출 흐름 가이드가 OpenAPI 계약과 어긋나지 않는지 검증한다.
 *
 * <p>가이드 HTML은 단계 데이터를 스크립트에서 만들어 내므로 정적 마크업을 파싱하지 않는다.
 * 대신 Markdown 다운로드본이 단계마다 남기는 {@code <!-- api-step ... -->} 마커를 기준으로 삼고,
 * HTML은 같은 operationId 집합을 담고 있는지만 확인한다.
 */
class FrontendApiFlowGuideContractTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final Path MARKDOWN =
        Path.of("docs/api/dabaeum-frontend-api-flow.md").toAbsolutePath();
    private static final Path HTML =
        Path.of("docs/api/guide/index.html").toAbsolutePath();
    private static final Path README =
        Path.of("docs/api/README.md").toAbsolutePath();

    /** 가이드가 단계마다 남기는 기계 판독용 마커. */
    private static final Pattern STEP = Pattern.compile(
        "<!-- api-step operationId=(\\S+) method=(\\S+) path=(\\S+) success=(\\d+) "
            + "request=(\\S+) store=(\\S+) role=(\\S+) policy=(\\S+) -->"
    );

    /** 지도 레인에 쓰는 호출 주체. 오타가 들어가면 지도에서 조용히 사라진다. */
    private static final Set<String> ROLES =
        Set.of("learner", "instructor", "admin", "public");

    /** 흐름에서 의도적으로 제외하는 범위. */
    private static final Set<String> EXCLUDED_TAGS = Set.of("Badge");

    @Test
    void guideStepsMatchCanonicalOpenApiContract() throws Exception {
        OpenAPI openAPI = parsedContract();
        List<GuideStep> steps = steps(Files.readString(MARKDOWN));

        assertThat(steps).isNotEmpty();
        steps.forEach(step -> {
            Operation operation = assertOpenApiContains(openAPI, step);
            assertRequestBodyPresence(operation, step);
            assertStoredResponsePaths(openAPI, operation, step);
            assertThat(ROLES).as("unknown role in " + step.operationId())
                .contains(step.role());
            assertThat(step.policy()).as("policy for " + step.operationId())
                .isNotBlank();
        });
    }

    /**
     * 인덱스가 계약의 모든 operation을 덮는지 확인한다.
     * 새 API를 추가하고 가이드에 적지 않으면 여기서 걸린다.
     */
    @Test
    void guideIndexCoversEveryDocumentedOperation() throws Exception {
        OpenAPI openAPI = parsedContract();
        String markdown = Files.readString(MARKDOWN);

        List<String> missing = new ArrayList<>();
        openAPI.getPaths().forEach((path, item) ->
            item.readOperationsMap().forEach((method, operation) -> {
                String operationId = operation.getOperationId();
                assertThat(operationId).as("operationId for " + method + " " + path)
                    .isNotBlank();
                if (!markdown.contains("`" + operationId + "`")) {
                    missing.add(method + " " + path + " (" + operationId + ")");
                }
            }));

        assertThat(missing)
            .as("가이드 인덱스에서 빠진 operation")
            .isEmpty();
    }

    /** 흐름 단계는 제외 범위를 다루지 않는다. */
    @Test
    void guideStepsSkipExcludedScope() throws Exception {
        OpenAPI openAPI = parsedContract();
        Set<String> excludedOperationIds = new LinkedHashSet<>();
        openAPI.getPaths().values().forEach(item ->
            item.readOperationsMap().values().forEach(operation -> {
                List<String> tags = operation.getTags() == null
                    ? List.of() : operation.getTags();
                if (tags.stream().anyMatch(EXCLUDED_TAGS::contains)) {
                    excludedOperationIds.add(operation.getOperationId());
                }
            }));

        assertThat(excludedOperationIds).isNotEmpty();
        assertThat(steps(Files.readString(MARKDOWN)))
            .noneMatch(step -> excludedOperationIds.contains(step.operationId()));
    }

    /** HTML과 Markdown이 같은 호출 집합을 다루는지 확인한다. */
    @Test
    void htmlGuideCoversTheSameOperationsAsMarkdown() throws Exception {
        String html = Files.readString(HTML);
        Set<String> operationIds = new LinkedHashSet<>();
        steps(Files.readString(MARKDOWN))
            .forEach(step -> operationIds.add(step.operationId()));

        assertThat(operationIds).isNotEmpty();
        assertThat(operationIds)
            .allSatisfy(operationId -> assertThat(html)
                .as("HTML 가이드에 없는 operation: " + operationId)
                .contains(operationId));
    }

    @Test
    void guideContainsNoSecretValues() throws Exception {
        String all = Files.readString(MARKDOWN) + Files.readString(HTML);

        assertThat(all)
            .doesNotContain(
                "adninnotech",
                "BEGIN PRIVATE KEY",
                "JASYPT_ENCRYPTOR_PASSWORD",
                "DABAEUM_DB_PASSWORD",
                "DABAEUM_SSH_PASSWORD"
            )
            .doesNotMatch("(?s).*Bearer\\s+[A-Za-z0-9_-]{20,}.*");
    }

    @Test
    void apiReadmeLinksSwaggerGuideAndMarkdownDownload() throws Exception {
        String readme = Files.readString(README);

        assertThat(readme)
            .contains("/swagger-ui/index.html")
            .contains("/api-guide/index.html")
            .contains("/api-guide/dabaeum-frontend-api-flow.md")
            .contains("dabaeum-frontend-api-flow.md");
    }

    private static OpenAPI parsedContract() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(
            CONTRACT.toUri().toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        return result.getOpenAPI();
    }

    private static Operation assertOpenApiContains(
        OpenAPI openAPI,
        GuideStep step
    ) {
        assertThat(openAPI.getPaths()).containsKey(step.path());
        PathItem pathItem = openAPI.getPaths().get(step.path());
        PathItem.HttpMethod method = PathItem.HttpMethod.valueOf(step.method());
        Operation operation = pathItem.readOperationsMap().get(method);

        assertThat(operation).as(step.method() + " " + step.path()).isNotNull();
        assertThat(operation.getOperationId()).isEqualTo(step.operationId());
        assertThat(operation.getResponses()).containsKey(
            Integer.toString(step.success()));
        return operation;
    }

    private static void assertRequestBodyPresence(
        Operation operation,
        GuideStep step
    ) {
        if ("-".equals(step.request())) {
            assertThat(operation.getRequestBody())
                .as(step.operationId() + " should not carry a request body")
                .isNull();
            return;
        }
        assertThat(operation.getRequestBody())
            .as(step.operationId() + " should carry a request body")
            .isNotNull();
    }

    private static void assertStoredResponsePaths(
        OpenAPI openAPI,
        Operation operation,
        GuideStep step
    ) {
        if ("-".equals(step.store())) {
            return;
        }
        for (String storePath : step.store().split("\\|")) {
            assertStoredResponsePath(openAPI, operation, step, storePath);
        }
    }

    private static void assertStoredResponsePath(
        OpenAPI openAPI,
        Operation operation,
        GuideStep step,
        String storePath
    ) {
        ApiResponse response = operation.getResponses()
            .get(Integer.toString(step.success()));
        if (response.get$ref() != null) {
            String name = response.get$ref().substring(
                response.get$ref().lastIndexOf('/') + 1);
            response = openAPI.getComponents().getResponses().get(name);
        }
        assertThat(response.getContent()).as(step.operationId()).isNotNull();
        Schema<?> schema = response.getContent()
            .get("application/json").getSchema();
        for (String segment : storePath.split("\\.")) {
            schema = resolve(openAPI, schema);
            boolean array = segment.endsWith("[]");
            String property = array
                ? segment.substring(0, segment.length() - 2)
                : segment;
            assertThat(schema.getProperties())
                .as(step.operationId() + " response path " + storePath)
                .containsKey(property);
            schema = (Schema<?>) schema.getProperties().get(property);
            if (array) {
                schema = schema.getItems();
            }
        }
    }

    private static Schema<?> resolve(OpenAPI openAPI, Schema<?> schema) {
        if (schema.get$ref() == null) {
            return schema;
        }
        String name = schema.get$ref().substring(
            schema.get$ref().lastIndexOf('/') + 1);
        return openAPI.getComponents().getSchemas().get(name);
    }

    private static List<GuideStep> steps(String source) {
        List<GuideStep> result = new ArrayList<>();
        Matcher matcher = STEP.matcher(source);
        while (matcher.find()) {
            result.add(new GuideStep(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                Integer.parseInt(matcher.group(4)),
                matcher.group(5),
                matcher.group(6),
                matcher.group(7),
                matcher.group(8)
            ));
        }
        return result;
    }

    private record GuideStep(
        String operationId,
        String method,
        String path,
        int success,
        String request,
        String store,
        String role,
        String policy
    ) {
    }
}
