package com.adn.dabaeum.classification.infrastructure.mybatis;

import com.adn.dabaeum.classification.domain.CourseClassification;
import com.adn.dabaeum.classification.domain.CourseClassificationRepository;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseClassificationMyBatisRepository implements CourseClassificationRepository {

    private final CourseClassificationMapper mapper;

    public CourseClassificationMyBatisRepository(CourseClassificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CourseClassification> findAll() {
        return mapper.selectAll().stream()
            .map(row -> new CourseClassification(
                row.code(), row.level(), row.parentCode(), row.name(), row.sortOrder(), row.enabled()))
            .toList();
    }

    @Override
    public boolean updateEnabled(String code, boolean enabled) {
        return mapper.updateEnabled(code, enabled) == 1;
    }
}
