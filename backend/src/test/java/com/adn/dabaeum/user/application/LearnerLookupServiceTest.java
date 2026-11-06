package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 이름·생년월일·휴대전화가 모두 맞는 1명만 찾는다. 2명 이상이어도 고르지 않고 없음과 같은 404 다. */
class LearnerLookupServiceTest {

    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String NAME = "홍길동";
    private static final String BIRTH = "1990-01-15";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String PHONE = "010-1234-5678";
    private static final String DIGITS = "01012345678";

    private final UserRepository users = mock(UserRepository.class);
    private final LearnerLookupService service = new LearnerLookupService(users, new AuthorizationPolicy());

    @Test
    void findsOneLearnerWithNormalizedNameAndPhone() {
        User learner = learner();
        when(users.findActiveLearnersByPhone(DIGITS, NAME, BIRTH_DATE, INSTITUTION_ID, 2)).thenReturn(List.of(learner));

        assertThat(service.lookup(admin(INSTITUTION_ID), INSTITUTION_ID, "  홍길동 ", BIRTH, PHONE)).isEqualTo(learner);
    }

    @Test
    void platformAdminCanLookUpAnyInstitution() {
        User learner = learner();
        when(users.findActiveLearnersByPhone(DIGITS, NAME, BIRTH_DATE, INSTITUTION_ID, 2)).thenReturn(List.of(learner));
        AuthenticatedUserContext platformAdmin = new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));

        assertThat(service.lookup(platformAdmin, INSTITUTION_ID, NAME, BIRTH, DIGITS)).isEqualTo(learner);
    }

    @Test
    void otherInstitutionAdminIsForbiddenBeforeAnyLookup() {
        assertThatThrownBy(() -> service.lookup(admin(OTHER_INSTITUTION_ID), INSTITUTION_ID, NAME, BIRTH, PHONE))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(error.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
            });
        verifyNoInteractions(users);
    }

    @Test
    void requiresAllThreeWellFormedValues() {
        assertBadRequest(null, BIRTH, PHONE, "name");
        assertBadRequest("  ", BIRTH, PHONE, "name");
        assertBadRequest(NAME, null, PHONE, "birthDate");
        assertBadRequest(NAME, "900115", PHONE, "birthDate");
        assertBadRequest(NAME, "1990-02-30", PHONE, "birthDate");
        assertBadRequest(NAME, BIRTH, null, "phone");
        assertBadRequest(NAME, BIRTH, "010", "phone");
        assertBadRequest(NAME, BIRTH, "010-abcd-5678", "phone");
        verifyNoInteractions(users);
    }

    @Test
    void noMatchIsNotFound() {
        when(users.findActiveLearnersByPhone(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        assertNotFound();
    }

    @Test
    void twoMatchesAreNotFoundInsteadOfPickingOne() {
        when(users.findActiveLearnersByPhone(DIGITS, NAME, BIRTH_DATE, INSTITUTION_ID, 2))
            .thenReturn(List.of(learner(), learner()));

        assertNotFound();
    }

    private void assertBadRequest(String name, String birthDate, String phone, String detail) {
        assertThatThrownBy(() -> service.lookup(admin(INSTITUTION_ID), INSTITUTION_ID, name, birthDate, phone))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(error.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
                assertThat(error.details()).containsExactly(detail);
            });
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> service.lookup(admin(INSTITUTION_ID), INSTITUTION_ID, NAME, BIRTH, PHONE))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(error.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
            });
    }

    private static AuthenticatedUserContext admin(UUID institutionId) {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)));
    }

    private static User learner() {
        Instant now = Instant.parse("2026-10-26T00:00:00Z");
        return new User(UUID.randomUUID(), NAME, null, PHONE, BIRTH_DATE, UserStatus.ACTIVE, null, now, now);
    }
}
