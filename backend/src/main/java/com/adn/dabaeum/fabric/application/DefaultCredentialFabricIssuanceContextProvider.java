package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialGroup;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialIdentifierProvider;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialStatusListAllocator;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 이수 집계에서 서버 소유 VC 문서를 생성한다. */
public final class DefaultCredentialFabricIssuanceContextProvider
    implements CredentialFabricIssuanceContextProvider {

    private final CredentialRepository credentialRepository;
    private final CredentialGroupRepository groupRepository;
    private final CompletionRepository completionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CredentialDocumentFactory documentFactory;
    private final CredentialIdentifierProvider identifierProvider;
    private final CredentialStatusListAllocator statusListAllocator;

    public DefaultCredentialFabricIssuanceContextProvider(
        CredentialRepository credentialRepository,
        CredentialGroupRepository groupRepository,
        CompletionRepository completionRepository,
        EnrollmentRepository enrollmentRepository,
        CourseRepository courseRepository,
        CredentialDocumentFactory documentFactory,
        CredentialIdentifierProvider identifierProvider,
        CredentialStatusListAllocator statusListAllocator
    ) {
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.groupRepository = Objects.requireNonNull(groupRepository);
        this.completionRepository = Objects.requireNonNull(completionRepository);
        this.enrollmentRepository = Objects.requireNonNull(enrollmentRepository);
        this.courseRepository = Objects.requireNonNull(courseRepository);
        this.documentFactory = Objects.requireNonNull(documentFactory);
        this.identifierProvider = Objects.requireNonNull(identifierProvider);
        this.statusListAllocator = Objects.requireNonNull(statusListAllocator);
    }

    @Override
    public CredentialFabricIssuanceContext resolve(UUID credentialId, Instant issuedAt) {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Credential credential = credentialRepository.findById(credentialId)
            .orElseThrow(() -> new IllegalStateException("Credential anchor target is missing"));
        CredentialGroup group = groupRepository.findById(credential.credentialGroupId())
            .orElseThrow(() -> new IllegalStateException("Credential group is missing"));
        Completion completion = completionRepository.findById(group.completionId())
            .orElseThrow(() -> new IllegalStateException("Completion is missing"));
        Enrollment enrollment = enrollmentRepository.findById(completion.enrollmentId())
            .orElseThrow(() -> new IllegalStateException("Enrollment is missing"));
        Course course = courseRepository.findById(enrollment.courseId())
            .orElseThrow(() -> new IllegalStateException("Course is missing"));
        Instant validFrom = credential.validFrom() == null ? issuedAt : credential.validFrom();
        // 재시도 시 같은 칸을 다시 받는다. 칸은 VC 본문에 박혀 서명되므로 한 번 정하면 바뀌지 않는다.
        CredentialStatusListEntry statusListEntry = statusListAllocator.allocate(
            credential.id(), course.institutionId());
        CredentialDocument document = documentFactory.create(
            credential.id(), statusListEntry, course.institutionId(), enrollment.userId(), completion.id(),
            enrollment.id(), course.id(), validFrom, credential.validUntil(), completion.completedAt(),
            completion.attendanceRate(), completion.completedMinutes(), completion.creditValue());
        return new CredentialFabricIssuanceContext(document,
            identifierProvider.issuerIdentifier(course.institutionId()),
            identifierProvider.subjectIdentifier(enrollment.userId()), issuedAt);
    }
}
