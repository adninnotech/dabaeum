package com.adn.dabaeum.cms.api;

import com.adn.dabaeum.cms.application.CmsService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.DeletionResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

/** CMS 메뉴·콘텐츠 (설계서 §7). 관리자 경로는 규칙표가 PLATFORM_ADMIN 으로 막는다. */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class CmsController {

    private final CmsService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CmsController(CmsService service, CurrentUserProvider currentUserProvider, Clock clock) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    // ---- 공개 ----

    @GetMapping("/menus")
    public ApiResponse<List<CmsDtos.PublicMenuNode>> publicMenus(HttpServletRequest request) {
        return new ApiResponse<>(service.publicMenuTree().stream().map(CmsDtos.PublicMenuNode::from).toList(),
            meta(request));
    }

    @GetMapping("/contents/pages/{slug}")
    public ApiResponse<CmsDtos.PublicPage> publicPage(@PathVariable String slug, HttpServletRequest request) {
        return new ApiResponse<>(CmsDtos.PublicPage.from(service.publishedPage(slug)), meta(request));
    }

    // ---- 콘텐츠 페이지 관리 ----

    @GetMapping("/admin/content-pages")
    public ApiResponse<List<CmsDtos.PageSummary>> pages(HttpServletRequest request) {
        return new ApiResponse<>(service.pages().stream().map(CmsDtos.PageSummary::from).toList(), meta(request));
    }

    @PostMapping("/admin/content-pages")
    public ResponseEntity<ApiResponse<CmsDtos.PageDetail>> createPage(
        @RequestBody(required = false) CmsDtos.PageRequest body, HttpServletRequest request
    ) {
        CmsDtos.PageDetail created = CmsDtos.PageDetail.from(
            service.createPage(currentUserProvider.requireUserId(), requireBody(body).toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/content-pages/" + created.id()))
            .body(new ApiResponse<>(created, meta(request)));
    }

    @GetMapping("/admin/content-pages/{pageId}")
    public ApiResponse<CmsDtos.PageDetail> page(@PathVariable UUID pageId, HttpServletRequest request) {
        return new ApiResponse<>(CmsDtos.PageDetail.from(service.page(pageId)), meta(request));
    }

    @PutMapping("/admin/content-pages/{pageId}")
    public ApiResponse<CmsDtos.PageDetail> updatePage(
        @PathVariable UUID pageId, @RequestBody(required = false) CmsDtos.PageRequest body, HttpServletRequest request
    ) {
        return new ApiResponse<>(CmsDtos.PageDetail.from(
            service.updatePage(currentUserProvider.requireUserId(), pageId, requireBody(body).toCommand())),
            meta(request));
    }

    @DeleteMapping("/admin/content-pages/{pageId}")
    public ApiResponse<DeletionResponse> deletePage(@PathVariable UUID pageId, HttpServletRequest request) {
        service.deletePage(pageId);
        return new ApiResponse<>(new DeletionResponse(pageId), meta(request));
    }

    @PostMapping("/admin/content-pages/{pageId}/publish")
    public ApiResponse<CmsDtos.PageDetail> publish(@PathVariable UUID pageId, HttpServletRequest request) {
        return new ApiResponse<>(CmsDtos.PageDetail.from(
            service.publish(currentUserProvider.requireUserId(), pageId, true)), meta(request));
    }

    @PostMapping("/admin/content-pages/{pageId}/unpublish")
    public ApiResponse<CmsDtos.PageDetail> unpublish(@PathVariable UUID pageId, HttpServletRequest request) {
        return new ApiResponse<>(CmsDtos.PageDetail.from(
            service.publish(currentUserProvider.requireUserId(), pageId, false)), meta(request));
    }

    // ---- 메뉴 관리 ----

    @GetMapping("/admin/menus")
    public ApiResponse<List<CmsDtos.AdminMenuNode>> menus(HttpServletRequest request) {
        return new ApiResponse<>(service.menuTree().stream().map(CmsDtos.AdminMenuNode::from).toList(), meta(request));
    }

    @GetMapping("/admin/menus/routes")
    public ApiResponse<List<CmsDtos.RouteItem>> routes(HttpServletRequest request) {
        return new ApiResponse<>(service.routes().stream().map(CmsDtos.RouteItem::from).toList(), meta(request));
    }

    @PostMapping("/admin/menus")
    public ResponseEntity<ApiResponse<CmsDtos.AdminMenu>> createMenu(
        @RequestBody(required = false) CmsDtos.MenuRequest body, HttpServletRequest request
    ) {
        CmsDtos.AdminMenu created = CmsDtos.AdminMenu.from(service.createMenu(requireBody(body).toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/menus/" + created.id()))
            .body(new ApiResponse<>(created, meta(request)));
    }

    @PutMapping("/admin/menus/order")
    public ApiResponse<List<CmsDtos.AdminMenuNode>> reorder(
        @RequestBody(required = false) List<CmsDtos.MenuOrderItem> body, HttpServletRequest request
    ) {
        return new ApiResponse<>(service.reorder(requireBody(body).stream().map(CmsDtos.MenuOrderItem::toOrder).toList())
            .stream().map(CmsDtos.AdminMenuNode::from).toList(), meta(request));
    }

    @PutMapping("/admin/menus/{menuId}")
    public ApiResponse<CmsDtos.AdminMenu> updateMenu(
        @PathVariable UUID menuId, @RequestBody(required = false) CmsDtos.MenuRequest body, HttpServletRequest request
    ) {
        return new ApiResponse<>(CmsDtos.AdminMenu.from(service.updateMenu(menuId, requireBody(body).toCommand())),
            meta(request));
    }

    @DeleteMapping("/admin/menus/{menuId}")
    public ApiResponse<DeletionResponse> deleteMenu(@PathVariable UUID menuId, HttpServletRequest request) {
        service.deleteMenu(menuId);
        return new ApiResponse<>(new DeletionResponse(menuId), meta(request));
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
                "Request body is required", List.of("body"));
        }
        return body;
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
    }
}
