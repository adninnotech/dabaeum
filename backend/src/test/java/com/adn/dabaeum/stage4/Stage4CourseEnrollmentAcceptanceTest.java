package com.adn.dabaeum.stage4;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.api.CourseController;
import com.adn.dabaeum.course.api.CourseSessionController;
import com.adn.dabaeum.enrollment.api.EnrollmentController;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class Stage4CourseEnrollmentAcceptanceTest {

    private static final List<String> EXPECTED_MAPPINGS = List.of(
        "GET /api/v1/courses",
        "POST /api/v1/courses",
        "GET /api/v1/courses/{courseId}",
        "PUT /api/v1/courses/{courseId}",
        "POST /api/v1/courses/{courseId}/publish",
        "POST /api/v1/courses/{courseId}/close",
        // 관리자 정정 API. Stage 4 이후 추가됐다.
        "POST /api/v1/courses/{courseId}/reopen",
        "POST /api/v1/sessions/{sessionId}/reopen",
        "GET /api/v1/courses/{courseId}/sessions",
        "POST /api/v1/courses/{courseId}/sessions",
        "GET /api/v1/sessions/{sessionId}",
        "PUT /api/v1/sessions/{sessionId}",
        "GET /api/v1/courses/{courseId}/enrollments",
        "POST /api/v1/courses/{courseId}/enrollments",
        "POST /api/v1/courses/{courseId}/proxy-enrollments",
        "GET /api/v1/enrollments/{enrollmentId}",
        "POST /api/v1/enrollments/{enrollmentId}/approve",
        "POST /api/v1/enrollments/{enrollmentId}/reject",
        "POST /api/v1/enrollments/{enrollmentId}/cancel",
        "POST /api/v1/enrollments/{enrollmentId}/withdraw"
    );

    @Test
    void stage4ArtifactsExist() {
        assertThat(Files.exists(Path.of("src/docs/asciidoc/stage4.adoc"))).isTrue();
        assertThat(Files.exists(Path.of("scripts/verify-stage4-course-enrollment.sh"))).isTrue();
    }

    @Test
    void courseSessionAndEnrollmentControllersExposeTheirMappings() {
        List<String> mappings = new ArrayList<>();
        mappings.addAll(controllerMappings(CourseController.class));
        mappings.addAll(controllerMappings(CourseSessionController.class));
        mappings.addAll(controllerMappings(EnrollmentController.class));

        assertThat(mappings).containsExactlyInAnyOrderElementsOf(EXPECTED_MAPPINGS);
    }

    private List<String> controllerMappings(Class<?> controller) {
        String[] prefixes = controller.getAnnotation(RequestMapping.class).value();
        String prefix = prefixes.length == 0 ? "" : prefixes[0];
        List<String> mappings = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            add(mappings, prefix, method.getAnnotation(GetMapping.class), "GET");
            add(mappings, prefix, method.getAnnotation(PostMapping.class), "POST");
            add(mappings, prefix, method.getAnnotation(PutMapping.class), "PUT");
            add(mappings, prefix, method.getAnnotation(PatchMapping.class), "PATCH");
            add(mappings, prefix, method.getAnnotation(DeleteMapping.class), "DELETE");
        }
        return mappings;
    }

    private void add(List<String> mappings, String prefix, GetMapping mapping, String method) {
        if (mapping != null) {
            mappings.add(method + " " + prefix + path(mapping.value()));
        }
    }

    private void add(List<String> mappings, String prefix, PostMapping mapping, String method) {
        if (mapping != null) {
            mappings.add(method + " " + prefix + path(mapping.value()));
        }
    }

    private void add(List<String> mappings, String prefix, PutMapping mapping, String method) {
        if (mapping != null) {
            mappings.add(method + " " + prefix + path(mapping.value()));
        }
    }

    private void add(List<String> mappings, String prefix, PatchMapping mapping, String method) {
        if (mapping != null) {
            mappings.add(method + " " + prefix + path(mapping.value()));
        }
    }

    private void add(List<String> mappings, String prefix, DeleteMapping mapping, String method) {
        if (mapping != null) {
            mappings.add(method + " " + prefix + path(mapping.value()));
        }
    }

    private String path(String[] values) {
        return values.length == 0 ? "" : values[0];
    }
}
