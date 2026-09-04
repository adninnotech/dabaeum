package com.adn.dabaeum.notification.api;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.notification.application.NotificationApplicationService;
import com.adn.dabaeum.notification.application.NotificationPage;
import com.adn.dabaeum.notification.domain.Notification;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class NotificationController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final NotificationApplicationService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public NotificationController(
        NotificationApplicationService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/users/me/notifications")
    public NotificationPageResponse listMine(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        HttpServletRequest servletRequest
    ) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
        NotificationPage result = service.listMine(
            currentUserProvider.requireContext(), unreadOnly, page, size);
        return new NotificationPageResponse(
            new NotificationPageData(
                result.data().stream().map(this::toResponse).toList(),
                result.unreadCount(),
                new PageMeta(result.page(), result.size(),
                    result.totalElements(), result.totalPages())),
            meta(requestId(servletRequest)));
    }

    @PostMapping("/users/me/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
        @PathVariable UUID notificationId,
        HttpServletRequest servletRequest
    ) {
        Notification notification = service.markRead(
            currentUserProvider.requireContext(), notificationId);
        return new ApiResponse<>(
            toResponse(notification), meta(requestId(servletRequest)));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.id(), notification.title(), notification.body(),
            notification.readAt() != null, notification.createdAt());
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
