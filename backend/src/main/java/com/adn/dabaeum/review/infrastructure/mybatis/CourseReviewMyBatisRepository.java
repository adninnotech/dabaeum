package com.adn.dabaeum.review.infrastructure.mybatis;

import com.adn.dabaeum.review.domain.CourseReview;
import com.adn.dabaeum.review.domain.CourseReviewRepository;
import com.adn.dabaeum.review.domain.CourseReviewView;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class CourseReviewMyBatisRepository implements CourseReviewRepository {

    private final CourseReviewMapper mapper;

    public CourseReviewMyBatisRepository(CourseReviewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CourseReview review) {
        mapper.insert(new CourseReviewRow(
            review.id(), review.userId(), review.courseId(), review.rating(),
            review.content(), review.createdAt(), review.updatedAt()));
    }

    @Override
    public boolean existsByUserIdAndCourseId(UUID userId, UUID courseId) {
        return mapper.existsByUserIdAndCourseId(userId, courseId);
    }

    @Override
    public boolean hasConfirmedCompletion(UUID userId, UUID courseId) {
        return mapper.hasConfirmedCompletion(userId, courseId);
    }

    @Override
    public List<CourseReviewView> findByUserId(UUID userId, int limit, int offset) {
        return mapper.selectByUserId(userId, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByUserId(UUID userId) {
        return mapper.countByUserId(userId);
    }

    @Override
    public List<CourseReviewView> findByCourseId(UUID courseId, int limit, int offset) {
        return mapper.selectByCourseId(courseId, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByCourseId(UUID courseId) {
        return mapper.countByCourseId(courseId);
    }

    private CourseReviewView toView(CourseReviewViewRow row) {
        return new CourseReviewView(
            row.id(), row.userId(), row.courseId(), row.rating(), row.content(),
            row.createdAt(), row.courseTitle(), row.userName());
    }
}
