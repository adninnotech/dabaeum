package com.adn.dabaeum.classification.api;

import com.adn.dabaeum.classification.application.CourseClassificationNode;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 강좌 분류 트리와 사용 여부 (설계서 §12.4). 관리자 경로는 규칙표가 PLATFORM_ADMIN 으로 막는다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class CourseClassificationController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CourseClassificationService service;
    private final Clock clock;

    public CourseClassificationController(CourseClassificationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/course-classifications")
    public ApiResponse<List<CourseClassificationNode>> list(HttpServletRequest request) {
        return new ApiResponse<>(service.tree(true), meta(request));
    }

    @GetMapping("/admin/course-classifications")
    public ApiResponse<List<CourseClassificationNode>> listAll(HttpServletRequest request) {
        return new ApiResponse<>(service.tree(false), meta(request));
    }

    @PostMapping("/admin/course-classifications/{code}/enable")
    public ApiResponse<CourseClassificationNode> enable(@PathVariable String code, HttpServletRequest request) {
        return new ApiResponse<>(service.setEnabled(code, true), meta(request));
    }

    @PostMapping("/admin/course-classifications/{code}/disable")
    public ApiResponse<CourseClassificationNode> disable(@PathVariable String code, HttpServletRequest request) {
        return new ApiResponse<>(service.setEnabled(code, false), meta(request));
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
