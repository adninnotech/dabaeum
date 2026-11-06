package com.adn.dabaeum.statistics.application;

import com.adn.dabaeum.classification.domain.ClassificationPrefix;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.statistics.domain.BadgeStatGroup;
import com.adn.dabaeum.statistics.domain.BadgeStatRow;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import com.adn.dabaeum.statistics.domain.StatisticsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 통계 (설계서 §9). 기간은 서울 날짜 [from, to] 포함, 기본 올해 1월 1일~오늘, 최대 5년. */
@Service
@Profile({"local", "dev"})
public class StatisticsService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int MAX_YEARS = 5;
    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final StatisticsRepository repository;
    private final Clock clock;

    public StatisticsService(StatisticsRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Period(LocalDate from, LocalDate to) {
    }

    public record EnrollmentStatistics(EnrollmentStatGroup groupBy, Period period, List<EnrollmentStatRow> rows) {
    }

    public record BadgeStatistics(BadgeStatGroup groupBy, Period period, List<BadgeStatRow> rows) {
    }

    @Transactional(readOnly = true)
    public EnrollmentStatistics enrollments(
        EnrollmentStatGroup group, LocalDate from, LocalDate to, String classificationCode, UUID institutionId,
        Integer limit
    ) {
        Objects.requireNonNull(group, "group");
        Period period = period(from, to);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw badRequest("limit");
        }
        String prefix = null;
        if (classificationCode != null && !classificationCode.isBlank()) {
            try {
                prefix = ClassificationPrefix.of(classificationCode.trim());
            } catch (IllegalArgumentException exception) {
                throw badRequest("classificationCode");
            }
        }
        List<EnrollmentStatRow> rows = repository.enrollments(group, start(period.from()), start(period.to().plusDays(1)),
                prefix, institutionId, size).stream()
            .map(row -> group == EnrollmentStatGroup.CONTENT ? row : row.withLabel(label(group, row.key())))
            .toList();
        return new EnrollmentStatistics(group, period, rows);
    }

    @Transactional(readOnly = true)
    public BadgeStatistics badges(BadgeStatGroup group, LocalDate from, LocalDate to) {
        Objects.requireNonNull(group, "group");
        Period period = period(from, to);
        List<BadgeStatRow> rows = repository.badges(group, start(period.from()), start(period.to().plusDays(1))).stream()
            .map(row -> switch (group) {
                case DEFINITION -> "NONE".equals(row.key()) ? row.withLabel("정의 없음(옛 배지)") : row;
                case YEAR, MONTH -> row.withLabel(row.key());
            })
            .toList();
        return new BadgeStatistics(group, period, rows);
    }

    public static String label(EnrollmentStatGroup group, String key) {
        return switch (group) {
            case AGE_BAND -> switch (key) {
                case "UNKNOWN" -> "미상";
                case "10" -> "10대 이하";
                case "70" -> "70대 이상";
                default -> key + "대";
            };
            case GENDER -> switch (key) {
                case "MALE" -> "남성";
                case "FEMALE" -> "여성";
                case "UNDISCLOSED" -> "밝히지 않음";
                default -> "미상";
            };
            default -> key;
        };
    }

    private Period period(LocalDate from, LocalDate to) {
        LocalDate today = clock.instant().atZone(SEOUL).toLocalDate();
        LocalDate start = from == null ? today.withDayOfYear(1) : from;
        LocalDate end = to == null ? today : to;
        if (start.isAfter(end)) {
            throw validation("from");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw validation("to");
        }
        return new Period(start, end);
    }

    private static java.time.Instant start(LocalDate date) {
        return date.atStartOfDay(SEOUL).toInstant();
    }

    private static ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid statistics query", List.of(field));
    }

    private static ApiException validation(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of(field));
    }
}
