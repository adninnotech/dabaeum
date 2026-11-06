package com.adn.dabaeum.search.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.search.domain.SearchHits;
import com.adn.dabaeum.search.domain.SearchRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 통합검색 (설계서 §5). "더보기" 는 각 탭의 기존 목록 API 로 간다. */
@Service
@Profile({"local", "dev"})
public class SearchService {

    public static final int DEFAULT_LIMIT = 5;
    static final int MIN_LENGTH = 2;
    static final int MAX_LIMIT = 20;

    private final SearchRepository repository;
    private final Clock clock;

    public SearchService(SearchRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Result(
        SearchHits.Tab<SearchHits.CourseHit> courses,
        SearchHits.Tab<SearchHits.InstitutionHit> institutions,
        SearchHits.Tab<SearchHits.NoticeHit> notices,
        SearchHits.Tab<SearchHits.FaqHit> faqs
    ) {
    }

    @Transactional(readOnly = true)
    public Result search(String q, int limit) {
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > KeywordQuery.MAX_LENGTH) {
            throw badRequest("q");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw badRequest("limit");
        }
        String pattern = KeywordQuery.normalize(trimmed);
        String term = trimmed.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        return new Result(
            new SearchHits.Tab<>(repository.countCourses(pattern), repository.courses(pattern, term, limit)),
            new SearchHits.Tab<>(repository.countInstitutions(pattern), repository.institutions(pattern, term, limit)),
            new SearchHits.Tab<>(repository.countNotices(pattern, now), repository.notices(pattern, term, now, limit)),
            new SearchHits.Tab<>(repository.countFaqs(pattern), repository.faqs(pattern, term, limit)));
    }

    private static ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
            "q must be 2-100 characters and limit 1-20", List.of(field));
    }
}
