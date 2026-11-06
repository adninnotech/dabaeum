package com.adn.dabaeum.interest.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.interest.domain.CourseInterest;
import com.adn.dabaeum.interest.domain.CourseInterestRepository;
import com.adn.dabaeum.interest.domain.CourseInterestView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseInterestMyBatisRepository implements CourseInterestRepository {

    private final CourseInterestMapper mapper;

    public CourseInterestMyBatisRepository(CourseInterestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CourseInterest interest) {
        mapper.insert(new CourseInterestRow(
            interest.id(), interest.userId(), interest.courseId(),
            interest.createdAt()));
    }

    @Override
    public Optional<CourseInterest> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<CourseInterest> findByUserIdAndCourseId(UUID userId, UUID courseId) {
        return Optional.ofNullable(
            mapper.selectByUserIdAndCourseId(userId, courseId)).map(this::toDomain);
    }

    @Override
    public List<CourseInterestView> findByUserId(
        UUID userId, String keyword, int limit, int offset
    ) {
        return mapper.selectByUserId(userId, keyword, limit, offset)
            .stream()
            .map(row -> new CourseInterestView(
                row.id(), row.courseId(), row.createdAt(), row.courseTitle(),
                row.institutionName(), row.classificationCode(), row.educationType(),
                row.courseStatus(), row.instructorName(), row.sessionCount(),
                row.tags(),
                row.tuitionType() == null ? null : CourseFeeType.valueOf(row.tuitionType()),
                row.tuitionAmount(), row.thumbnailFileId()))
            .toList();
    }

    @Override
    public long countByUserId(UUID userId, String keyword) {
        return mapper.countByUserId(userId, keyword);
    }

    @Override
    public boolean delete(UUID id) {
        return mapper.deleteById(id) == 1;
    }

    private CourseInterest toDomain(CourseInterestRow row) {
        return new CourseInterest(
            row.id(), row.userId(), row.courseId(), row.createdAt());
    }
}
