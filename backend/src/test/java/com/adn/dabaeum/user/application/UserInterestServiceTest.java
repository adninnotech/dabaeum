package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** docs/specs/interest-based-member-profile.md §1·§3.1. */
@ExtendWith(MockitoExtension.class)
class UserInterestServiceTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final String ARTS = "TSIX_040000";
    private static final String HUMANITIES = "TSIX_050000";
    private static final String CIVIC = "TSIX_060000";
    private static final String JOB = "TSIX_030000";
    private static final Supplier<Set<String>> CATALOG = () -> Set.of(ARTS, HUMANITIES, CIVIC, JOB);

    @Mock
    private UserInterestRepository interests;
    @Mock
    private UserAgreementRepository agreements;

    private UserInterestService service;

    @BeforeEach
    void setUp() {
        service = new UserInterestService(interests, agreements, CATALOG);
    }

    @Test
    void acceptsUpToThreeWithConsent() {
        service.validateForSignup(List.of(ARTS, HUMANITIES, CIVIC), true);
    }

    @Test
    void acceptsEmptySelectionWithOrWithoutConsent() {
        service.validateForSignup(List.of(), true);
        service.validateForSignup(List.of(), false);
        service.validateForSignup(null, null);
    }

    @Test
    void rejectsFourCodes() {
        assertRejected(List.of(ARTS, HUMANITIES, CIVIC, JOB), true);
    }

    @Test
    void rejectsDuplicateCodes() {
        assertRejected(List.of(ARTS, ARTS), true);
    }

    @Test
    void rejectsCodeOutsideEnabledTopLevelCatalog() {
        assertRejected(List.of("TSIX_040100"), true);
    }

    @Test
    void rejectsCodesWithoutConsent() {
        assertRejected(List.of(ARTS), false);
        assertRejected(List.of(ARTS), null);
    }

    @Test
    void signupStoresCodesAndConsent() {
        service.recordAtSignup(USER, List.of(ARTS), true, NOW);

        verify(interests).replace(USER, List.of(ARTS), NOW);
        assertThat(savedAgreement().agreed()).isTrue();
    }

    @Test
    void signupWithoutConsentStoresDeclineAndNoCodes() {
        service.recordAtSignup(USER, null, null, NOW);

        verify(interests, never()).replace(any(), any(), any());
        assertThat(savedAgreement().agreed()).isFalse();
    }

    @Test
    void emptyUpdateClearsCodesAndRecordsWithdrawal() {
        when(agreements.findByUserId(USER)).thenReturn(List.of(agreement(true, NOW.minusSeconds(60))));

        service.updateMine(USER, List.of(), null, NOW);

        verify(interests).replace(USER, List.of(), NOW);
        assertThat(savedAgreement().agreed()).isFalse();
    }

    @Test
    void updateWithoutPriorConsentNeedsConsent() {
        when(agreements.findByUserId(USER)).thenReturn(List.of(agreement(false, NOW.minusSeconds(60))));

        ApiException thrown = catchThrowableOfType(ApiException.class,
            () -> service.updateMine(USER, List.of(ARTS), null, NOW));

        assertThat(thrown.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(interests, never()).replace(any(), any(), any());
    }

    @Test
    void updateWithPriorConsentNeedsNoNewConsentRow() {
        when(agreements.findByUserId(USER)).thenReturn(List.of(agreement(true, NOW.minusSeconds(60))));

        service.updateMine(USER, List.of(HUMANITIES), null, NOW);

        verify(interests).replace(USER, List.of(HUMANITIES), NOW);
        verify(agreements, never()).saveAll(any());
    }

    @Test
    void viewUsesLatestConsentRowOnly() {
        when(interests.findCodes(USER)).thenReturn(List.of());
        when(agreements.findByUserId(USER)).thenReturn(List.of(
            agreement(true, NOW.minusSeconds(120)),
            agreement(false, NOW.minusSeconds(60))));

        UserInterests view = service.view(USER);

        assertThat(view.codes()).isEmpty();
        assertThat(view.agreed()).isFalse();
    }

    private void assertRejected(List<String> codes, Boolean agreed) {
        ApiException thrown = catchThrowableOfType(ApiException.class,
            () -> service.validateForSignup(codes, agreed));
        assertThat(thrown.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(thrown.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @SuppressWarnings("unchecked")
    private UserAgreement savedAgreement() {
        ArgumentCaptor<List<UserAgreement>> captor = ArgumentCaptor.forClass(List.class);
        verify(agreements).saveAll(captor.capture());
        UserAgreement only = captor.getValue().getFirst();
        assertThat(only.agreementType()).isEqualTo(AgreementType.INTEREST);
        return only;
    }

    private static UserAgreement agreement(boolean agreed, Instant at) {
        return new UserAgreement(UUID.randomUUID(), USER, AgreementType.INTEREST, agreed, at);
    }
}
