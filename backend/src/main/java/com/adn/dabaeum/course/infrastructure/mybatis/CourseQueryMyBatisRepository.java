package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseQueryRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.course.domain.InstructorCourseView;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseQueryMyBatisRepository implements CourseQueryRepository {

    private final CourseQueryMapper mapper;

    public CourseQueryMyBatisRepository(CourseQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<InstructorCourseView> findInstructorCourses(
        UUID instructorUserId, CourseStatus status, int limit, int offset, String sort
    ) {
        CourseQuerySort querySort = CourseQuerySort.from(sort);
        return mapper.selectInstructorCourses(
                instructorUserId, status == null ? null : status.name(),
                limit, offset, querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countInstructorCourses(UUID instructorUserId, CourseStatus status) {
        return mapper.countInstructorCourses(
            instructorUserId, status == null ? null : status.name());
    }

    @Override
    public InstructorCourseStats instructorCourseStats(UUID instructorUserId) {
        InstructorCourseStatsRow row = mapper.selectInstructorCourseStats(instructorUserId);
        if (row == null) {
            return new InstructorCourseStats(0, 0, 0, 0);
        }
        return new InstructorCourseStats(
            zeroIfNull(row.total()), zeroIfNull(row.recruiting()),
            zeroIfNull(row.inProgress()), zeroIfNull(row.completed()));
    }

    @Override
    public List<Course> findInstitutionCourses(
        UUID institutionId, CourseStatus status, int limit, int offset, String sort
    ) {
        CourseQuerySort querySort = CourseQuerySort.from(sort);
        return mapper.selectInstitutionCourses(
                institutionId, status == null ? null : status.name(),
                limit, offset, querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countInstitutionCourses(UUID institutionId, CourseStatus status) {
        return mapper.countInstitutionCourses(
            institutionId, status == null ? null : status.name());
    }

    @Override
    public List<InstitutionInstructorView> findInstitutionInstructors(
        UUID institutionId, String status, int limit, int offset, String sort
    ) {
        InstructorListSort querySort = InstructorListSort.from(sort);
        return mapper.selectInstitutionInstructors(
                institutionId, status, limit, offset,
                querySort.field, querySort.direction)
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countInstitutionInstructors(UUID institutionId, String status) {
        return mapper.countInstitutionInstructors(institutionId, status);
    }

    private InstructorCourseView toDomain(InstructorCourseViewRow row) {
        Course course = new Course(
            row.id(), row.institutionId(), row.courseCode(), row.title(),
            row.description(), row.category(),
            CourseEducationType.valueOf(row.educationType()),
            row.startDate(), row.endDate(), row.recruitStartDate(), row.recruitEndDate(),
            row.capacity(), row.location(), row.onlineUrl(), row.creditBankEligible(),
            row.creditValue(), CourseStatus.valueOf(row.status()),
            row.createdAt(), row.updatedAt(), row.deletedAt(), row.thumbnailFileId());
        return new InstructorCourseView(
            course,
            CourseInstructorRole.valueOf(row.instructorRole()),
            zeroIfNull(row.enrolledCount()));
    }

    private Course toDomain(CourseRow row) {
        return new Course(
            row.id(), row.institutionId(), row.courseCode(), row.title(),
            row.description(), row.category(),
            CourseEducationType.valueOf(row.educationType()),
            row.startDate(), row.endDate(), row.recruitStartDate(), row.recruitEndDate(),
            row.capacity(), row.location(), row.onlineUrl(), row.creditBankEligible(),
            row.creditValue(), CourseStatus.valueOf(row.status()),
            row.createdAt(), row.updatedAt(), row.deletedAt(), row.thumbnailFileId());
    }

    private InstitutionInstructorView toDomain(InstitutionInstructorViewRow row) {
        return new InstitutionInstructorView(
            row.userId(), row.name(), row.email(), row.phone(), row.status(),
            row.joinedAt(), zeroIfNull(row.courseCount()), row.memo());
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0 : value;
    }

    private enum CourseQuerySort {
        CREATED_AT_ASC("createdAt,asc", "created_at", "ASC"),
        CREATED_AT_DESC("createdAt,desc", "created_at", "DESC"),
        TITLE_ASC("title,asc", "title", "ASC"),
        TITLE_DESC("title,desc", "title", "DESC"),
        START_DATE_ASC("startDate,asc", "start_date", "ASC"),
        START_DATE_DESC("startDate,desc", "start_date", "DESC");

        private final String value;
        private final String field;
        private final String direction;

        CourseQuerySort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        private static CourseQuerySort from(String value) {
            for (CourseQuerySort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException("Unsupported course query sort: " + value);
        }
    }

    private enum InstructorListSort {
        NAME_ASC("name,asc", "name", "ASC"),
        NAME_DESC("name,desc", "name", "DESC"),
        JOINED_AT_ASC("joinedAt,asc", "joined_at", "ASC"),
        JOINED_AT_DESC("joinedAt,desc", "joined_at", "DESC");

        private final String value;
        private final String field;
        private final String direction;

        InstructorListSort(String value, String field, String direction) {
            this.value = value;
            this.field = field;
            this.direction = direction;
        }

        private static InstructorListSort from(String value) {
            for (InstructorListSort sort : values()) {
                if (sort.value.equals(value)) {
                    return sort;
                }
            }
            throw new IllegalArgumentException("Unsupported instructor list sort: " + value);
        }
    }
}
