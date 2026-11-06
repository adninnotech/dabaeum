package com.adn.dabaeum.user.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.common.web.RequestRateLimiter;
import com.adn.dabaeum.user.application.LearnerLookupService;
import com.adn.dabaeum.user.domain.PhoneNumber;
import com.adn.dabaeum.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기관 콘솔 대리 수강신청의 학습자 찾기(중앙 #56). 이름·생년월일·휴대전화가 모두 일치하는 1명이고, 조회는 관리자
 * 접속기록에 남는다. 횟수 제한은 둘이다: 계정마다 하루 200회(많이 조회하기), 휴대전화마다 하루 10회(한 사람의
 * 생년월일을 하루씩 바꿔 맞히기). 둘 다 같은 limiter 를 쓰고 서버 메모리라 재시작하면 초기화된다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class LearnerLookupController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /**
     * 계정마다 하루 조회 횟수. IP 제한(분당 30회, RequestRateLimitConfiguration)만으로는 한 계정이 IP 를 바꿔 가며
     * 값을 대입해 다른 기관 학습자까지 확인하는 것을 막지 못한다. 한 반 대리 신청에는 충분한 값이다.
     */
    static final int DAILY_LOOKUPS_PER_ACCOUNT = 200;
    private static final Duration ACCOUNT_WINDOW = Duration.ofDays(1);
    /**
     * 같은 휴대전화는 누가 조회하든 하루 이만큼만. 결과를 보기 전에 세야 동시에 보낸 요청이 한꺼번에 통과하지
     * 못한다(틀린 것만 결과를 보고 세면 그 사이에 30개가 다 들어간다). 그래서 맞게 찾은 조회도 세고, 성공해도
     * 줄이지 않는다 — 다른 관리자의 성공이 생년월일을 맞히던 사람의 횟수를 지우면 안 되기 때문이다.
     * 정상 대리 신청은 한 학습자를 하루 1~3번 찾는다.
     */
    static final int LOOKUPS_PER_KEY = 10;
    private static final Duration KEY_WINDOW = Duration.ofDays(1);

    private final LearnerLookupService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final RequestRateLimiter rateLimiter;

    public LearnerLookupController(LearnerLookupService service, CurrentUserProvider currentUserProvider,
        Clock clock, RequestRateLimiter rateLimiter) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/institutions/{institutionId}/learners/lookup")
    @PersonalDataAccess(action = AccessLogAction.READ, targetField = "userId")
    public ApiResponse<LearnerLookupResponse> lookup(
        @PathVariable UUID institutionId,
        @RequestBody(required = false) LearnerLookupRequest body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        var actor = currentUserProvider.requireContext();
        String name = body == null ? null : body.name();
        String birthDate = body == null ? null : body.birthDate();
        String phone = body == null ? null : body.phone();
        rejectIfLimited(rateLimiter.allow("learner-lookup-account:" + actor.userId(),
            DAILY_LOOKUPS_PER_ACCOUNT, ACCOUNT_WINDOW), response);
        // 형식이 틀린 값은 서비스가 400 으로 돌려보내므로 키를 만들지 않는다. 아무 문자열로 메모리를 채우지 못하게.
        Optional<String> digits = PhoneNumber.digits(phone);
        if (digits.isPresent()) {
            rejectIfLimited(rateLimiter.allow("learner-lookup-key:phone:" + digits.get(),
                LOOKUPS_PER_KEY, KEY_WINDOW), response);
        }
        User learner = service.lookup(actor, institutionId, name, birthDate, phone);
        return new ApiResponse<>(LearnerLookupResponse.from(learner), new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL)));
    }

    private static void rejectIfLimited(long retry, HttpServletResponse response) {
        if (retry > 0) {
            response.setHeader("Retry-After", Long.toString(retry));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED, "Too many requests");
        }
    }
}
