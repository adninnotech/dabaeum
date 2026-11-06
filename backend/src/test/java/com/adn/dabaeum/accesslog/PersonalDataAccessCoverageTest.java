package com.adn.dabaeum.accesslog;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiResponse;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설계서 §10.2 — 관리자 권한으로 개인정보를 다루는 핸들러에 @PersonalDataAccess 가 빠지지 않았는지 본다.
 * 목록은 계획 2 Task 7 표가 정본이다. 새 개인정보 API 를 만들면 여기와 핸들러에 함께 더한다.
 */
class PersonalDataAccessCoverageTest {

    private static final Map<String, AccessLogAction> EXPECTED = Map.ofEntries(
        Map.entry("GET /api/v1/users", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/enrollments", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/learning-courses", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/inquiries", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/learning-stats", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/identities", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/roles", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/credentials", AccessLogAction.READ),
        Map.entry("GET /api/v1/users/{userId}/badges", AccessLogAction.READ),
        Map.entry("GET /api/v1/admin/badges", AccessLogAction.READ),
        Map.entry("POST /api/v1/admin/badges/{badgeId}/revoke", AccessLogAction.UPDATE),
        Map.entry("GET /api/v1/admin/enrollments", AccessLogAction.READ),
        Map.entry("GET /api/v1/admin/inquiries", AccessLogAction.READ),
        Map.entry("GET /api/v1/institutions/{institutionId}/enrollments", AccessLogAction.READ),
        Map.entry("GET /api/v1/instructors/{userId}/enrollment-status", AccessLogAction.READ),
        Map.entry("GET /api/v1/institutions/{institutionId}/inquiries", AccessLogAction.READ),
        Map.entry("GET /api/v1/instructors/{userId}/inquiries", AccessLogAction.READ),
        Map.entry("GET /api/v1/inquiries/{inquiryId}", AccessLogAction.READ),
        Map.entry("GET /api/v1/institutions/{institutionId}/instructors", AccessLogAction.READ),
        Map.entry("POST /api/v1/institutions/{institutionId}/learners/lookup", AccessLogAction.READ),
        Map.entry("GET /api/v1/courses/{courseId}/enrollments", AccessLogAction.READ),
        Map.entry("GET /api/v1/enrollments/{enrollmentId}", AccessLogAction.READ),
        Map.entry("POST /api/v1/users", AccessLogAction.UPDATE),
        Map.entry("PUT /api/v1/users/{userId}", AccessLogAction.UPDATE),
        Map.entry("PATCH /api/v1/users/{userId}/status", AccessLogAction.UPDATE),
        Map.entry("PATCH /api/v1/users/{userId}/memo", AccessLogAction.UPDATE),
        Map.entry("POST /api/v1/users/{userId}/password/reset", AccessLogAction.UPDATE),
        Map.entry("POST /api/v1/users/{userId}/identities", AccessLogAction.UPDATE),
        Map.entry("POST /api/v1/users/{userId}/roles", AccessLogAction.UPDATE),
        Map.entry("POST /api/v1/courses/{courseId}/proxy-enrollments", AccessLogAction.UPDATE),
        Map.entry("PATCH /api/v1/institutions/{institutionId}/instructors/{userId}", AccessLogAction.UPDATE),
        Map.entry("DELETE /api/v1/users/{userId}/identities/{identityId}", AccessLogAction.DELETE),
        Map.entry("DELETE /api/v1/users/{userId}/roles/{roleId}", AccessLogAction.DELETE),
        Map.entry("GET /api/v1/admin/administrators", AccessLogAction.READ),
        Map.entry("POST /api/v1/admin/administrators", AccessLogAction.UPDATE),
        Map.entry("GET /api/v1/admin/statistics/enrollments?format=csv", AccessLogAction.DOWNLOAD),
        Map.entry("GET /api/v1/admin/statistics/badges?format=csv", AccessLogAction.DOWNLOAD),
        Map.entry("GET /api/v1/admin/statistics/usage?format=csv", AccessLogAction.DOWNLOAD)
    );

    /** 아래 패턴에 걸리지만 개인정보 처리가 아니어서 빼는 핸들러와 이유. */
    private static final Map<String, String> EXEMPT = Map.of(
        "POST /api/v1/courses/{courseId}/enrollments", "학습자 본인 신청",
        "GET /api/v1/instructors/{userId}/courses", "강좌 정보",
        "GET /api/v1/instructors/{userId}/courses/stats", "강좌 통계",
        "PUT /api/v1/courses/{courseId}/instructors/{userId}", "강사 배정 관리",
        "DELETE /api/v1/courses/{courseId}/instructors/{userId}", "강사 배정 해제"
    );

    /** 이 모양의 경로는 남의 개인정보를 다룰 가능성이 높다. 새 핸들러가 걸리면 EXPECTED 또는 EXEMPT 에 넣어야 한다. */
    private static final List<Pattern> SUSPICIOUS = List.of(
        Pattern.compile("^/api/v1/users/\\{userId\\}(/.*)?$"),
        Pattern.compile("^/api/v1/admin/(users|enrollments|inquiries|administrators)(/.*)?$"),
        Pattern.compile("^/api/v1/institutions/\\{institutionId\\}/(enrollments|inquiries|instructors)(/.*)?$"),
        Pattern.compile("^/api/v1/instructors/\\{userId\\}(/.*)?$"),
        Pattern.compile("^/api/v1/courses/\\{courseId\\}/(enrollments|proxy-enrollments|instructors/.*)$")
    );

    private static Map<String, Method> handlers;

    @BeforeAll
    static void scanControllers() throws ClassNotFoundException {
        // 컨트롤러 대부분이 @Profile({"local", "dev"}) 라 프로필 없는 환경으로 스캔하면 빠진다.
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("local");
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        handlers = new HashMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.adn.dabaeum")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = base == null || base.path().length == 0 ? "" : base.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                // 같은 경로를 params 로 나눈 핸들러(통계 JSON·CSV)는 키에 params 를 붙여 구분한다.
                String params = mapping.params().length == 0 ? "" : "?" + String.join("&", mapping.params());
                for (var httpMethod : mapping.method()) {
                    for (String path : paths) {
                        handlers.put(httpMethod.name() + " " + prefix + path + params, method);
                    }
                }
            }
        }
    }

    @Test
    void scanFindsTheControllers() {
        // 스캔이 비면 아래 검사들이 모두 헛돈다.
        assertThat(handlers).hasSizeGreaterThan(150).containsKey("GET /api/v1/users/{userId}");
    }

    @Test
    void annotatedHandlersMatchTheAgreedListExactly() {
        Map<String, AccessLogAction> annotated = handlers.entrySet().stream()
            .filter(entry -> entry.getValue().isAnnotationPresent(PersonalDataAccess.class))
            .collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().getAnnotation(PersonalDataAccess.class).action()));

        assertThat(annotated).containsExactlyInAnyOrderEntriesOf(EXPECTED);
    }

    @Test
    void everySuspiciousHandlerIsAnnotatedOrExempt() {
        Set<String> missing = handlers.keySet().stream()
            .filter(key -> SUSPICIOUS.stream().anyMatch(p -> p.matcher(key.substring(key.indexOf(' ') + 1).split("\\?")[0]).matches()))
            .filter(key -> !EXPECTED.containsKey(key) && !EXEMPT.containsKey(key))
            .collect(Collectors.toSet());

        assertThat(missing).as("@PersonalDataAccess 누락 또는 EXEMPT 미등록").isEmpty();
    }

    @Test
    void targetFieldExistsOnTheResponseRows() {
        handlers.forEach((key, method) -> {
            PersonalDataAccess access = method.getAnnotation(PersonalDataAccess.class);
            if (access == null || access.targetField().isBlank()) {
                return;
            }
            assertThat(componentNames(rowType(method))).as(key).contains(access.targetField());
        });
    }

    private static Class<?> rowType(Method method) {
        ResolvableType type = ResolvableType.forMethodReturnType(method);
        if (type.toClass() == ResponseEntity.class) {
            type = type.getGeneric(0);
        }
        ResolvableType data = type.toClass() == ApiResponse.class
            ? type.getGeneric(0)
            : ResolvableType.forType(component(type.toClass(), "data").getGenericType());
        return List.class.isAssignableFrom(data.toClass()) ? data.getGeneric(0).toClass() : data.toClass();
    }

    private static RecordComponent component(Class<?> type, String name) {
        return Arrays.stream(type.getRecordComponents())
            .filter(component -> component.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError(type + " 에 data 가 없다"));
    }

    private static Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }
}
