package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.dashboard.domain.PlatformDashboard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 카테고리 비율의 반올림·0으로 나누기 방어를 확인한다. 각 행의 percentage 합이
 * 100이 아닐 수 있는 것은 반올림 특성상 정상이라 그 합은 검증하지 않는다.
 */
class DashboardMyBatisRepositoryTest {

    private static final UUID INSTITUTION_ID = UUID.randomUUID();

    private final DashboardMapper mapper = mock(DashboardMapper.class);
    private final DashboardMyBatisRepository repository =
        new DashboardMyBatisRepository(mapper);

    @Test
    void 카테고리별_비율을_반올림해서_계산한다() {
        when(mapper.selectInstitutionCounts(INSTITUTION_ID)).thenReturn(null);
        when(mapper.selectInstitutionCategoryCounts(INSTITUTION_ID)).thenReturn(List.of(
            new CategoryCountRow("인문교양", 2L),
            new CategoryCountRow("IT", 1L)
        ));

        InstitutionDashboard dashboard = repository.institutionDashboard(INSTITUTION_ID);

        assertThat(dashboard.categoryRatios()).containsExactly(
            new InstitutionDashboard.CategoryRatio("인문교양", 2, 67),
            new InstitutionDashboard.CategoryRatio("IT", 1, 33)
        );
    }

    @Test
    void 강좌가_없으면_카테고리_비율은_빈_배열이고_0으로_나누지_않는다() {
        when(mapper.selectInstitutionCounts(INSTITUTION_ID)).thenReturn(null);
        when(mapper.selectInstitutionCategoryCounts(INSTITUTION_ID)).thenReturn(List.of());

        InstitutionDashboard dashboard = repository.institutionDashboard(INSTITUTION_ID);

        assertThat(dashboard.categoryRatios()).isEmpty();
        assertThat(dashboard.courseSummary())
            .isEqualTo(new InstitutionDashboard.CourseSummary(0, 0, 0));
    }

    @Test
    void 강좌_상태별_분해를_그대로_전달한다() {
        InstitutionCountsRow counts = new InstitutionCountsRow(
            10L, 8L, 1L, 1L, 5L, 42L, 2L, 1L, 2L, 30L, 12L);
        when(mapper.selectInstitutionCounts(INSTITUTION_ID)).thenReturn(counts);
        when(mapper.selectInstitutionCategoryCounts(any())).thenReturn(List.of());

        InstitutionDashboard dashboard = repository.institutionDashboard(INSTITUTION_ID);

        assertThat(dashboard.courseSummary())
            .isEqualTo(new InstitutionDashboard.CourseSummary(2, 1, 2));
        assertThat(dashboard.courseCount()).isEqualTo(5);
    }

    // QA DATA-01·DATA-02·INST-06: 지금 승인 상태인 수강 수와 지금 COMPLETED 인 수료 수를 그대로 싣는다.
    @Test
    void 기관_승인_수와_수료_수를_전달한다() {
        when(mapper.selectInstitutionCounts(INSTITUTION_ID)).thenReturn(new InstitutionCountsRow(
            10L, 8L, 1L, 1L, 5L, 42L, 2L, 1L, 2L, 30L, 12L));
        when(mapper.selectInstitutionCategoryCounts(any())).thenReturn(List.of());

        InstitutionDashboard dashboard = repository.institutionDashboard(INSTITUTION_ID);

        assertThat(dashboard.approvedEnrollmentCount()).isEqualTo(30);
        assertThat(dashboard.completionCount()).isEqualTo(12);
    }

    @Test
    void 기관_집계가_없으면_승인_수와_수료_수는_0이다() {
        when(mapper.selectInstitutionCounts(INSTITUTION_ID)).thenReturn(null);
        when(mapper.selectInstitutionCategoryCounts(any())).thenReturn(List.of());

        InstitutionDashboard dashboard = repository.institutionDashboard(INSTITUTION_ID);

        assertThat(dashboard.approvedEnrollmentCount()).isZero();
        assertThat(dashboard.completionCount()).isZero();
    }

    // QA ADM-05: 관리자 대시보드 KPI 에 누적 수료 수와 유효 배지 수를 싣는다.
    @Test
    void 관리자_KPI_에_수료_수와_배지_수를_싣는다() {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        when(mapper.selectPlatformCounts(now)).thenReturn(new PlatformCountsRow(
            100L, 10L, 5L, 50L, 20L, 15L, 15L, 200L,
            80L, 5L, 5L, 2L, 1L,
            7L, 2L, 1L, 3L, 4L, 1L, 60L, 25L));
        when(mapper.selectRecentMembers(5)).thenReturn(List.of());

        PlatformDashboard.Kpis kpis = repository.platformDashboard(5, now).kpis();

        assertThat(kpis.completionCount()).isEqualTo(60);
        assertThat(kpis.badgeCount()).isEqualTo(25);
    }

    // 스펙 #43·#39·최근가입: 새로 추가한 세 블록이 조립 결과에 그대로 실리는지 본다.
    @Test
    void 사이트_전체_강사_기관_최근가입_통계를_조립한다() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        PlatformCountsRow counts = new PlatformCountsRow(
            100L, 10L, 5L, 50L, 20L, 15L, 15L, 200L,
            80L, 5L, 5L, 2L, 1L,
            7L, 2L, 1L, 3L, 4L, 1L, 60L, 25L);
        when(mapper.selectPlatformCounts(now)).thenReturn(counts);
        when(mapper.selectRecentMembers(5)).thenReturn(List.of());

        PlatformDashboard dashboard = repository.platformDashboard(5, now);

        assertThat(dashboard.instructorSummary())
            .isEqualTo(new PlatformDashboard.InstructorSummary(10, 7, 2, 1));
        assertThat(dashboard.institutionSummary())
            .isEqualTo(new PlatformDashboard.InstitutionSummary(5, 4, 1));
        assertThat(dashboard.memberSummary().recentSignupCount()).isEqualTo(3);
    }
}
