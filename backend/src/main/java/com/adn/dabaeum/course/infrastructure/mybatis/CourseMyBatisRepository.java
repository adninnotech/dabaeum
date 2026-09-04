package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CoursePageCriteria;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseMyBatisRepository implements CourseRepository {

    private final CourseMapper mapper;

    public CourseMyBatisRepository(CourseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Course course) {
        mapper.insert(toRow(course));
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveById(UUID id) {
        return Optional.ofNullable(mapper.selectActiveById(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveByIdForUpdate(UUID id) {
        return Optional.ofNullable(mapper.selectActiveByIdForUpdate(id))
            .map(this::toDomain);
    }

    @Override
    public Optional<Course> findActiveByInstitutionAndCode(
        UUID institutionId,
        String courseCode
    ) {
        return Optional.ofNullable(mapper.selectActiveByInstitutionAndCode(
            institutionId,
            courseCode
        )).map(this::toDomain);
    }

    @Override
    public List<Course> findActivePage(CoursePageCriteria criteria) {
        return mapper.selectActivePage(criteria)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countActive() {
        return mapper.countActive();
    }

    @Override
    public boolean updateActive(Course course) {
        return mapper.updateActive(toRow(course)) == 1;
    }

    private CourseRow toRow(Course course) {
        return new CourseRow(
            course.id(),
            course.institutionId(),
            course.courseCode(),
            course.title(),
            course.description(),
            course.category(),
            course.educationType().name(),
            course.startDate(),
            course.endDate(),
            course.recruitStartDate(),
            course.recruitEndDate(),
            course.capacity(),
            course.location(),
            course.onlineUrl(),
            course.creditBankEligible(),
            course.creditValue(),
            course.status().name(),
            course.createdAt(),
            course.updatedAt(),
            course.deletedAt(),
            course.thumbnailFileId()
        );
    }

    private Course toDomain(CourseRow row) {
        return new Course(
            row.id(),
            row.institutionId(),
            row.courseCode(),
            row.title(),
            row.description(),
            row.category(),
            CourseEducationType.valueOf(row.educationType()),
            row.startDate(),
            row.endDate(),
            row.recruitStartDate(),
            row.recruitEndDate(),
            row.capacity(),
            row.location(),
            row.onlineUrl(),
            row.creditBankEligible(),
            row.creditValue(),
            CourseStatus.valueOf(row.status()),
            row.createdAt(),
            row.updatedAt(),
            row.deletedAt(),
            row.thumbnailFileId()
        );
    }
}
