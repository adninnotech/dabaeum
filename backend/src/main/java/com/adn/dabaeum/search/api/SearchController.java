package com.adn.dabaeum.search.api;

import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.search.application.SearchService;
import com.adn.dabaeum.search.domain.SearchHits;
import com.adn.dabaeum.usage.api.UsageEventController;
import com.adn.dabaeum.usage.application.UsageEventService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 통합검색 (설계서 §5.1). 무인증. */
@RestController
@Profile({"local", "dev"})
public class SearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);

    private final SearchService service;
    private final ObjectProvider<CourseClassificationService> classifications;
    private final ObjectProvider<UsageEventService> usage;
    private final ObjectProvider<CurrentUserProvider> currentUsers;
    private final Clock clock;

    public SearchController(
        SearchService service, ObjectProvider<CourseClassificationService> classifications,
        ObjectProvider<UsageEventService> usage, ObjectProvider<CurrentUserProvider> currentUsers, Clock clock
    ) {
        this.service = service;
        this.classifications = classifications;
        this.usage = usage;
        this.currentUsers = currentUsers;
        this.clock = clock;
    }

    public record CourseItem(UUID id, String title, String institutionName, String classificationCode,
        List<String> classificationPath, String status, LocalDate startDate) {
    }

    public record SearchResponse(
        SearchHits.Tab<CourseItem> courses,
        SearchHits.Tab<SearchHits.InstitutionHit> institutions,
        SearchHits.Tab<SearchHits.NoticeHit> notices,
        SearchHits.Tab<SearchHits.FaqHit> faqs
    ) {
    }

    @GetMapping("/api/v1/search")
    public ApiResponse<SearchResponse> search(
        @RequestParam String q,
        @RequestParam(defaultValue = "" + SearchService.DEFAULT_LIMIT) int limit,
        HttpServletRequest request
    ) {
        SearchService.Result result = service.search(q, limit);
        recordUsage(q, result, request);
        List<CourseItem> courses = result.courses().items().stream()
            .map(hit -> new CourseItem(hit.id(), hit.title(), hit.institutionName(), hit.classificationCode(),
                path(hit.classificationCode()), hit.status(), hit.startDate()))
            .toList();
        return new ApiResponse<>(new SearchResponse(
            new SearchHits.Tab<>(result.courses().total(), courses), result.institutions(), result.notices(),
            result.faqs()), new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))));
    }

    /** 이용 통계 검색 기록 (docs/specs/usage-statistics.md §4.2). 기록이 실패해도 검색 응답은 그대로 나간다. */
    private void recordUsage(String q, SearchService.Result result, HttpServletRequest request) {
        UsageEventService recorder = usage.getIfAvailable();
        if (recorder == null) {
            return;
        }
        boolean zeroResult = result.courses().total() + result.institutions().total() + result.notices().total()
            + result.faqs().total() == 0;
        try {
            CurrentUserProvider users = currentUsers.getIfAvailable();
            recorder.recordSearch(q, zeroResult, users == null ? null : UsageEventController.currentUserId(users),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (RuntimeException exception) {
            LOGGER.warn("USAGE_RECORD_FAILED type=SEARCH error={}", exception.getClass().getName(), exception);
        }
    }

    private List<String> path(String code) {
        CourseClassificationService service = classifications.getIfAvailable();
        return service == null ? List.of() : service.pathOf(code);
    }
}
