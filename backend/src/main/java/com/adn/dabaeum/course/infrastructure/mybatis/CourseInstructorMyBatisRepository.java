package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseInstructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseInstructorMyBatisRepository implements CourseInstructorRepository {

    private final CourseInstructorMapper mapper;

    public CourseInstructorMyBatisRepository(CourseInstructorMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CourseInstructor instructor) {
        mapper.insert(instructor);
    }

    @Override
    public Optional<CourseInstructor> findByCourseIdAndUserId(
        UUID courseId,
        UUID userId
    ) {
        return Optional.ofNullable(mapper.selectByCourseIdAndUserId(courseId, userId))
            .map(CourseInstructorRow::toDomain);
    }

    @Override
    public List<CourseInstructor> findByCourseId(UUID courseId) {
        return mapper.selectByCourseId(courseId).stream()
            .map(CourseInstructorRow::toDomain)
            .toList();
    }

    @Override
    public boolean existsByCourseIdAndUserId(UUID courseId, UUID userId) {
        return mapper.existsByCourseIdAndUserId(courseId, userId);
    }

    @Override
    public boolean existsByCourseIdAndUserIdAndRole(
        UUID courseId,
        UUID userId,
        CourseInstructorRole role
    ) {
        return mapper.existsByCourseIdAndUserIdAndRole(courseId, userId, role);
    }

    @Override
    public boolean existsByUserIdAndInstitutionId(
        UUID userId,
        UUID institutionId
    ) {
        return mapper.existsByUserIdAndInstitutionId(userId, institutionId);
    }

    @Override
    public boolean updateRole(
        CourseInstructor instructor,
        CourseInstructorRole expectedRole
    ) {
        return mapper.updateRole(instructor, expectedRole) == 1;
    }

    @Override
    public boolean deleteByCourseIdAndUserId(UUID courseId, UUID userId) {
        return mapper.deleteByCourseIdAndUserId(courseId, userId) == 1;
    }
}
