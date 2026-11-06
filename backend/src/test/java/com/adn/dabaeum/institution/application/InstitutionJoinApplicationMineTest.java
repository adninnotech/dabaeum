package com.adn.dabaeum.institution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationRepository;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequestRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/** 신청자 본인의 기관 등록 신청 이력과, 대기 중 신청이 있을 때의 중복 신청 차단. */
@ExtendWith(MockitoExtension.class)
class InstitutionJoinApplicationMineTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final UUID APPLICANT_ID =
        UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock InstitutionJoinApplicationRepository applicationRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock InstitutionSigningKeyRequestRepository signingKeyRequests;

    private DefaultInstitutionJoinApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultInstitutionJoinApplicationService(
            applicationRepository, institutionRepository, userRoleRepository,
            UUID::randomUUID, new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC), signingKeyRequests);
    }

    @Test
    void applyIsRejectedWhileTheApplicantHasAPendingApplication() {
        when(applicationRepository.existsPendingByApplicant(APPLICANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.apply(command()))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.INSTITUTION_APPLICATION_CONFLICT);
            });
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void concurrentPendingApplicationCaughtByTheUniqueIndexIsAConflict() {
        when(applicationRepository.existsPendingByApplicant(APPLICANT_ID)).thenReturn(false);
        doThrow(new DuplicateKeyException("uq_tb_institution_applications_pending"))
            .when(applicationRepository).save(any());

        assertThatThrownBy(() -> service.apply(command()))
            .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.code())
                .isEqualTo(ApiErrorCode.INSTITUTION_APPLICATION_CONFLICT));
    }

    @Test
    void applySavesAPendingApplicationForTheApplicant() {
        when(applicationRepository.existsPendingByApplicant(APPLICANT_ID)).thenReturn(false);

        InstitutionJoinApplication saved = service.apply(command());

        assertThat(saved.status()).isEqualTo(InstitutionJoinApplicationStatus.PENDING);
        assertThat(saved.applicantUserId()).isEqualTo(APPLICANT_ID);
        verify(applicationRepository).save(saved);
    }

    @Test
    void listMineReturnsOnlyTheApplicantsApplications() {
        InstitutionJoinApplication mine = pending();
        when(applicationRepository.findPage(
            InstitutionJoinApplicationStatus.PENDING, APPLICANT_ID, 20, 20))
            .thenReturn(List.of(mine));
        when(applicationRepository.count(InstitutionJoinApplicationStatus.PENDING, APPLICANT_ID))
            .thenReturn(21L);

        InstitutionJoinApplicationPage page = service.listMine(
            applicant(), InstitutionJoinApplicationStatus.PENDING, 1, 20);

        assertThat(page.data()).containsExactly(mine);
        assertThat(page.totalElements()).isEqualTo(21L);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    private ApplyInstitutionJoinCommand command() {
        return new ApplyInstitutionJoinCommand(
            "새 기관", null, "대표자", "rep@example.com", "010-0000-0000", null,
            applicant(), NOW);
    }

    private InstitutionJoinApplication pending() {
        return new InstitutionJoinApplication(
            UUID.randomUUID(), "새 기관", null, "대표자", "rep@example.com",
            "010-0000-0000", null, InstitutionJoinApplicationStatus.PENDING,
            null, APPLICANT_ID, null, null, null, NOW, NOW);
    }

    private static AuthenticatedUserContext applicant() {
        return new AuthenticatedUserContext(APPLICANT_ID, "LOCAL", Set.of());
    }
}
