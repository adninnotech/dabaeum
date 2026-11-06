package com.adn.dabaeum.institution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplication;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationRepository;
import com.adn.dabaeum.institution.domain.InstitutionJoinApplicationStatus;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequest;
import com.adn.dabaeum.institution.domain.InstitutionSigningKeyRequestRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 기관 등록 신청을 승인하면 같은 트랜잭션에서 기관 서명키 생성을 요청한다. */
@ExtendWith(MockitoExtension.class)
class InstitutionJoinApprovalSigningKeyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final UUID APPLICATION_ID =
        UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID =
        UUID.fromString("33333333-3333-3333-3333-333333333333");
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
            () -> INSTITUTION_ID, new AuthorizationPolicy(),
            Clock.fixed(NOW, ZoneOffset.UTC), signingKeyRequests);
    }

    @Test
    void approvalRequestsSigningKeysForTheCreatedInstitution() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
            .thenReturn(Optional.of(pending()));
        when(institutionRepository.findActiveByCode(any())).thenReturn(Optional.empty());
        when(applicationRepository.updateDecision(any(), eq(InstitutionJoinApplicationStatus.PENDING)))
            .thenReturn(true);

        service.approve(APPLICATION_ID, platformAdmin());

        ArgumentCaptor<InstitutionSigningKeyRequest> request =
            ArgumentCaptor.forClass(InstitutionSigningKeyRequest.class);
        verify(signingKeyRequests).save(request.capture());
        assertThat(request.getValue().institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(request.getValue().requestedBy()).isEqualTo(ADMIN_ID);
        assertThat(request.getValue().requestedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectedApprovalDoesNotRequestSigningKeys() {
        when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
            .thenReturn(Optional.of(pending()));
        when(institutionRepository.findActiveByCode(any())).thenReturn(Optional.empty());
        when(applicationRepository.updateDecision(any(), eq(InstitutionJoinApplicationStatus.PENDING)))
            .thenReturn(false);

        assertThatThrownBy(() -> service.approve(APPLICATION_ID, platformAdmin()))
            .isInstanceOf(ApiException.class);

        verify(signingKeyRequests, never()).save(any());
    }

    private InstitutionJoinApplication pending() {
        return new InstitutionJoinApplication(
            APPLICATION_ID, "QA 기관", null, "QA대표", "qa@example.com", "053-000-0000",
            null, InstitutionJoinApplicationStatus.PENDING, null, APPLICANT_ID, null, null,
            null, NOW, NOW);
    }

    private AuthenticatedUserContext platformAdmin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
