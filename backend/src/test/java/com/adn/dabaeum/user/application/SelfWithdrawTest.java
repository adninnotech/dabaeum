package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

/**
 * 본인 탈퇴 정책을 코드에 못박는 테스트.
 *
 * <p>탈퇴는 되돌릴 수 없는 동작이다: 상태를 WITHDRAWN 으로 바꾸고 이미 발급된 토큰을 같은
 * 트랜잭션에서 무효화한다. 진행 중인 수강이 있어도 막지 않고, 수강·수강평 등 어떤 이력도
 * 지우지 않는다 — 서비스에는 그런 이력을 지울 리포지토리 자체가 주입돼 있지 않다.
 */
@ExtendWith(MockitoExtension.class)
class SelfWithdrawTest {

    private static final UUID USER_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserRepository repository;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository,
            () -> USER_ID,
            Clock.fixed(NOW, ZoneOffset.UTC),
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class),
            org.mockito.Mockito.mock(com.adn.dabaeum.role.domain.UserRoleRepository.class),
            org.mockito.Mockito.mock(UserInterestService.class)
        );
    }

    @Test
    void 탈퇴하면_상태가_WITHDRAWN_이_된다() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));
        when(repository.updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW)).thenReturn(true);

        User withdrawn = service.withdrawSelf(USER_ID);

        assertThat(withdrawn.status()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.withdrawnAt()).isEqualTo(NOW);
        verify(repository).updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW);
    }

    @Test
    void 탈퇴하면_같은_트랜잭션에서_토큰을_무효화한다() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));
        when(repository.updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW)).thenReturn(true);

        service.withdrawSelf(USER_ID);

        InOrder order = inOrder(repository);
        order.verify(repository).updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW);
        order.verify(repository).invalidateTokens(USER_ID, NOW);
    }

    @Test
    void 이미_탈퇴한_사용자는_409() {
        when(repository.findById(USER_ID))
            .thenReturn(Optional.of(user(UserStatus.WITHDRAWN, NOW.minusSeconds(60))));

        ApiException exception = catchThrowableOfType(
            () -> service.withdrawSelf(USER_ID),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_STATUS_CONFLICT);
        verify(repository, never()).updateStatus(any(), any(), any(), any());
        verify(repository, never()).invalidateTokens(any(), any());
    }

    @Test
    void 로그인하지_않으면_401() {
        ApiException exception = catchThrowableOfType(
            () -> service.withdrawSelf(null),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void 사용자가_이미_없으면_404() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
            () -> service.withdrawSelf(USER_ID),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 진행_중인_수강이_있어도_탈퇴는_막지_않는다() {
        // 탈퇴 경로는 수강 여부를 조회하지 않는다 — 탈퇴를 막으면 사용자가 서비스를 떠날 수 없다.
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));
        when(repository.updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW)).thenReturn(true);

        User withdrawn = service.withdrawSelf(USER_ID);

        assertThat(withdrawn.status()).isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    void 탈퇴는_상태와_토큰_외에는_아무것도_건드리지_않는다() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE, null)));
        when(repository.updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW)).thenReturn(true);

        service.withdrawSelf(USER_ID);

        // findById 조회, 상태 변경, 토큰 무효화 세 번의 호출 외에는 이 리포지토리에도,
        // (주입돼 있지 않은) 다른 어떤 리포지토리에도 손대지 않는다.
        verify(repository).findById(USER_ID);
        verify(repository).updateStatus(USER_ID, UserStatus.WITHDRAWN, NOW, NOW);
        verify(repository).invalidateTokens(USER_ID, NOW);
        verifyNoMoreInteractions(repository);
    }

    private User user(UserStatus status, Instant withdrawnAt) {
        return new User(
            USER_ID,
            "기존 사용자",
            "old@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            status,
            withdrawnAt,
            CREATED_AT,
            NOW.minusSeconds(60)
        );
    }
}
