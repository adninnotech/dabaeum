package com.adn.dabaeum.search.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 통합검색 결과 (설계서 §5.1). 탭마다 전체 개수와 상위 항목. */
public final class SearchHits {

    private SearchHits() {
    }

    public record Tab<T>(long total, List<T> items) {

        public Tab {
            items = List.copyOf(items);
        }
    }

    public record CourseHit(UUID id, String title, String institutionName, String classificationCode, String status,
        LocalDate startDate) {
    }

    public record InstitutionHit(UUID id, String name, String region) {
    }

    public record NoticeHit(UUID id, String title, Instant publishedAt) {
    }

    public record FaqHit(UUID id, String question) {
    }
}
