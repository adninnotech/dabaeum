package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * 가입 약관 (설계서 §4.3). 이용약관·개인정보는 필수 true, 마케팅은 선택. 자체 가입과 다대구 가입이 같이 쓴다.
 * validate 는 아무것도 저장하기 전에 부르고, record 는 사용자를 저장한 뒤 같은 트랜잭션에서 부른다.
 */
final class SignupAgreements {

    private final UserAgreementRepository repository;

    SignupAgreements(UserAgreementRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    static void validate(Boolean usage, Boolean privacy) {
        requireAgreed(usage, "agreedToUsage");
        requireAgreed(privacy, "agreedToPrivacy");
    }

    void record(UUID userId, Boolean marketing, Instant now) {
        repository.saveAll(List.of(
            agreement(userId, AgreementType.USAGE, true, now),
            agreement(userId, AgreementType.PRIVACY, true, now),
            agreement(userId, AgreementType.MARKETING, Boolean.TRUE.equals(marketing), now)));
    }

    private static void requireAgreed(Boolean value, String field) {
        if (!Boolean.TRUE.equals(value)) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                List.of(field));
        }
    }

    private static UserAgreement agreement(UUID userId, AgreementType type, boolean agreed, Instant now) {
        return new UserAgreement(UUID.randomUUID(), userId, type, agreed, now);
    }
}
