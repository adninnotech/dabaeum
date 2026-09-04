package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionSort;
import com.adn.dabaeum.course.domain.CourseSessionSortDirection;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseSessionMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseSessionRepository repository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void roundTripsNullableFieldsAndSupportsPageCountAndUpdate() {
        Course course = course();
        courseRepository.save(course);
        CourseSession first = session(course.id(), 1, null);
        CourseSession second = session(course.id(), 2, "강의실");
        repository.save(first);
        repository.save(second);

        assertThat(repository.findById(first.id())).contains(first);
        assertThat(repository.findByIdForUpdate(first.id())).contains(first);
        assertThat(repository.findByCourseIdAndSessionNo(course.id(), 1))
            .contains(first);
        assertThat(repository.findPageByCourseId(new CourseSessionPageCriteria(
            course.id(), 0, 10, CourseSessionSort.SESSION_NO,
            CourseSessionSortDirection.ASC
        ))).extracting(CourseSession::sessionNo).containsExactly(1, 2);
        assertThat(repository.countByCourseId(course.id())).isEqualTo(2);

        CourseSession updated = new CourseSession(
            first.id(), first.courseId(), first.sessionNo(), first.startsAt(), first.endsAt(),
            "온라인", first.attendanceOpensAt(), first.attendanceClosesAt(),
            CourseSessionStatus.OPEN, first.createdAt(), first.updatedAt().plusSeconds(1)
        );
        assertThat(repository.update(updated)).isTrue();
        assertThat(repository.findById(first.id())).contains(updated);
        assertThat(repository.update(updatedWithUnknownId(updated))).isFalse();
    }

    @Test
    void allowsSameSessionNumberForDifferentCoursesAndRejectsDuplicateWithinCourse() {
        Course firstCourse = course();
        Course secondCourse = course();
        courseRepository.save(firstCourse);
        courseRepository.save(secondCourse);
        repository.save(session(firstCourse.id(), 1, null));
        repository.save(session(secondCourse.id(), 1, null));
        assertThat(repository.findByCourseIdAndSessionNo(secondCourse.id(), 1)).isPresent();

        assertSqlState("23505", () -> repository.save(
            session(firstCourse.id(), 1, "중복")
        ));
    }

    private CourseSession updatedWithUnknownId(CourseSession source) {
        return new CourseSession(
            UUID.randomUUID(), source.courseId(), source.sessionNo(), source.startsAt(),
            source.endsAt(), source.location(), source.attendanceOpensAt(),
            source.attendanceClosesAt(), source.status(), source.createdAt(), source.updatedAt()
        );
    }

    private CourseSession session(UUID courseId, int number, String location) {
        Instant starts = Instant.parse("2099-09-01T00:00:00Z").plusSeconds(number * 86400L);
        Instant ends = starts.plusSeconds(7200);
        return new CourseSession(UUID.randomUUID(), courseId, number, starts, ends, location,
            null, null, CourseSessionStatus.SCHEDULED, starts.minusSeconds(86400), starts);
    }

    private Course course() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(
            UUID.randomUUID(), uniqueCode("INST"), "세션 테스트 기관", null, null, null, null,
            null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), "세션 과정",
            null, null, CourseEducationType.HYBRID, java.time.LocalDate.of(2099, 9, 1),
            java.time.LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            com.adn.dabaeum.course.domain.CourseStatus.DRAFT, now, now, null);
    }
}
