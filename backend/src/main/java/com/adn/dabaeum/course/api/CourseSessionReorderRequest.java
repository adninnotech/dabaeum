package com.adn.dabaeum.course.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * 드래그 핸들로 바뀐 회차 순서. {@code sessionIds} 는 과정의 회차 전부를 정확히
 * 한 번씩, 새 순서대로 담아야 한다 — 나머지는 서비스가 422 로 거부한다.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CourseSessionReorderRequest(
    @NotEmpty List<UUID> sessionIds
) {
}
