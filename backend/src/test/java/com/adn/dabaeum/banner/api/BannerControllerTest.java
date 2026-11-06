package com.adn.dabaeum.banner.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.banner.application.BannerService;
import com.adn.dabaeum.banner.domain.Banner;
import com.adn.dabaeum.banner.domain.BannerVisibility;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 스펙 #19·B-10·E-1: 배너 CRUD 와 공개 캐러셀 조회. */
@WebMvcTest(BannerController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=banner-token")
@Import({
    BannerApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class BannerControllerTest {

    private static final UUID ADMIN_ID = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean BannerService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 공개_목록은_보이는_배너만_정렬순으로_준다() throws Exception {
        lenient().when(service.list(true)).thenReturn(List.of(
            banner("첫 번째 배너", BannerVisibility.PUBLIC, 0, null, null),
            banner("두 번째 배너", BannerVisibility.SCHEDULED, 1,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/banners").with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("첫 번째 배너"))
            .andExpect(jsonPath("$.data[*].visibility").value(not(hasItem("PRIVATE"))));
    }

    @Test
    void 공개_목록은_로그인하지_않아도_볼_수_있다() throws Exception {
        // 홈 최상단이라 비로그인 방문자도 본다
        lenient().when(service.list(true)).thenReturn(List.of(
            banner("첫 번째 배너", BannerVisibility.PUBLIC, 0, null, null)));

        mockMvc.perform(get("/api/v1/banners"))
            .andExpect(status().isOk());
    }

    @Test
    void 관리자_목록은_비공개도_전부_준다() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        lenient().when(service.list(false)).thenReturn(List.of(
            banner("첫 번째 배너", BannerVisibility.PUBLIC, 0, null, null),
            banner("숨긴 배너", BannerVisibility.PRIVATE, 1, null, null)));

        mockMvc.perform(get("/api/v1/admin/banners")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].visibility").value(hasItem("PRIVATE")));
    }

    @Test
    void 학습자는_배너를_만들_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/banners")
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"내 배너\", \"visibility\": \"PUBLIC\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void 기간공개인데_기간이_없으면_422() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        lenient().when(service.create(any(), any())).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of("startAt")));

        mockMvc.perform(post("/api/v1/admin/banners")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"기간 배너\", \"visibility\": \"SCHEDULED\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.details").value(hasItem("startAt")));
    }

    @Test
    void 상시공개인데_기간이_있으면_422() throws Exception {
        lenient().when(currentUserProvider.requireContext()).thenReturn(admin());
        lenient().when(service.create(any(), any())).thenThrow(new ApiException(
            HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorCode.VALIDATION_FAILED,
            "Request validation failed", List.of("endAt")));

        mockMvc.perform(post("/api/v1/admin/banners")
                .with(user("admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"상시 배너\", \"visibility\": \"PUBLIC\", "
                    + "\"endAt\": \"2026-09-30T00:00:00Z\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.details").value(hasItem("endAt")));
    }

    private Banner banner(
        String title,
        BannerVisibility visibility,
        int sortOrder,
        Instant startAt,
        Instant endAt
    ) {
        return new Banner(
            UUID.randomUUID(), null, title, null, null, null,
            visibility, startAt, endAt, sortOrder, null, null, NOW, NOW, null);
    }

    private AuthenticatedUserContext admin() {
        return new AuthenticatedUserContext(ADMIN_ID, "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null)));
    }
}
