package com.adn.dabaeum.view.api;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.view.application.CourseViewService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 강좌 상세 조회 기록 (설계서 §8.1). 프론트가 상세 화면에 들어갈 때 한 번 부른다. 상세 GET 에는 부수효과를 넣지 않는다.
 * 로그인하지 않아도 되며, 로그인했으면 그 사용자로 센다. 항상 같은 200 응답 — 강좌 존재 여부를 알리지 않는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/courses")
public class CourseViewController {

    private final CourseViewService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CourseViewController(CourseViewService service, CurrentUserProvider currentUserProvider, Clock clock) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** 모든 응답은 JSON 봉투를 쓴다(계약 규칙). 기록 여부와 무관하게 accepted=true. */
    public record ViewAccepted(boolean accepted) {
    }

    @PostMapping("/{courseId}/views")
    public ApiResponse<ViewAccepted> record(@PathVariable UUID courseId, HttpServletRequest request) {
        service.record(courseId, viewerUserId(), request.getRemoteAddr(), request.getHeader("User-Agent"));
        return new ApiResponse<>(new ViewAccepted(true), new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))));
    }

    private UUID viewerUserId() {
        try {
            return currentUserProvider.findContext().map(AuthenticatedUserContext::userId).orElse(null);
        } catch (ApiException notAUserPrincipal) {
            // dev 고정 토큰처럼 사용자 문맥이 없는 인증은 비로그인으로 센다.
            return null;
        }
    }
}
