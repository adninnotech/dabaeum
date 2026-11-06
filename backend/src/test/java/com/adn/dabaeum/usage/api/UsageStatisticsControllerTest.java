package com.adn.dabaeum.usage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.usage.application.UsageStatisticsService;
import com.adn.dabaeum.usage.application.UsageStatisticsService.BannerClicks;
import com.adn.dabaeum.usage.application.UsageStatisticsService.Bucket;
import com.adn.dabaeum.usage.application.UsageStatisticsService.Demographics;
import com.adn.dabaeum.usage.application.UsageStatisticsService.KeywordRanking;
import com.adn.dabaeum.usage.application.UsageStatisticsService.Summary;
import com.adn.dabaeum.usage.domain.BannerClickCount;
import com.adn.dabaeum.usage.domain.KeywordCount;
import com.adn.dabaeum.usage.domain.KeywordRankingType;
import com.adn.dabaeum.usage.domain.UsageCounts;
import com.adn.dabaeum.usage.domain.UsageInterval;
import com.adn.dabaeum.usage.domain.UsageMetric;
import com.adn.dabaeum.usage.domain.UsageTrendRow;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** docs/specs/usage-statistics.md §6 — 응답이 계약과 맞는지, CSV 를 내려주는지. 인가는 규칙표 테스트가 본다. */
@WebMvcTest(UsageStatisticsController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=usage-stat-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UsageStatisticsControllerTest {

    private static final String TOKEN = "Bearer usage-stat-token";
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 2);
    private static final UsageCounts COUNTS = new UsageCounts(10, 4, 6, 1, 2);

    @Autowired MockMvc mockMvc;
    @MockitoBean UsageStatisticsService service;

    @Test
    void summaryMatchesContract() throws Exception {
        when(service.summary(isNull(), isNull(), isNull())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/admin/statistics/usage").header("Authorization", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totals.visitors").value(10))
            .andExpect(jsonPath("$.data.rows[1].period").value("2026-10-02"));
    }

    @Test
    void summaryCsvHasOneLinePerPeriod() throws Exception {
        when(service.summary(isNull(), isNull(), isNull())).thenReturn(summary());

        String body = new String(mockMvc.perform(get("/api/v1/admin/statistics/usage").param("format", "csv")
                .header("Authorization", TOKEN))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(body.lines()).hasSize(3);
        assertThat(body).contains("\"2026-10-01\",\"5\",\"2\"");
    }

    @Test
    void demographicsMatchesContract() throws Exception {
        when(service.demographics(eq(UsageMetric.VISIT), any(), any())).thenReturn(new Demographics(
            UsageMetric.VISIT, FROM, TO, 10, 6, 4,
            List.of(new Bucket("MALE", "남성", 2), new Bucket("FEMALE", "여성", 4),
                new Bucket("UNDISCLOSED", "밝히지 않음", 0), new Bucket("UNKNOWN", "미상", 0)),
            List.of(new Bucket("30", "30대", 6))));

        mockMvc.perform(get("/api/v1/admin/statistics/usage/demographics").param("metric", "VISIT")
                .header("Authorization", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.guests").value(4));
    }

    @Test
    void searchKeywordsMatchesContract() throws Exception {
        when(service.keywords(eq(KeywordRankingType.POPULAR), any(), any(), any())).thenReturn(
            new KeywordRanking(KeywordRankingType.POPULAR, FROM, TO, List.of(new KeywordCount("엑셀", 7))));

        mockMvc.perform(get("/api/v1/admin/statistics/usage/search-keywords").param("type", "POPULAR")
                .header("Authorization", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows[0].keyword").value("엑셀"));
    }

    @Test
    void bannersMatchesContract() throws Exception {
        when(service.banners(any(), any())).thenReturn(new BannerClicks(FROM, TO,
            List.of(new BannerClickCount(UUID.randomUUID(), "가을 강좌", false, 3))));

        mockMvc.perform(get("/api/v1/admin/statistics/usage/banners").header("Authorization", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows[0].clickers").value(3));
    }

    private static Summary summary() {
        return new Summary(FROM, TO, UsageInterval.DAY, COUNTS, LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 9, 30), UsageCounts.ZERO, List.of(
                new UsageTrendRow("2026-10-01", 5, 2, 3, 1, 1),
                new UsageTrendRow("2026-10-02", 5, 2, 3, 0, 1)));
    }
}
