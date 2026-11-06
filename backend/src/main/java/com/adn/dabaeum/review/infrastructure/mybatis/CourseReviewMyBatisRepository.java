package com.adn.dabaeum.review.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.review.domain.CourseReview;
import com.adn.dabaeum.review.domain.CourseReviewRepository;
import com.adn.dabaeum.review.domain.CourseReviewSummary;
import com.adn.dabaeum.review.domain.CourseReviewView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public List<CourseReviewView> findByUserId(
        UUID userId, String keyword, int limit, int offset
    ) {
        return mapper.selectByUserId(userId, keyword, limit, offset)
            .stream().map(this::toView).toList();
    }

    @Override
    public long countByUserId(UUID userId, String keyword) {
        return mapper.countByUserId(userId, keyword);
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

    @Override
    public CourseReviewSummary summarizeByCourseId(UUID courseId) {
        return toSummary(mapper.summarizeByCourseId(courseId));
    }

    @Override
    public CourseReviewSummary summarizeByUserId(UUID userId) {
        return toSummary(mapper.summarizeByUserId(userId));
    }

    @Override
    public long countWritable(UUID userId) {
        return mapper.countWritable(userId);
    }

    private CourseReviewSummary toSummary(ReviewSummaryRow row) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("star5", row.count5());
        distribution.put("star4", row.count4());
        distribution.put("star3", row.count3());
        distribution.put("star2", row.count2());
        distribution.put("star1", row.count1());
        BigDecimal averageRating = row.averageRating() == null
            ? null : row.averageRating().setScale(2, RoundingMode.HALF_UP);
        return new CourseReviewSummary(row.totalCount(), averageRating, distribution);
    }

    private CourseReviewView toView(CourseReviewViewRow row) {
        return new CourseReviewView(
            row.id(), row.userId(), row.courseId(), row.rating(), row.content(),
            row.createdAt(), row.courseTitle(), row.userName(), row.institutionName(),
            row.instructorName(), row.classificationCode(), row.educationType(),
            row.sessionCount(), row.tags(),
            row.tuitionType() == null ? null : CourseFeeType.valueOf(row.tuitionType()),
            row.tuitionAmount(), row.thumbnailFileId());
    }
}
