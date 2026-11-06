<template>
  <q-page padding>
    <AppPageHeader title="수강 현황 관리" show-refresh :loading="loading" @refresh="loadRows" />
    <AppErrorBanner :message="errorMessage" />
    <!-- 이수 조회가 실패한 칸을 '-'로 두면 '평가 전'과 헷갈렸다 — 칸은 '확인 불가', 여기서 몇 명인지 알린다 -->
    <AppErrorBanner :message="completionFailure">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadRows" />
      </template>
    </AppErrorBanner>

    <!-- API 가 courseId 를 받는데 거를 수단이 없어 강의 여러 개가 한 표에 섞였다 -->
    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-5">
        <q-select
          :model-value="courseFilter"
          outlined
          dense
          emit-value
          map-options
          :options="courseOptions"
          label="강의"
          @update:model-value="setCourse"
        />
      </div>
    </div>
    <p v-if="capped" class="capped-note">
      최근 수강생 {{ PAGE_SIZE }}명만 보여 줍니다. 강의를 고르면 강의 상세의 '수강 현황'에서 전체를
      볼 수 있습니다.
    </p>

    <q-table
      flat
      bordered
      :rows="filteredRows"
      :columns="columns"
      row-key="enrollmentId"
      :loading="loading"
      :grid="$q.screen.xs"
      :pagination="{ rowsPerPage: 10 }"
      no-data-label="승인된 수강생이 없습니다."
      class="status-table"
      @row-click="onRowClick"
    >
      <template #body-cell-courseTitle="props">
        <q-td :props="props">
          <router-link :to="courseLink(props.row)" class="course-link">{{ props.value || '-' }}</router-link>
        </q-td>
      </template>
      <!-- 출석률은 attendanceRate(학습자 출결 요약과 같은 계산: 지각=출석, 공결은 분모 제외).
           진도율(progressPercent = 출석 회차 / 전체 회차)을 '출석'으로 보여 학습자 화면과 숫자가 달랐다(QA ATT) -->
      <template #body-cell-attendance="props">
        <q-td :props="props">
          <div class="attendance-cell">
            <q-linear-progress rounded size="8px" :value="(props.row.attendanceRate || 0) / 100" color="primary" />
            <span class="attendance-cell__text">
              출석률 {{ formatAttendanceRate(props.row.attendanceRate) }} · {{ props.row.attendedCount }}/{{ props.row.sessionCount }}회
            </span>
          </div>
        </q-td>
      </template>
      <!-- 신청 상태(늘 '승인')가 아니라 이수 상태 — 강의 상세 수강 현황 탭과 같은 기준 -->
      <template #body-cell-completion="props">
        <q-td :props="props">
          <StatusBadge :status="completionKey(props.row)" :label="completionLabel(props.row)" size="sm" />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense no-caps color="primary" label="출결" :to="attendanceLink(props.row)" />
        </q-td>
      </template>
      <!-- 폰(xs): 5칸 표가 옆으로 밀렸다 — 한 수강생을 카드 한 장으로 -->
      <template #item="props">
        <div class="col-12">
          <!-- 카드 어디를 눌러도 그 수강생 출결로 — 강의명 링크·[출결 보기]는 제 갈 곳으로 -->
          <div class="status-card" @click="onRowClick($event, props.row)">
            <div class="status-card__head">
              <span class="status-card__name">{{ props.row.userName || '-' }}</span>
              <StatusBadge
                :status="completionKey(props.row)"
                :label="completionLabel(props.row)"
                size="sm"
              />
            </div>
            <router-link :to="courseLink(props.row)" class="course-link status-card__course">
              {{ props.row.courseTitle || '-' }}
            </router-link>
            <div class="attendance-cell">
              <q-linear-progress
                rounded
                size="8px"
                :value="(props.row.attendanceRate || 0) / 100"
                color="primary"
              />
              <!-- [출결 보기]가 혼자 한 줄을 차지했다 — 출석 줄 오른쪽에 -->
              <div class="status-card__foot">
                <span class="attendance-cell__text">
                  출석률 {{ formatAttendanceRate(props.row.attendanceRate) }} ·
                  {{ props.row.attendedCount }}/{{ props.row.sessionCount }}회
                </span>
                <q-btn flat dense no-caps color="primary" label="출결 보기" :to="attendanceLink(props.row)" />
              </div>
            </div>
          </div>
        </div>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listInstructorEnrollmentStatus } from '@/services/instructor-api';
