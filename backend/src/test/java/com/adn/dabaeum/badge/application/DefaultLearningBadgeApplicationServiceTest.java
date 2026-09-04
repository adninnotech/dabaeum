package com.adn.dabaeum.badge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.completion.domain.CompletionRepository;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.credential.domain.CredentialGroupRepository;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;

class DefaultLearningBadgeApplicationServiceTest {

    private static final UUID BADGE_ID = UUID.randomUUID();
    private static final UUID CREDENTIAL_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();

    private LearningBadgeRepository badgeRepository;
    private CredentialRepository credentialRepository;
    private CredentialGroupRepository groupRepository;
    private CompletionRepository completionRepository;
    private EnrollmentRepository enrollmentRepository;
    private CourseRepository courseRepository;
    private AuthorizationPolicy authorizationPolicy;
    private DefaultLearningBadgeApplicationService service;

    @BeforeEach
    void setUp() {
        badgeRepository = mock(LearningBadgeRepository.class);
        credentialRepository = mock(CredentialRepository.class);
        groupRepository = mock(CredentialGroupRepository.class);
        completionRepository = mock(CompletionRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        courseRepository = mock(CourseRepository.class);
        authorizationPolicy = mock(AuthorizationPolicy.class);
        service = new DefaultLearningBadgeApplicationService(
            badgeRepository, credentialRepository, groupRepository,
            completionRepository, enrollmentRepository, courseRepository,
            authorizationPolicy);
    }

    @Test
    void issueFailsWhenCredentialDoesNotExist() {
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(command()))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).code())
            .isEqualTo(ApiErrorCode.CREDENTIAL_NOT_FOUND);
        verify(badgeRepository, never()).save(any());
    }

    @Test
    void getFailsWhenBadgeDoesNotExist() {
        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(BADGE_ID, learner()))
            .isInstanceOf(ApiException.class)
            .extracting(exception -> ((ApiException) exception).code())
            .isEqualTo(ApiErrorCode.BADGE_NOT_FOUND);
    }

    @Test
    void getReturnsOwnBadgeWithoutInstitutionScopeCheck() {
        LearningBadge badge = issuedBadge();
        when(badgeRepository.findById(BADGE_ID)).thenReturn(Optional.of(badge));

        assertThat(service.get(BADGE_ID, learner())).isEqualTo(badge);
        verify(courseRepository, never()).findActiveById(any());
    }

    @Test
    void listReturnsPageForSubjectWithoutScopeCheck() {
        when(badgeRepository.findByUserId(USER_ID, 20, 0, "createdAt,desc"))
            .thenReturn(java.util.List.of(issuedBadge()));
        when(badgeRepository.countByUserId(USER_ID)).thenReturn(1L);

        LearningBadgePage page = service.listByUser(new ListUserBadgesQuery(
            USER_ID, 0, 20, "createdAt,desc", learner()));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        verify(enrollmentRepository, never()).findInstitutionIdsByUserId(any());
    }

    private IssueLearningBadgeCommand command() {
        return new IssueLearningBadgeCommand(
            CREDENTIAL_ID, "COURSE_COMPLETION", "AI 기초 이수", null,
            "badge-idem-key-1", learner(), Instant.parse("2026-08-27T00:00:00Z"));
    }

    private LearningBadge issuedBadge() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new LearningBadge(BADGE_ID, USER_ID, COURSE_ID, CREDENTIAL_ID,
            "COURSE_COMPLETION", "AI 기초 이수", LearningBadgeStatus.ISSUED,
            null, now, null, now, now);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
