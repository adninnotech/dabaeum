package com.adn.dabaeum.popularity.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.popularity.domain.PopularCourseScore;
import com.adn.dabaeum.popularity.domain.PopularityRepository;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인기 강좌 조회 (설계서 §8.3). 집계가 없으면 빈 목록 — 실시간 계산으로 대신하지 않는다. */
@Service
@Profile({"local", "dev"})
public class PopularCourseService {

    public static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final Set<String> AGE_BANDS = Set.of("10", "20", "30", "40", "50", "60", "70");
    private static final Set<CourseStatus> HIDDEN = Set.of(CourseStatus.DRAFT, CourseStatus.CANCELLED);

    private final PopularityRepository popularity;
    private final CourseRepository courses;
    private final PopularityProperties properties;

    public PopularCourseService(PopularityRepository popularity, CourseRepository courses, PopularityProperties properties) {
        this.popularity = Objects.requireNonNull(popularity);
        this.courses = Objects.requireNonNull(courses);
        this.properties = Objects.requireNonNull(properties);
    }

    public record PopularCourse(CourseView course, PopularCourseScore score) {
    }

    @Transactional(readOnly = true)
    public List<PopularCourse> top(String ageBand, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw badRequest("limit");
        }
        PopularitySegment segment = PopularitySegment.ALL;
        String key = "*";
        if (ageBand != null) {
            if (!AGE_BANDS.contains(ageBand)) {
                throw badRequest("ageBand");
            }
            key = ageBand;
            segment = PopularitySegment.AGE_BAND;
        }
        // 집계는 매시 갱신이라 그 사이 비공개로 바뀐 강좌는 여기서 한 번 더 거른다.
        List<PopularCourseScore> scores = popularity.top(segment, key, properties.windowDays(), limit);
        Map<UUID, CourseView> views = courses.findActiveViewsByIds(
                scores.stream().map(PopularCourseScore::courseId).toList()).stream()
            .filter(view -> !HIDDEN.contains(view.course().status()))
            .collect(Collectors.toMap(view -> view.course().id(), Function.identity()));
        return scores.stream()
            .filter(score -> views.containsKey(score.courseId()))
            .map(score -> new PopularCourse(views.get(score.courseId()), score))
            .toList();
    }

    private static ApiException badRequest(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Invalid popular course query", List.of(field));
    }
}
