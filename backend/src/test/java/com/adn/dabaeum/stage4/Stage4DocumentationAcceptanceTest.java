package com.adn.dabaeum.stage4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Stage4DocumentationAcceptanceTest {

    private static final Set<String> STAGE4_OPERATION_IDS = Set.of(
        "listCourses", "createCourse", "getCourse", "updateCourse", "publishCourse", "closeCourse",
        "listCourseSessions", "createCourseSession", "getCourseSession", "updateCourseSession",
        "listCourseEnrollments", "createEnrollment", "createProxyEnrollment", "getEnrollment",
        "approveEnrollment", "rejectEnrollment", "cancelEnrollment", "withdrawEnrollment");
    private static final Set<String> CREATED_OPERATION_IDS = Set.of(
        "createCourse", "createCourseSession", "createEnrollment", "createProxyEnrollment");

    @Test
    void stage4AsciiDocIncludesAll18OperationSnippets() {
        assertThat(Files.exists(Path.of("src/docs/asciidoc/stage4.adoc"))).isTrue();
        String document = read("src/docs/asciidoc/stage4.adoc");
        for (String operation : List.of(
            "course-list", "course-create", "course-get", "course-update", "course-publish",
            "course-close", "course-session-list", "course-session-create", "course-session-get",
            "course-session-update", "enrollment-list", "enrollment-create", "enrollment-proxy-create",
            "enrollment-get", "enrollment-approve", "enrollment-reject", "enrollment-cancel",
            "enrollment-withdraw")) {
            assertThat(document).contains(operation);
        }
    }

    private String read(String file) {
        try {
            return Files.readString(Path.of(file));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
