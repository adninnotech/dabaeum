package com.adn.dabaeum.search.domain;

import java.time.Instant;
import java.util.List;

/** pattern 은 LIKE 용(소문자·이스케이프), term 은 유사도 정렬용(소문자). */
public interface SearchRepository {

    List<SearchHits.CourseHit> courses(String pattern, String term, int limit);

    long countCourses(String pattern);

    List<SearchHits.InstitutionHit> institutions(String pattern, String term, int limit);

    long countInstitutions(String pattern);

    List<SearchHits.NoticeHit> notices(String pattern, String term, Instant now, int limit);

    long countNotices(String pattern, Instant now);

    List<SearchHits.FaqHit> faqs(String pattern, String term, int limit);

    long countFaqs(String pattern);
}
