package com.adn.dabaeum.interest.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.interest.domain.CourseInterest;
import com.adn.dabaeum.interest.domain.CourseInterestRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultCourseInterestApplicationService
    implements CourseInterestApplicationService {

    private final CourseInterestRepository interestRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public DefaultCourseInterestApplicationService(
        CourseInterestRepository interestRepository,
        CourseRepository courseRepository,
        Clock clock
    ) {
        this.interestRepository = interestRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CourseInterest add(AuthenticatedUserContext actor, UUID courseId) {
        Objects.requireNonNull(actor, "actor");
        if (courseId == null) {
            throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED,
                "Request validation failed", java.util.List.of("courseId"));
        }
        courseRepository.findActiveById(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND,
                "Course not found"));
        Optional<CourseInterest> existing =
            interestRepository.findByUserIdAndCourseId(actor.userId(), courseId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CourseInterest interest = new CourseInterest(
            UUID.randomUUID(), actor.userId(), courseId, clock.instant());
        try {
            interestRepository.save(interest);
        } catch (DataIntegrityViolationException exception) {
            return interestRepository.findByUserIdAndCourseId(actor.userId(), courseId)
                .orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT, ApiErrorCode.INTEREST_CONFLICT,
                    "Interest was changed concurrently"));
        }
        return interest;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseInterestPage listMine(AuthenticatedUserContext actor, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        var data = interestRepository.findByUserId(actor.userId(), size, offset);
        long totalElements = interestRepository.countByUserId(actor.userId());
        return new CourseInterestPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional
    public void remove(AuthenticatedUserContext actor, UUID interestId) {
        CourseInterest interest = interestRepository.findById(interestId)
            .orElseThrow(this::interestNotFound);
        if (!Objects.equals(interest.userId(), actor.userId())) {
            throw interestNotFound();
        }
        if (!interestRepository.delete(interestId)) {
            throw interestNotFound();
        }
    }

    private ApiException interestNotFound() {
        return new ApiException(
            HttpStatus.NOT_FOUND, ApiErrorCode.INTEREST_NOT_FOUND,
            "Interest not found");
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
