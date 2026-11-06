package com.adn.dabaeum.user.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.user.domain.PhoneNumber;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 관리자가 대리 수강신청할 학습자를 이름·생년월일·휴대전화 정확 일치로 찾는다. 검색이 아니다.
 * 다대구 회원은 이메일이 없어 이 세 값으로 찾는다. 불일치와 2명 이상은 모두 같은 404 라 어느 값이 틀렸는지,
 * 동명이인이 있는지 알려 주지 않는다.
 */
@Service
public class LearnerLookupService {

    /** 둘만 읽으면 1명인지 판정에 충분하다. */
    private static final int AMBIGUITY_PROBE = 2;
    /** yyyy-MM-dd. STRICT 라 2월 30일 같은 없는 날짜를 말일로 바꾸지 않고 거부한다. */
    private static final DateTimeFormatter BIRTH_DATE =
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final UserRepository users;
    private final AuthorizationPolicy authorizationPolicy;

    public LearnerLookupService(UserRepository users, AuthorizationPolicy authorizationPolicy) {
        this.users = users;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Transactional(readOnly = true)
    public User lookup(
        AuthenticatedUserContext actor, UUID institutionId, String name, String birthDate, String phone
    ) {
        // 대리 수강신청(POST /courses/{id}/proxy-enrollments)과 같은 권한: 플랫폼 관리자 또는 그 기관 관리자.
        authorizationPolicy.requireCourseManager(actor, institutionId);
        if (name == null || name.isBlank()) {
            throw badRequest("name");
        }
        LocalDate birth = parseBirthDate(birthDate);
        String digits = PhoneNumber.digits(phone).orElseThrow(() -> badRequest("phone"));
        List<User> matches = users.findActiveLearnersByPhone(digits, name.strip(), birth, institutionId, AMBIGUITY_PROBE);
        if (matches.size() != 1) {
            throw notFound();
        }
        return matches.getFirst();
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("birthDate");
        }
        try {
            return LocalDate.parse(value, BIRTH_DATE);
        } catch (DateTimeParseException invalid) {
            throw badRequest("birthDate");
        }
    }

    private ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid request",
            List.of(detail));
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "Learner not found");
    }
}
