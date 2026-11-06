package com.adn.dabaeum.banner.api;

import com.adn.dabaeum.banner.application.BannerService;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class BannerController {

    private final BannerService service;
    private final BannerApiMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public BannerController(
        BannerService service,
        BannerApiMapper mapper,
        CurrentUserProvider currentUserProvider
    ) {
        this.service = service;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    /** E-1 홈 최상단 캐러셀. 비로그인 방문자도 본다. */
    @GetMapping("/banners")
    public ApiResponse<List<BannerResponse>> list(HttpServletRequest servletRequest) {
        return mapper.toListResponse(service.list(true), requestId(servletRequest));
    }

    /** B-10 배너 관리. 비공개·기간 밖 배너까지 전부 준다. */
    @GetMapping("/admin/banners")
    public ApiResponse<List<BannerResponse>> listAdmin(HttpServletRequest servletRequest) {
        return mapper.toListResponse(service.list(false), requestId(servletRequest));
    }

    @PostMapping("/admin/banners")
    public ResponseEntity<ApiResponse<BannerResponse>> create(
        @Valid @RequestBody BannerRequest request,
        HttpServletRequest servletRequest
    ) {
        var banner = service.create(
            mapper.toCommand(request), currentUserProvider.requireContext());
        return ResponseEntity.status(HttpStatus.CREATED)
            .location(URI.create("/api/v1/admin/banners/" + banner.id()))
            .body(mapper.toResponse(banner, requestId(servletRequest)));
    }

    @PutMapping("/admin/banners/{bannerId}")
    public ApiResponse<BannerResponse> update(
        @PathVariable UUID bannerId,
        @Valid @RequestBody BannerRequest request,
        HttpServletRequest servletRequest
    ) {
        var banner = service.update(
            bannerId, mapper.toCommand(request), currentUserProvider.requireContext());
        return mapper.toResponse(banner, requestId(servletRequest));
    }

    @DeleteMapping("/admin/banners/{bannerId}")
    public ApiResponse<DeletionResponse> delete(
        @PathVariable UUID bannerId,
        HttpServletRequest servletRequest
    ) {
        service.delete(bannerId, currentUserProvider.requireContext());
        return new ApiResponse<>(
            new DeletionResponse(bannerId), mapper.meta(requestId(servletRequest)));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
