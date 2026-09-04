package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.UUID;

public record CredentialCourseViewRow(
    UUID credentialId,
    UUID courseId,
    String courseTitle,
    String courseCode,
    String institutionName
) {
}
