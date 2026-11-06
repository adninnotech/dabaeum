package com.adn.dabaeum.search.infrastructure.mybatis;

import com.adn.dabaeum.search.domain.SearchHits;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchMapper {

    List<SearchHits.CourseHit> courses(@Param("pattern") String pattern, @Param("term") String term, @Param("limit") int limit);

    long countCourses(@Param("pattern") String pattern);

    List<SearchHits.InstitutionHit> institutions(@Param("pattern") String pattern, @Param("term") String term,
        @Param("limit") int limit);

    long countInstitutions(@Param("pattern") String pattern);

    List<SearchHits.NoticeHit> notices(@Param("pattern") String pattern, @Param("term") String term,
        @Param("now") Instant now, @Param("limit") int limit);

    long countNotices(@Param("pattern") String pattern, @Param("now") Instant now);

    List<SearchHits.FaqHit> faqs(@Param("pattern") String pattern, @Param("term") String term, @Param("limit") int limit);

    long countFaqs(@Param("pattern") String pattern);
}
