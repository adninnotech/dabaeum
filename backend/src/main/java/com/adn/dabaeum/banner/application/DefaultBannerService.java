package com.adn.dabaeum.banner.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerRepository;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.file.domain.StoredFile;
import com.adn.dabaeum.file.domain.StoredFilePurpose;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultBannerService implements BannerService {

    private final BannerRepository repository;
    private final StoredFileRepository storedFileRepository;
    private final AuthorizationPolicy authorizationPolicy;
    private final Clock clock;

    public DefaultBannerService(
        BannerRepository repository,
        StoredFileRepository storedFileRepository,
        AuthorizationPolicy authorizationPolicy,
        Clock clock
    ) {
        this.repository = repository;
        this.storedFileRepository = storedFileRepository;
        this.authorizationPolicy = authorizationPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Banner> list(boolean visibleOnly) {
        List<Banner> banners = repository.findAll();
        if (!visibleOnly) {
            return banners;
        }
        // 노출 판정은 도메인 한 곳에만 있다. SQL 은 삭제 여부와 정렬만 본다.
        Instant now = clock.instant();
        return banners.stream().filter(banner -> banner.isVisibleAt(now)).toList();
    }

    @Override
    @Transactional
    public Banner create(BannerCommand command, AuthenticatedUserContext actor) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(actor);
        requireValidPeriod(command);
        requireBannerImage(command.pcImageFileId(), "pcImageFileId");
        requireBannerImage(command.mobileImageFileId(), "mobileImageFileId");

        Instant now = clock.instant();
        Banner banner = new Banner(
            UUID.randomUUID(),
            command.badgeText(), command.title(), command.subtitle(),
            command.buttonText(), command.buttonLink(),
            command.visibility(), command.startAt(), command.endAt(), command.sortOrder(),
            command.pcImageFileId(), command.mobileImageFileId(),
            now, now, null);
        repository.save(banner);
        return banner;
    }

    @Override
    @Transactional
    public Banner update(UUID bannerId, BannerCommand command, AuthenticatedUserContext actor) {
        Objects.requireNonNull(command, "command");
        authorizationPolicy.requirePlatformAdmin(actor);
        requireValidPeriod(command);
        requireBannerImage(command.pcImageFileId(), "pcImageFileId");
        requireBannerImage(command.mobileImageFileId(), "mobileImageFileId");

        Banner existing = repository.findById(bannerId)
            .filter(banner -> banner.deletedAt() == null)
            .orElseThrow(this::notFound);
        Banner updated = new Banner(
            existing.id(),
            command.badgeText(), command.title(), command.subtitle(),
            command.buttonText(), command.buttonLink(),
            command.visibility(), command.startAt(), command.endAt(), command.sortOrder(),
            command.pcImageFileId(), command.mobileImageFileId(),
            existing.createdAt(), clock.instant(), null);
        if (!repository.update(updated)) {
            throw notFound();
        }
        return updated;
    }

    @Override
    @Transactional
    public void delete(UUID bannerId, AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        if (!repository.softDelete(bannerId, clock.instant())) {
            throw notFound();
        }
    }

    /**
     * 기간 공개는 시작·종료가 둘 다 있어야 하고, 상시·비공개는 기간을 가질 수 없다.
     *
     * <p>도메인 생성자도 SCHEDULED 규칙은 같이 막지만 거기서 나오는 것은
     * {@code IllegalArgumentException} 이라 500 이 된다. 어느 필드가 문제인지 422 로
     * 돌려주려고 여기서 먼저 본다. PUBLIC·PRIVATE 의 기간 금지는 도메인 생성자에는 없다 —
     * 이미 저장된 행(과거에 잘못 들어간 것 포함)을 읽는 경로까지 막으면 안 되기 때문이다.
     */
    private void requireValidPeriod(BannerCommand command) {
        if (command.visibility() == BannerVisibility.SCHEDULED
            && (command.startAt() == null || command.endAt() == null)) {
            // 빠진 쪽을 정확히 가리킨다. 화면이 details 로 어느 칸을 붉힐지 정한다.
            List<String> missing = new ArrayList<>();
            if (command.startAt() == null) {
                missing.add("startAt");
            }
            if (command.endAt() == null) {
                missing.add("endAt");
            }
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.copyOf(missing));
        }
        if (command.visibility() != BannerVisibility.SCHEDULED
            && (command.startAt() != null || command.endAt() != null)) {
            List<String> present = new ArrayList<>();
            if (command.startAt() != null) {
                present.add("startAt");
            }
            if (command.endAt() != null) {
                present.add("endAt");
            }
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.copyOf(present));
        }
        if (command.startAt() != null && command.endAt() != null
            && command.startAt().isAfter(command.endAt())) {
            throw validation("endAt");
        }
    }

    /** 배너 이미지는 존재하고 BANNER 용도로 올린 파일만 연결할 수 있다. */
    private void requireBannerImage(UUID fileId, String field) {
        if (fileId == null) {
            return;
        }
        StoredFile file = storedFileRepository.findById(fileId)
            .orElseThrow(() -> validation(field));
        if (file.purpose() != StoredFilePurpose.BANNER) {
            throw validation(field);
        }
    }

    private ApiException validation(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field));
    }

    private ApiException notFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.BANNER_NOT_FOUND, "Banner not found");
    }
}
