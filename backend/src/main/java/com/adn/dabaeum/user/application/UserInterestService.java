package com.adn.dabaeum.user.application;

import com.adn.dabaeum.classification.application.CourseClassificationNode;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.AgreementType;
import com.adn.dabaeum.user.domain.UserAgreement;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserInterestRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관심 분야 (docs/specs/interest-based-member-profile.md §1·§3.1). 선택 입력이고 0~3개이며, 값을 저장하려면
 * 선택 동의가 있어야 한다. 동의 이력은 append-only 라 철회도 새 행으로 남긴다.
 */
@Service
public class UserInterestService {

    private static final int MAX_CODES = 3;
    private static final String CODES_FIELD = "interestCodes";
    private static final String AGREED_FIELD = "agreedToInterest";

    private final UserInterestRepository interests;
    private final UserAgreementRepository agreements;
    private final Supplier<Set<String>> enabledTopLevelCodes;

    @Autowired
    public UserInterestService(
        UserInterestRepository interests,
        UserAgreementRepository agreements,
        CourseClassificationService classifications
    ) {
        this(interests, agreements, () -> classifications.tree(true).stream()
            .map(CourseClassificationNode::code)
            .collect(Collectors.toUnmodifiableSet()));
    }

    UserInterestService(
        UserInterestRepository interests,
        UserAgreementRepository agreements,
        Supplier<Set<String>> enabledTopLevelCodes
    ) {
        this.interests = Objects.requireNonNull(interests);
        this.agreements = Objects.requireNonNull(agreements);
        this.enabledTopLevelCodes = Objects.requireNonNull(enabledTopLevelCodes);
    }

    /** 가입 값 검증. 아무것도 저장하기 전에 부른다. */
    public void validateForSignup(List<String> codes, Boolean agreed) {
        List<String> selected = orEmpty(codes);
        requireValidCodes(selected);
        if (!selected.isEmpty() && !Boolean.TRUE.equals(agreed)) {
            throw invalid(AGREED_FIELD);
        }
    }

    /** 사용자 저장 뒤 같은 트랜잭션에서 부른다. 거부도 동의 이력으로 남긴다. */
    @Transactional
    public void recordAtSignup(UUID userId, List<String> codes, Boolean agreed, Instant now) {
        validateForSignup(codes, agreed);
        List<String> selected = orEmpty(codes);
        if (!selected.isEmpty()) {
            interests.replace(userId, selected, now);
        }
        saveConsent(userId, Boolean.TRUE.equals(agreed), now);
    }

    /** 빈 목록은 삭제이며, 동의 중이던 회원에게는 철회로 기록한다. */
    @Transactional
    public void updateMine(UUID userId, List<String> codes, Boolean agreed, Instant now) {
        List<String> selected = orEmpty(codes);
        requireValidCodes(selected);
        boolean consented = latestConsent(userId);
        if (selected.isEmpty()) {
            interests.replace(userId, List.of(), now);
            if (consented) {
                saveConsent(userId, false, now);
            }
            return;
        }
        if (Boolean.FALSE.equals(agreed) || (!consented && !Boolean.TRUE.equals(agreed))) {
            throw invalid(AGREED_FIELD);
        }
        interests.replace(userId, selected, now);
        if (!consented) {
            saveConsent(userId, true, now);
        }
    }

    @Transactional(readOnly = true)
    public UserInterests view(UUID userId) {
        return new UserInterests(interests.findCodes(userId), latestConsent(userId));
    }

    private boolean latestConsent(UUID userId) {
        return agreements.findByUserId(userId).stream()
            .filter(agreement -> agreement.agreementType() == AgreementType.INTEREST)
            .max(Comparator.comparing(UserAgreement::agreedAt))
            .map(UserAgreement::agreed)
            .orElse(false);
    }

    private void saveConsent(UUID userId, boolean agreed, Instant now) {
        agreements.saveAll(List.of(
            new UserAgreement(UUID.randomUUID(), userId, AgreementType.INTEREST, agreed, now)));
    }

    private void requireValidCodes(List<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        if (codes.size() > MAX_CODES || new HashSet<>(codes).size() != codes.size()
            || !enabledTopLevelCodes.get().containsAll(codes)) {
            throw invalid(CODES_FIELD);
        }
    }

    private static List<String> orEmpty(List<String> codes) {
        return codes == null ? List.of() : codes;
    }

    private static ApiException invalid(String field) {
        return new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT,
            ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            List.of(field));
    }
}
