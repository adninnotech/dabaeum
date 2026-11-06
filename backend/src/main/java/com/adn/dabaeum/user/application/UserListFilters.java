package com.adn.dabaeum.user.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.UserListFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 목록 질의를 저장소 필터로 바꾼다. 날짜는 서울 날짜, 최근 접속은 요청 시각(now) 기준이다.
 * 가입일 To 는 다음 날 00:00(서울) 미만으로 바꿔 그날 하루를 포함한다.
 */
final class UserListFilters {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private UserListFilters() {
    }

    static UserListFilter from(ListUsersQuery query, Instant now) {
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw badRequest("createdFrom");
        }
        Instant createdFrom = query.createdFrom() == null
                ? null
                : query.createdFrom().atStartOfDay(SEOUL).toInstant();
        Instant createdBefore = query.createdTo() == null
                ? null
                : query.createdTo().plusDays(1).atStartOfDay(SEOUL).toInstant();
        UserLastLoginFilter lastLogin = query.lastLogin();
        return new UserListFilter(
                query.status(),
                query.keyword(),
                query.role(),
                query.gender(),
                query.hasEnrollment(),
                lastLoginFrom(lastLogin, now),
                lastLogin == UserLastLoginFilter.OVER_90 ? now.minus(Duration.ofDays(90)) : null,
                lastLogin == UserLastLoginFilter.NEVER,
                createdFrom,
                createdBefore
        );
    }

    private static Instant lastLoginFrom(UserLastLoginFilter lastLogin, Instant now) {
        if (lastLogin == UserLastLoginFilter.WITHIN_7) {
            return now.minus(Duration.ofDays(7));
        }
        if (lastLogin == UserLastLoginFilter.WITHIN_30) {
            return now.minus(Duration.ofDays(30));
        }
        return null;
    }

    private static ApiException badRequest(String field) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid",
                List.of(field)
        );
    }
}
