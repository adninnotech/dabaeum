package com.adn.dabaeum.instructor.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.instructor.domain.InstructorApplication;
import com.adn.dabaeum.instructor.domain.InstructorApplicationRepository;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstructorApplicationServiceTest {

    private static final UUID APPLICATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INSTITUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ROLE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Mock InstructorApplicationRepository repository;
    @Mock UserRepository userRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock UserRoleRepository roleRepository;
    @Mock InstructorApplicationIdGenerator idGenerator;
    @Mock com.adn.dabaeum.role.application.RoleIdGenerator roleIdGenerator;

    private InstructorApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInstructorApplicationService(
            repository, userRepository, institutionRepository, roleRepository,
            idGenerator, roleIdGenerator, new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void applicantCreatesOwnPendingApplicationWithOptionalMessage() {
        stubApplicantAndInstitution();
        when(idGenerator.generate()).thenReturn(APPLICATION_ID);

        InstructorApplicationView result = service.apply(
            new ApplyInstructorCommand(INSTITUTION_ID, "  강의 경험  "),
            learnerContext()
        );

        ArgumentCaptor<InstructorApplication> captor =
            ArgumentCaptor.forClass(InstructorApplication.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().applicationMessage()).isEqualTo("강의 경험");
        assertThat(captor.getValue().status()).isEqualTo(InstructorApplicationStatus.PENDING);
        assertThat(result.applicantEmail()).isEqualTo("learner@example.com");
    }

    @Test
    void applicantCannotDuplicatePendingOrApplyWhenAlreadyInstructor() {
        stubApplicantAndInstitution();
        when(repository.findPending(USER_ID, INSTITUTION_ID))
            .thenReturn(Optional.of(pending()));
        assertError(() -> service.apply(
            new ApplyInstructorCommand(INSTITUTION_ID, null), learnerContext()
        ), 409, ApiErrorCode.INSTRUCTOR_APPLICATION_CONFLICT);

        when(roleRepository.findByUserIdAndInstitution(USER_ID, INSTITUTION_ID))
            .thenReturn(List.of(new UserRoleAssignment(
                ROLE_ID, USER_ID, INSTITUTION_ID, UserRole.INSTRUCTOR, NOW
            )));
        assertError(() -> service.apply(
            new ApplyInstructorCommand(INSTITUTION_ID, null), learnerContext()
        ), 409, ApiErrorCode.INSTRUCTOR_APPLICATION_CONFLICT);
    }

    @Test
    void institutionAdminApprovesPendingAndAddsScopedInstructorRole() {
        when(repository.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(pending()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        when(roleRepository.saveIfAbsent(any())).thenReturn(true);
        when(repository.updateReview(any(), any())).thenReturn(true);

        InstructorApplicationView result = service.approve(APPLICATION_ID, adminContext());

        verify(roleRepository).saveIfAbsent(new UserRoleAssignment(
            ROLE_ID, USER_ID, INSTITUTION_ID, UserRole.INSTRUCTOR, NOW
        ));
        assertThat(result.status()).isEqualTo(InstructorApplicationStatus.APPROVED);
        assertThat(result.reviewedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void approvalConvergesWhenRoleAlreadyExistsAndRejectRequiresReason() {
        when(repository.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(pending()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roleRepository.findByUserIdAndInstitution(USER_ID, INSTITUTION_ID))
            .thenReturn(List.of(new UserRoleAssignment(
                ROLE_ID, USER_ID, INSTITUTION_ID, UserRole.INSTRUCTOR, NOW
            )));
        when(repository.updateReview(any(), any())).thenReturn(true);

        service.approve(APPLICATION_ID, adminContext());

        verify(roleRepository, never()).saveIfAbsent(any());
        assertError(() -> service.reject(
            new RejectInstructorApplicationCommand(APPLICATION_ID, "  "), adminContext()
        ), 422, ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void otherInstitutionAdminCannotReviewAndCompletedApplicationCannotChange() {
        when(repository.findByIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(pending()));
        AuthenticatedUserContext other = new AuthenticatedUserContext(
            ADMIN_ID, "LOCAL", Set.of(new AuthenticatedRole(
                "INSTITUTION_ADMIN", UUID.randomUUID()
            ))
        );
        assertError(() -> service.approve(APPLICATION_ID, other),
            403, ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);

        when(repository.findByIdForUpdate(APPLICATION_ID))
            .thenReturn(Optional.of(approved()));
        assertError(() -> service.approve(APPLICATION_ID, adminContext()),
            409, ApiErrorCode.INSTRUCTOR_APPLICATION_STATUS_CONFLICT);
    }

    @Test
    void approvalConvergesAfterConflictSafeRoleInsert() {
        when(repository.findByIdForUpdate(APPLICATION_ID))
            .thenReturn(Optional.of(pending()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        when(roleRepository.saveIfAbsent(any())).thenReturn(false);
        when(roleRepository.findByUserIdAndInstitution(USER_ID, INSTITUTION_ID))
            .thenReturn(List.of())
            .thenReturn(List.of(new UserRoleAssignment(
                ROLE_ID, USER_ID, INSTITUTION_ID, UserRole.INSTRUCTOR, NOW
            )));
        when(repository.updateReview(any(), any())).thenReturn(true);

        assertThat(service.approve(APPLICATION_ID, adminContext()).status())
            .isEqualTo(InstructorApplicationStatus.APPROVED);
    }

    private void stubApplicantAndInstitution() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(institutionRepository.findActiveById(INSTITUTION_ID))
            .thenReturn(Optional.of(institution()));
        when(repository.findPending(USER_ID, INSTITUTION_ID)).thenReturn(Optional.empty());
        when(roleRepository.findByUserIdAndInstitution(USER_ID, INSTITUTION_ID))
            .thenReturn(List.of());
    }

    private User activeUser() {
        return new User(USER_ID, "학습자", "learner@example.com", "010-0000-0000",
            null, UserStatus.ACTIVE, null, NOW, NOW);
    }

    private Institution institution() {
        return new Institution(INSTITUTION_ID, "INST-1", "기관", null, null,
            null, null, null, InstitutionStatus.ACTIVE, NOW, NOW, null);
    }

    private InstructorApplication pending() {
        return new InstructorApplication(APPLICATION_ID, USER_ID, INSTITUTION_ID,
            InstructorApplicationStatus.PENDING, "경력", null, null,
            NOW, null, NOW, NOW);
    }

    private InstructorApplication approved() {
        return new InstructorApplication(APPLICATION_ID, USER_ID, INSTITUTION_ID,
            InstructorApplicationStatus.APPROVED, "경력", null, ADMIN_ID,
            NOW, NOW, NOW, NOW);
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext adminContext() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private void assertError(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
        int status,
        ApiErrorCode code
    ) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.status().value()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
        });
    }
}
