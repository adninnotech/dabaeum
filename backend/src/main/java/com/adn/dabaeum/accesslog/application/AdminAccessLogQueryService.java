package com.adn.dabaeum.accesslog.application;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogPage;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 접속기록 조회 (설계서 §10.2). 수정·삭제 경로는 없다. */
@Service
@Profile({"local", "dev"})
public class AdminAccessLogQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int MAX_PAGE_SIZE = 100;

    private final AdminAccessLogRepository repository;

    public AdminAccessLogQueryService(AdminAccessLogRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** from·to 는 서울 날짜이며 둘 다 포함한다. */
    @Transactional(readOnly = true)
    public AdminAccessLogPage list(
        int page, int size, UUID actorId, AccessLogAction action, LocalDate from, LocalDate to
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Invalid page request", List.of("page"));
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", List.of("from"));
        }
        return repository.search(new AdminAccessLogCriteria(
            actorId, action, startOf(from), to == null ? null : startOf(to.plusDays(1)),
            Math.multiplyExact(page, size), size));
    }

    private static Instant startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay(SEOUL).toInstant();
    }
}
