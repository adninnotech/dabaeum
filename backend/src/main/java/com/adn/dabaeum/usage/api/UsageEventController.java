package com.adn.dabaeum.usage.api;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.usage.application.UsageEventService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방문·배너 클릭 기록 (docs/specs/usage-statistics.md §4.1, §4.3). 로그인하지 않아도 되며, 로그인했으면 그 회원으로 센다.
 * 항상 같은 200 응답 — 배너 존재 여부를 알리지 않는다. 본문은 읽지 않는다(sendBeacon 의 text/plain 도 받는다).
 */
@RestController
@Profile({"local", "dev"})
public class UsageEventController {

    private final UsageEventService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public UsageEventController(UsageEventService service, CurrentUserProvider currentUserProvider, Clock clock) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** 모든 응답은 JSON 봉투를 쓴다(계약 규칙). 기록 여부와 무관하게 accepted=true. */
    public record UsageAccepted(boolean accepted) {
    }

    @PostMapping("/api/v1/visits")
    public ApiResponse<UsageAccepted> visit(HttpServletRequest request) {
        service.recordVisit(currentUserId(currentUserProvider), request.getRemoteAddr(), request.getHeader("User-Agent"));
        return accepted(request);
    }

    @PostMapping("/api/v1/banners/{bannerId}/clicks")
    public ApiResponse<UsageAccepted> bannerClick(@PathVariable UUID bannerId, HttpServletRequest request) {
        service.recordBannerClick(bannerId, currentUserId(currentUserProvider), request.getRemoteAddr(),
            request.getHeader("User-Agent"));
        return accepted(request);
    }

    private ApiResponse<UsageAccepted> accepted(HttpServletRequest request) {
        return new ApiResponse<>(new UsageAccepted(true), new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))));
    }

    /** 검색 기록에서도 같은 규칙을 쓴다. */
    public static UUID currentUserId(CurrentUserProvider currentUserProvider) {
        try {
            return currentUserProvider.findContext().map(AuthenticatedUserContext::userId).orElse(null);
        } catch (ApiException notAUserPrincipal) {
            // dev 고정 토큰처럼 사용자 문맥이 없는 인증은 비로그인으로 센다.
            return null;
        }
    }
}
