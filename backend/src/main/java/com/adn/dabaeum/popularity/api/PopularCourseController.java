package com.adn.dabaeum.popularity.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.api.CourseApiMapper;
import com.adn.dabaeum.course.api.CourseResponse;
import com.adn.dabaeum.popularity.application.PopularCourseService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인기 강좌 (설계서 §8.3). 무인증. ageBand 가 없으면 전체. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1/courses")
public class PopularCourseController {

    private final PopularCourseService service;
    private final CourseApiMapper courseMapper;
    private final Clock clock;

    public PopularCourseController(PopularCourseService service, CourseApiMapper courseMapper, Clock clock) {
        this.service = service;
        this.courseMapper = courseMapper;
        this.clock = clock;
    }

    public record Popularity(int enrollmentCount, int viewCount, BigDecimal score) {
    }

    public record PopularCourseResponse(CourseResponse course, Popularity popularity) {
    }

    @GetMapping("/popular")
    public ApiResponse<List<PopularCourseResponse>> popular(
        @RequestParam(required = false) String ageBand,
        @RequestParam(defaultValue = "" + PopularCourseService.DEFAULT_LIMIT) int limit,
        HttpServletRequest request
    ) {
        List<PopularCourseResponse> data = service.top(ageBand, limit).stream()
            .map(item -> new PopularCourseResponse(
                courseMapper.toResponse(item.course()),
                new Popularity(item.score().enrollmentCount(), item.score().viewCount(), item.score().score())))
            .toList();
        return new ApiResponse<>(data, new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))));
    }
}
