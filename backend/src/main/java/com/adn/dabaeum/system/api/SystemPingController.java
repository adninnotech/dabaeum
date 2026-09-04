package com.adn.dabaeum.system.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemPingController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public SystemPingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), SEOUL);

        return new ApiResponse<>(
            Map.of("status", "OK"),
            new ApiMeta(requestId, now)
        );
    }
}
