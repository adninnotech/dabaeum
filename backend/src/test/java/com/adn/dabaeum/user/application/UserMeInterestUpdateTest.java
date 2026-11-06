package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** docs/specs/interest-based-member-profile.md §3.1 PUT /users/me. 관심 분야 카드는 관심 분야 값만 보낸다. */
@ExtendWith(MockitoExtension.class)
class UserMeInterestUpdateTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final List<String> CODES = List.of("TSIX_040000");
    private static final UpdateMeCommand EMPTY_PROFILE = new UpdateMeCommand(
        UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent());

    @Mock private UserRepository repository;
    @Mock private UserRoleRepository roles;
    @Mock private UserInterestService interests;
    @Mock private User user;

    private DefaultUserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(repository, mock(UserIdGenerator.class),
            Clock.fixed(NOW, ZoneOffset.UTC), mock(StoredFileRepository.class), roles, interests);
    }

    @Test
    void learnerCanSendOnlyInterestFields() {
        givenRole(UserRole.LEARNER);
        when(repository.findById(USER)).thenReturn(Optional.of(user));

        User result = service.updateMeWithInterests(USER, EMPTY_PROFILE, CODES, true);

        assertThat(result).isSameAs(user);
        verify(interests).updateMine(USER, CODES, true, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void nonLearnerCannotStoreInterests() {
        givenRole(UserRole.INSTRUCTOR);

        ApiException thrown = catchThrowableOfType(ApiException.class,
            () -> service.updateMeWithInterests(USER, EMPTY_PROFILE, CODES, true));

        assertThat(thrown.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(thrown.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(thrown.details()).containsExactly("interestCodes");
        verifyNoInteractions(interests);
    }

    @Test
    void anyoneCanClearInterests() {
        when(repository.findById(USER)).thenReturn(Optional.of(user));

        service.updateMeWithInterests(USER, EMPTY_PROFILE, List.of(), null);

        verify(interests).updateMine(USER, List.of(), null, NOW);
    }

    private void givenRole(UserRole role) {
        when(roles.findByUserIds(List.of(USER)))
            .thenReturn(Map.of(USER, List.of(new RoleSummary(role, null, null))));
    }
}
