package com.adn.dabaeum.attendance.api;

import com.adn.dabaeum.attendance.application.DadaeguAttendSessionService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트의 학생 출석 페이지({@code /attend?t=})가 부른다. 강사 QR 을 폰 기본 카메라로 찍은 학생이
 * 여기서 다대구 세션을 열고, 앱에서 돌아와 결과를 묻는다. 학생은 로그인하지 않는다 —
 * 본인 확인은 다대구 DID 가, 현장 확인은 QR 토큰이 한다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/attend-sessions")
public class DadaeguAttendSessionController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final DadaeguAttendSessionService service;
    private final Clock clock;

    public DadaeguAttendSessionController(DadaeguAttendSessionService service, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttendSessionResponse> open(
        @Valid @RequestBody AttendSessionCreateRequest body,
        HttpServletRequest request
    ) {
        return new ApiResponse<>(
            AttendSessionResponse.opened(service.open(body.token())), meta(request));
    }

    /** 폰이 앱에서 돌아온 순간과 버튼을 눌렀을 때 부른다. 타이머로 부를 필요는 없다. */
    @GetMapping("/{attendId}")
    public ApiResponse<AttendSessionResponse> status(
        @PathVariable UUID attendId,
        HttpServletRequest request
    ) {
        return service.status(attendId)
            .map(view -> new ApiResponse<>(AttendSessionResponse.status(view), meta(request)))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                ApiErrorCode.ATTEND_SESSION_NOT_FOUND, "Attend session not found or expired"));
    }

    private ApiMeta meta(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
