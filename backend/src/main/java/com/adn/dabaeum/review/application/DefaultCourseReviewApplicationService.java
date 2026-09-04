package com.adn.dabaeum.review.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.review.domain.CourseReview;
import com.adn.dabaeum.review.domain.CourseReviewRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultCourseReviewApplicationService
    implements CourseReviewApplicationService {

    private final CourseReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final Clock clock;

    public DefaultCourseReviewApplicationService(
        CourseReviewRepository reviewRepository,
        CourseRepository courseRepository,
        Clock clock
    ) {
        this.reviewRepository = reviewRepository;
        this.courseRepository = courseRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CourseReview create(
        AuthenticatedUserContext actor, UUID courseId, int rating, String content
    ) {
        Objects.requireNonNull(actor, "actor");
        courseRepository.findActiveById(courseId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.COURSE_NOT_FOUND,
                "Course not found"));
        if (!reviewRepository.hasConfirmedCompletion(actor.userId(), courseId)) {
            throw new ApiException(
                HttpStatus.FORBIDDEN, ApiErrorCode.REVIEW_NOT_ELIGIBLE,
                "Only completed learners can write a review");
        }
        if (reviewRepository.existsByUserIdAndCourseId(actor.userId(), courseId)) {
            throw reviewConflict();
        }
        Instant now = clock.instant();
        CourseReview review = new CourseReview(
            UUID.randomUUID(), actor.userId(), courseId, rating, content, now, now);
        try {
            reviewRepository.save(review);
        } catch (DataIntegrityViolationException exception) {
            throw reviewConflict();
        }
        return review;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseReviewPage listMine(AuthenticatedUserContext actor, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        var data = reviewRepository.findByUserId(actor.userId(), size, offset);
        long totalElements = reviewRepository.countByUserId(actor.userId());
        return new CourseReviewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public CourseReviewPage listByCourse(UUID courseId, int page, int size) {
        int offset = Math.multiplyExact(page, size);
        var data = reviewRepository.findByCourseId(courseId, size, offset);
        long totalElements = reviewRepository.countByCourseId(courseId);
        return new CourseReviewPage(
            data, page, size, totalElements, totalPages(totalElements, size));
    }

    private ApiException reviewConflict() {
        return new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.REVIEW_CONFLICT,
            "Review already exists for this course");
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