import { formatAttendanceRate } from '@/utils/attendance-rate';
import {
  completionFailureText,
  completionKey,
  completionLabel,
  completionOrNull,
} from '@/utils/completion-status';

const PAGE_SIZE = 100;

export default {
  name: 'InstructorEnrollmentStatusPage',
  components: { AppErrorBanner, AppPageHeader, StatusBadge },
  data() {
    return {
      PAGE_SIZE,
      rows: [],
      capped: false,
      loading: false,
      errorMessage: '',
      courseFilter: this.$route.query.courseId || null,
      columns: [
        { name: 'userName', label: '수강생', field: 'userName', align: 'left' },
        { name: 'courseTitle', label: '강의명', field: 'courseTitle', align: 'left' },
        { name: 'attendance', label: '출석률', field: 'attendanceRate', align: 'left', sortable: true },
        { name: 'completion', label: '이수', field: 'completion', align: 'center' },
        { name: 'actions', label: '바로가기', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    completionFailure() {
      return completionFailureText(this.filteredRows);
    },
    courseOptions() {
      const seen = new Map();
      this.rows.forEach((row) => {
        if (row.courseId && !seen.has(row.courseId)) seen.set(row.courseId, row.courseTitle || '-');
      });
      return [
        { label: '전체 강의', value: null },
        ...[...seen].map(([value, label]) => ({ value, label })),
      ];
    },
    filteredRows() {
      // 수강 현황은 승인된 수강생만 — 신청·거절 건은 수강 신청 관리에서 본다
      return this.rows.filter(
        (row) => row.status === 'APPROVED' && (!this.courseFilter || row.courseId === this.courseFilter),
      );
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    formatAttendanceRate,
    setCourse(value) {
      this.courseFilter = value || null;
      // 고른 강의를 주소에 남겨 새로고침·뒤로가기에도 유지
      this.$router.replace({ query: { ...this.$route.query, courseId: this.courseFilter || undefined } });
    },
    courseLink(row) {
      return { name: 'instructor-course-detail', params: { courseId: row.courseId }, query: { tab: 'enrollments' } };
    },
    /** 행·카드를 누르면 그 수강생 출결 — 링크·버튼을 누른 건 그쪽에 맡긴다 */
    onRowClick(event, row) {
      if (event?.target?.closest?.('a, button, [role="button"]')) return;
      this.$router.push(this.attendanceLink(row));
    },
    attendanceLink(row) {
      return { path: '/instructor/attendance', query: { courseId: row.courseId, userId: row.userId } };
    },
    completionKey,
    completionLabel,
    async loadRows() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listInstructorEnrollmentStatus({ page: 0, size: PAGE_SIZE });
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
        // 계약에 정렬 파라미터가 없다 — 강좌명 순은 여기서 맞춘다
        const sorted = [...(page.data || [])].sort((a, b) =>
          String(a.courseTitle || '').localeCompare(String(b.courseTitle || ''), 'ko'),
        );
        this.rows = await Promise.all(
          sorted.map(async (row) => ({
            ...row,
            completion: row.status === 'APPROVED' ? await completionOrNull(row.enrollmentId) : null,
          })),
        );
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강 현황을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 행을 누르면 그 수강생 출결로 — 가리키면 바탕이 옅게 바뀐다(터치 기기는 hover 가 남아 뺀다) */
.status-table :deep(tbody tr),
.status-card {
  cursor: pointer;
}

@media (hover: hover) {
  .status-table :deep(tbody tr) {
    transition: background-color 0.15s ease;
  }

  .status-table :deep(tbody tr:hover) {
    background: var(--db-bg-subtle);
  }
}

@media (prefers-reduced-motion: reduce) {
  .status-table :deep(tbody tr) {
    transition: none;
  }
}

.course-link {
  color: var(--db-text-title);
  text-decoration: none;
}

.course-link:hover,
.course-link:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.capped-note {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.status-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--db-border);
}

.status-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.status-card__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.status-card__course {
  font-size: 13px;
  overflow-wrap: anywhere;
}

.status-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.attendance-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
}

.attendance-cell__text {
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}
</style>
