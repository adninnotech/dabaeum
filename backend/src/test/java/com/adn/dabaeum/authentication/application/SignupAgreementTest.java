package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 자체 가입의 필수값 (설계서 §4.1, §4.3) — 구·군과 이용약관·개인정보 동의는 필수, 마케팅은 선택.
 * 세 약관은 항상 기록한다. 검증은 아무것도 저장하기 전에 끝난다.
 */
@ExtendWith(MockitoExtension.class)
class SignupAgreementTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-09-16T01:00:00Z");

    @Mock LocalAccountRepository localAccountRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserAgreementRepository agreementRepository;
    @Mock UserIdGenerator userIdGenerator;
    @Mock IdentityIdGenerator identityIdGenerator;
    @Mock RoleIdGenerator roleIdGenerator;
    @Mock LocalAccessTokenService tokenService;
    @Mock LoginAttemptRepository loginAttempts;

    private LocalAccountApplicationService service;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        service = new DefaultLocalAccountApplicationService(
            localAccountRepository,
            userRepository,
            userRoleRepository,
            agreementRepository,
            userIdGenerator,
            identityIdGenerator,
            roleIdGenerator,
            passwordEncoder,
            tokenService,
            loginAttempts,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        org.mockito.Mockito.lenient().when(userIdGenerator.generate()).thenReturn(USER_ID);
        org.mockito.Mockito.lenient().when(identityIdGenerator.generate()).thenReturn(IDENTITY_ID);
        org.mockito.Mockito.lenient().when(roleIdGenerator.generate()).thenReturn(ROLE_ID);
        // 약관 미동의는 이 스텁에 닿기 전에 422 로 끝나는 시나리오가 있어 lenient 로 둔다.
        org.mockito.Mockito.lenient().when(tokenService.issue(USER_ID))
            .thenReturn(new IssuedAccessToken("signed-access-token", NOW.plusSeconds(3600)));
    }

    @Test
    void 가입하면_세_약관_이력이_남는다() {
        service.signup(command(true, true, false));

        ArgumentCaptor<List<UserAgreement>> captor = ArgumentCaptor.forClass(List.class);
        verify(agreementRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .extracting(UserAgreement::agreementType, UserAgreement::agreed)
            .containsExactlyInAnyOrder(
                tuple(AgreementType.USAGE, true),
                tuple(AgreementType.PRIVACY, true),
                tuple(AgreementType.MARKETING, false));
    }

    @Test
    void 필수_약관이_없거나_거부면_아무것도_저장하기_전에_422() {
        Boolean[][] cases = {{null, null}, {true, null}, {null, true}, {false, true}, {true, false}};
        for (Boolean[] agreements : cases) {
            ApiException exception = catchThrowableOfType(
                () -> service.signup(command(agreements[0], agreements[1], null)),
                ApiException.class);

            assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(exception.details())
                .containsAnyOf("agreedToUsage", "agreedToPrivacy");
        }
        verifyNoInteractions(userRepository, agreementRepository);
    }

    @Test
    void 동의_시각은_가입_시각과_같다() {
        service.signup(command(true, true, true));

        ArgumentCaptor<List<UserAgreement>> captor = ArgumentCaptor.forClass(List.class);
        verify(agreementRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
            .allSatisfy(agreement -> assertThat(agreement.agreedAt()).isEqualTo(NOW));
    }

    private static SignupCommand command(Boolean usage, Boolean privacy, Boolean marketing) {
        return new SignupCommand(
            "a@b.kr", "password12", "홍길동", "010-0000-0000", null,
            usage, privacy, marketing);
    }
}
