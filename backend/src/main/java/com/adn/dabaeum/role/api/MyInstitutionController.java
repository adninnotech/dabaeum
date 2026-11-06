package com.adn.dabaeum.role.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.application.MyInstitution;
import com.adn.dabaeum.role.application.MyInstitutionApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MyInstitutionController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MyInstitutionApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public MyInstitutionController(
        MyInstitutionApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/users/me/institutions")
    public ApiResponse<List<MyInstitutionResponse>> listMine(
        HttpServletRequest servletRequest
    ) {
        List<MyInstitutionResponse> data = service.listMine(
                currentUserProvider.requireContext())
            .stream()
            .map(this::toResponse)
            .toList();
        return new ApiResponse<>(data, meta(requestId(servletRequest)));
    }

    private MyInstitutionResponse toResponse(MyInstitution institution) {
        return new MyInstitutionResponse(
            institution.institutionId(), institution.name(), institution.role());
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
