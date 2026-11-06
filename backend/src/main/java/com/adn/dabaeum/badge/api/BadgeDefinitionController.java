package com.adn.dabaeum.badge.api;

import com.adn.dabaeum.badge.application.BadgeDefinitionService;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 배지 도감(공개)과 정의 관리(플랫폼 관리자) — 설계서 §11.3. 관리자 경로는 규칙표가 막는다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class BadgeDefinitionController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BadgeDefinitionService service;
    private final Clock clock;

    public BadgeDefinitionController(BadgeDefinitionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/badge-definitions")
    public ApiResponse<List<BadgeDefinitionResponse>> catalog(HttpServletRequest request) {
        return new ApiResponse<>(responses(service.list(true)), meta(request));
    }

    @GetMapping("/admin/badge-definitions")
    public ApiResponse<List<BadgeDefinitionResponse>> listAll(HttpServletRequest request) {
        return new ApiResponse<>(responses(service.list(false)), meta(request));
    }

    @PostMapping("/admin/badge-definitions")
    public ResponseEntity<ApiResponse<BadgeDefinitionResponse>> create(
        @Valid @RequestBody BadgeDefinitionRequest body, HttpServletRequest request
    ) {
        BadgeDefinitionResponse created = BadgeDefinitionResponse.from(service.create(body.toCommand()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>(created, meta(request)));
    }

    @GetMapping("/admin/badge-definitions/{definitionId}")
    public ApiResponse<BadgeDefinitionResponse> get(@PathVariable UUID definitionId, HttpServletRequest request) {
        return new ApiResponse<>(BadgeDefinitionResponse.from(service.get(definitionId)), meta(request));
    }

    @PutMapping("/admin/badge-definitions/{definitionId}")
    public ApiResponse<BadgeDefinitionResponse> update(
        @PathVariable UUID definitionId, @Valid @RequestBody BadgeDefinitionRequest body, HttpServletRequest request
    ) {
        return new ApiResponse<>(
            BadgeDefinitionResponse.from(service.update(definitionId, body.toCommand())), meta(request));
    }

    @DeleteMapping("/admin/badge-definitions/{definitionId}")
    public ApiResponse<DeletionResponse> delete(@PathVariable UUID definitionId, HttpServletRequest request) {
        service.delete(definitionId);
        return new ApiResponse<>(new DeletionResponse(definitionId), meta(request));
    }

    @PostMapping("/admin/badge-definitions/{definitionId}/activate")
    public ApiResponse<BadgeDefinitionResponse> activate(@PathVariable UUID definitionId, HttpServletRequest request) {
        return new ApiResponse<>(BadgeDefinitionResponse.from(service.setActive(definitionId, true)), meta(request));
    }

    @PostMapping("/admin/badge-definitions/{definitionId}/deactivate")
    public ApiResponse<BadgeDefinitionResponse> deactivate(@PathVariable UUID definitionId, HttpServletRequest request) {
        return new ApiResponse<>(BadgeDefinitionResponse.from(service.setActive(definitionId, false)), meta(request));
    }

    private static List<BadgeDefinitionResponse> responses(List<com.adn.dabaeum.badge.domain.BadgeDefinition> all) {
        return all.stream().map(BadgeDefinitionResponse::from).toList();
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta(
            (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }
}
