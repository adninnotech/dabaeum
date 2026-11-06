package com.adn.dabaeum.usage.api;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.web.PersonalDataAccess;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.statistics.api.StatisticsCsv;
import com.adn.dabaeum.usage.application.UsageStatisticsService;
import com.adn.dabaeum.usage.application.UsageStatisticsService.BannerClicks;
import com.adn.dabaeum.usage.application.UsageStatisticsService.Demographics;
import com.adn.dabaeum.usage.application.UsageStatisticsService.KeywordRanking;
import com.adn.dabaeum.usage.application.UsageStatisticsService.Summary;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이용 통계 (docs/specs/usage-statistics.md §6). 인가는 규칙표가 PLATFORM_ADMIN 으로 막는다.
 * CSV 는 같은 경로의 다른 핸들러(`format=csv`)이고, CSV 내려받기만 접속기록에 DOWNLOAD 로 남는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/admin/statistics/usage")
public class UsageStatisticsController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final UsageStatisticsService service;
    private final Clock clock;

    public UsageStatisticsController(UsageStatisticsService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public ApiResponse<Summary> summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UsageInterval interval,
        @RequestParam(required = false) String format,
        HttpServletRequest request
    ) {
        requireJson(format);
        return new ApiResponse<>(service.summary(from, to, interval), meta(request));
    }

    @GetMapping(params = "format=csv")
    @PersonalDataAccess(action = AccessLogAction.DOWNLOAD)
    public ResponseEntity<byte[]> summaryCsv(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UsageInterval interval
    ) {
        Summary summary = service.summary(from, to, interval);
        byte[] body = StatisticsCsv.write(
            List.of("period", "visitors", "searches", "courseViews", "enrollments", "bannerClicks"),
            summary.rows().stream().map(row -> List.<Object>of(row.period(), row.visitors(), row.searches(),
                row.courseViews(), row.enrollments(), row.bannerClicks())).toList());
        return ResponseEntity.ok()
            .contentType(CSV)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("usage-statistics-" + summary.interval().name().toLowerCase() + ".csv").build().toString())
            .body(body);
    }

    @GetMapping("/demographics")
    public ApiResponse<Demographics> demographics(
        @RequestParam UsageMetric metric,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        HttpServletRequest request
    ) {
        return new ApiResponse<>(service.demographics(metric, from, to), meta(request));
    }

    @GetMapping("/search-keywords")
    public ApiResponse<KeywordRanking> searchKeywords(
        @RequestParam KeywordRankingType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Integer limit,
        HttpServletRequest request
    ) {
        return new ApiResponse<>(service.keywords(type, from, to, limit), meta(request));
    }

    @GetMapping("/banners")
    public ApiResponse<BannerClicks> banners(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        HttpServletRequest request
    ) {
        return new ApiResponse<>(service.banners(from, to), meta(request));
    }

    private static void requireJson(String format) {
        if (format != null && !"json".equals(format)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "format must be json or csv",
                List.of("format"));
        }
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
