package com.adn.dabaeum;

import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceAdjustmentRepository;
import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequestRepository;
import com.adn.dabaeum.instructor.domain.InstructorApplicationRepository;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseAttachmentRepository;
import com.adn.dabaeum.course.application.CourseSessionIdGenerator;
import com.adn.dabaeum.enrollment.application.EnrollmentIdGenerator;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.completion.application.CompletionIdGenerator;
import com.adn.dabaeum.completion.domain.CompletionOutboxRepository;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.correction.domain.AdminCorrectionRepository;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialStatusListRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.LegacySignKeyBackfillRepository;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
import com.adn.dabaeum.credential.application.KmsOperationAdjudicationService;
import com.adn.dabaeum.credential.application.SigningKeyCompromiseService;
import com.adn.dabaeum.credential.application.SigningKeyLifecycleService;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningService;
import com.adn.dabaeum.credential.application.SigningKeyRotationService;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class ApplicationContextTest {

    @MockitoBean
    InstitutionRepository institutionRepository;

    @MockitoBean
    InstitutionSigningKeyRequestRepository institutionSigningKeyRequestRepository;

    @MockitoBean
    InstructorApplicationRepository instructorApplicationRepository;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    UserInterestRepository userInterestRepository;

    @MockitoBean
    UserAgreementRepository userAgreementRepository;

    @MockitoBean
    UserIdentityRepository userIdentityRepository;

    @MockitoBean
    UserRoleRepository userRoleRepository;

    @MockitoBean
    CourseRepository courseRepository;

    @MockitoBean
    com.adn.dabaeum.classification.domain.CourseClassificationRepository courseClassificationRepository;

    @MockitoBean
    CourseInstructorRepository courseInstructorRepository;

    @MockitoBean
    CourseSessionRepository courseSessionRepository;

    @MockitoBean
    CourseAttachmentRepository courseAttachmentRepository;

    @MockitoBean
    CourseSessionIdGenerator courseSessionIdGenerator;

    @MockitoBean
    EnrollmentRepository enrollmentRepository;

    @MockitoBean
    EnrollmentIdGenerator enrollmentIdGenerator;

    @MockitoBean
    AttendanceQrTokenRepository attendanceQrTokenRepository;

    @MockitoBean
    AttendanceQrTokenCodec attendanceQrTokenCodec;

    @MockitoBean
    AttendanceApplicationService attendanceApplicationService;

    @MockitoBean
    AttendanceRepository attendanceRepository;

    @MockitoBean
    AttendanceAdjustmentRepository attendanceAdjustmentRepository;

    @MockitoBean
    CompletionRepository completionRepository;

    @MockitoBean
    CompletionOutboxRepository completionOutboxRepository;

    @MockitoBean
    CompletionIdGenerator completionIdGenerator;

    @MockitoBean
    CredentialApplicationService credentialApplicationService;

    @MockitoBean
    CredentialVerificationService credentialVerificationService;

    @MockitoBean
    SigningKeyProvisioningService signingKeyProvisioningService;

    @MockitoBean
    SigningKeyRotationService signingKeyRotationService;

    @MockitoBean
    SigningKeyLifecycleService signingKeyLifecycleService;

    @MockitoBean
    SigningKeyCompromiseService signingKeyCompromiseService;

    @MockitoBean
    KmsOperationAdjudicationService kmsOperationAdjudicationService;

    @MockitoBean
    StoredFileRepository storedFileRepository;

    @MockitoBean
    CredentialGroupRepository credentialGroupRepository;

    @MockitoBean
    AdminCorrectionRepository adminCorrectionRepository;

    @MockitoBean
    CredentialStatusListRepository credentialStatusListRepository;

    @MockitoBean
    InstitutionSignKeyRepository institutionSignKeyRepository;

    @MockitoBean
    InstitutionSignKeyVersionRepository institutionSignKeyVersionRepository;

    @MockitoBean
    LegacySignKeyBackfillRepository legacySignKeyBackfillRepository;

    @Test
    void contextLoads() {
    }
}
