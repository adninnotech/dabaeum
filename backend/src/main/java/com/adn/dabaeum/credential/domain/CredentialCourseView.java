package com.adn.dabaeum.credential.domain;

import java.util.UUID;

public record CredentialCourseView(
    UUID credentialId,
    UUID courseId,
    String courseTitle,
    String courseCode,
    String institutionName
) {
}
