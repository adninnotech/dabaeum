package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

@Component
public class CredentialDocumentFactory {

    private static final String W3C_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final List<String> TYPES = List.of(
        "VerifiableCredential", "LifelongEducationCompletionCredential");

    private final ObjectMapper objectMapper;
    private final CredentialIdentifierProvider identifierProvider;

    public CredentialDocumentFactory(
        ObjectMapper objectMapper,
        CredentialIdentifierProvider identifierProvider
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.identifierProvider = Objects.requireNonNull(identifierProvider, "identifierProvider");
    }

    public CredentialDocument create(
        UUID credentialId,
        CredentialStatusListEntry statusListEntry,
        UUID institutionId,
        UUID userId,
        UUID completionId,
        UUID enrollmentId,
        UUID courseId,
        Instant validFrom,
        Instant validUntil,
        Instant completedAt,
        BigDecimal attendanceRate,
        int completedMinutes,
        BigDecimal creditValue
    ) {
        Objects.requireNonNull(statusListEntry, "statusListEntry");
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(completedAt, "completedAt");
        if (validUntil != null && !validFrom.isBefore(validUntil)) {
            throw new IllegalArgumentException("validFrom must be before validUntil");
        }
        BigDecimal normalizedAttendanceRate = scaleTwo(attendanceRate, "attendanceRate");
        if (normalizedAttendanceRate.signum() < 0
            || normalizedAttendanceRate.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("attendanceRate must be between 0 and 100");
        }
        if (completedMinutes < 0) {
            throw new IllegalArgumentException("completedMinutes must not be negative");
        }
        BigDecimal normalizedCreditValue = creditValue == null
            ? null : scaleTwo(creditValue, "creditValue");
        if (normalizedCreditValue != null && normalizedCreditValue.signum() < 0) {
            throw new IllegalArgumentException("creditValue must not be negative");
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("@context", List.of(W3C_CONTEXT, identifierProvider.contextIdentifier()));
        document.put("id", identifierProvider.credentialIdentifier(credentialId));
        document.put("type", TYPES);
        document.put("issuer", identifierProvider.issuerIdentifier(institutionId));
        document.put("validFrom", validFrom.toString());
        if (validUntil != null) {
            document.put("validUntil", validUntil.toString());
        }

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", identifierProvider.subjectIdentifier(userId));
        subject.put("completionId", completionId.toString());
        subject.put("enrollmentId", enrollmentId.toString());
        subject.put("courseId", courseId.toString());
        subject.put("completedAt", completedAt.toString());
        subject.put("attendanceRate", normalizedAttendanceRate);
        subject.put("completedMinutes", completedMinutes);
        if (normalizedCreditValue != null) {
            subject.put("creditValue", normalizedCreditValue);
        }
        document.put("credentialSubject", subject);

        // W3C Bitstring Status List v1.0. 검증자는 statusListCredential 을 받아 statusListIndex 번째
        // 비트로 폐기 여부를 확인한다. 어느 수료증을 봤는지는 발급기관에 드러나지 않는다.
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", identifierProvider.statusListEntryIdentifier(statusListEntry));
        status.put("type", "BitstringStatusListEntry");
        status.put("statusPurpose", "revocation");
        status.put("statusListIndex", Integer.toString(statusListEntry.index()));
        status.put("statusListCredential",
            identifierProvider.statusListCredentialIdentifier(statusListEntry.listId()));
        document.put("credentialStatus", status);

        return new CredentialDocument(canonicalJson(document));
    }

    private String canonicalJson(Map<String, Object> document) {
        return objectMapper.writer()
            .without(SerializationFeature.INDENT_OUTPUT,
                SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .writeValueAsString(document);
    }

    private BigDecimal scaleTwo(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must have at most two decimal places");
        }
    }
}
