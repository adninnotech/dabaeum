package com.adn.dabaeum.banner.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 배너 생성·수정의 기간 검증. PUBLIC·PRIVATE 는 기간을 가질 수 없다. */
class DefaultBannerServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private static final UUID ADMIN_ID = UUID.randomUUID();

    private final BannerRepository repository = mock(BannerRepository.class);
    private final StoredFileRepository storedFileRepository = mock(StoredFileRepository.class);
    private final DefaultBannerService service = new DefaultBannerService(
        repository, storedFileRepository, new AuthorizationPolicy(),
        Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @EnumSource(value = BannerVisibility.class, names = {"PUBLIC", "PRIVATE"})
    void 상시_비공개는_기간을_가지면_생성이_거부된다(BannerVisibility visibility) {
        var command = command(visibility, Instant.parse("2026-09-01T00:00:00Z"), null);

        assertThatThrownBy(() -> service.create(command, admin()))
            .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BannerVisibility.class, names = {"PUBLIC", "PRIVATE"})
    void 상시_비공개는_기간을_가지면_수정이_거부된다(BannerVisibility visibility) {
        var command = command(visibility, null, Instant.parse("2026-09-30T00:00:00Z"));

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), command, admin()))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void 기간공개는_기간이_있어야_생성된다() {
        var command = command(BannerVisibility.SCHEDULED,
            Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z"));

        var banner = service.create(command, admin());

        org.assertj.core.api.Assertions.assertThat(banner.startAt())
            .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    private BannerCommand command(BannerVisibility visibility, Instant startAt, Instant endAt) {
        return new BannerCommand(
            null, "배너", null, null, null, visibility, startAt, endAt, 0, null, null);
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
