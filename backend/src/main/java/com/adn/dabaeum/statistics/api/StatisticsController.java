package com.adn.dabaeum.statistics.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.statistics.application.StatisticsService;
import com.adn.dabaeum.statistics.application.StatisticsService.BadgeStatistics;
import com.adn.dabaeum.statistics.application.StatisticsService.EnrollmentStatistics;
import com.adn.dabaeum.statistics.domain.BadgeStatGroup;
import com.adn.dabaeum.statistics.domain.BadgeStatRow;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 통계 (설계서 §9.2). JSON 과 CSV 는 같은 경로의 다른 핸들러다(`format=csv`). CSV 내려받기만 접속기록에 DOWNLOAD 로 남는다.
 * 인가는 규칙표가 PLATFORM_ADMIN 으로 막는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin/statistics")
public class StatisticsController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

    private final StatisticsService service;
    private final Clock clock;

    public StatisticsController(StatisticsService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    public record Envelope<T>(String groupBy, LocalDate from, LocalDate to, List<T> rows) {
    }

    @GetMapping("/enrollments")
    public ApiResponse<Envelope<EnrollmentStatRow>> enrollments(
        @RequestParam EnrollmentStatGroup groupBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String classificationCode,
        @RequestParam(required = false) UUID institutionId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String format,
        HttpServletRequest request
    ) {
        requireJson(format);
        EnrollmentStatistics result = service.enrollments(groupBy, from, to, classificationCode, institutionId, limit);
        return new ApiResponse<>(new Envelope<>(result.groupBy().name(), result.period().from(), result.period().to(),
            result.rows()), meta(request));
    }

    @GetMapping(value = "/enrollments", params = "format=csv")
    @PersonalDataAccess(action = AccessLogAction.DOWNLOAD)
    public ResponseEntity<byte[]> enrollmentsCsv(
        @RequestParam EnrollmentStatGroup groupBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String classificationCode,
        @RequestParam(required = false) UUID institutionId,
        @RequestParam(required = false) Integer limit
    ) {
        EnrollmentStatistics result = service.enrollments(groupBy, from, to, classificationCode, institutionId, limit);
        byte[] body = StatisticsCsv.write(List.of("key", "label", "applied", "approved", "completed"),
            result.rows().stream().map(row -> List.<Object>of(row.key(), label(row.label()), row.applied(),
                row.approved(), row.completed())).toList());
        return csv(body, "enrollment-statistics-" + groupBy.name().toLowerCase() + ".csv");
    }

    @GetMapping("/badges")
    public ApiResponse<Envelope<BadgeStatRow>> badges(
        @RequestParam BadgeStatGroup groupBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String format,
        HttpServletRequest request
    ) {
        requireJson(format);
        BadgeStatistics result = service.badges(groupBy, from, to);
        return new ApiResponse<>(new Envelope<>(result.groupBy().name(), result.period().from(), result.period().to(),
            result.rows()), meta(request));
    }

    @GetMapping(value = "/badges", params = "format=csv")
    @PersonalDataAccess(action = AccessLogAction.DOWNLOAD)
    public ResponseEntity<byte[]> badgesCsv(
        @RequestParam BadgeStatGroup groupBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        BadgeStatistics result = service.badges(groupBy, from, to);
        byte[] body = StatisticsCsv.write(List.of("key", "label", "issued", "revoked"),
            result.rows().stream().map(row -> List.<Object>of(row.key(), label(row.label()), row.issued(),
                row.revoked())).toList());
        return csv(body, "badge-statistics-" + groupBy.name().toLowerCase() + ".csv");
    }

    private static String label(String value) {
        return value == null ? "" : value;
    }

    private static void requireJson(String format) {
        if (format != null && !"json".equals(format)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "format must be json or csv", List.of("format"));
        }
    }

    private static ResponseEntity<byte[]> csv(byte[] body, String filename) {
        return ResponseEntity.ok()
            .contentType(CSV)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(body);
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
