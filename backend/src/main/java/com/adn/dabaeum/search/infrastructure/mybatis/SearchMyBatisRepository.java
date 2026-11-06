package com.adn.dabaeum.search.infrastructure.mybatis;

import com.adn.dabaeum.search.domain.SearchHits;
import com.adn.dabaeum.search.domain.SearchRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class SearchMyBatisRepository implements SearchRepository {

    private final SearchMapper mapper;

    public SearchMyBatisRepository(SearchMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SearchHits.CourseHit> courses(String pattern, String term, int limit) {
        return mapper.courses(pattern, term, limit);
    }

    @Override
    public long countCourses(String pattern) {
        return mapper.countCourses(pattern);
    }

    @Override
    public List<SearchHits.InstitutionHit> institutions(String pattern, String term, int limit) {
        return mapper.institutions(pattern, term, limit);
    }

    @Override
    public long countInstitutions(String pattern) {
        return mapper.countInstitutions(pattern);
    }

    @Override
    public List<SearchHits.NoticeHit> notices(String pattern, String term, Instant now, int limit) {
        return mapper.notices(pattern, term, now, limit);
    }

    @Override
    public long countNotices(String pattern, Instant now) {
        return mapper.countNotices(pattern, now);
    }

    @Override
    public List<SearchHits.FaqHit> faqs(String pattern, String term, int limit) {
        return mapper.faqs(pattern, term, limit);
    }

    @Override
    public long countFaqs(String pattern) {
        return mapper.countFaqs(pattern);
    }
}
