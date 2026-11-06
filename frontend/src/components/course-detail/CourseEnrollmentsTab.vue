<template>
  <section class="enrollments">
    <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

    <div class="enrollments__head">
      <h2 class="enrollments__title">수강 현황</h2>
      <!-- 이수 평가·수료증 발급은 이수 처리 화면이 맡는다 — 이 강의가 골라진 채로 연다.
           강사는 평가·확정만, 기관은 발급만 하므로 콘솔마다 이름을 맞춘다 -->
      <router-link :to="completionLink" class="enrollments__link">{{ completionLinkLabel }} →</router-link>
    </div>

    <div v-if="loading" class="enrollments__state"><q-spinner color="primary" size="24px" /></div>
    <AppEmptyState v-else-if="!pagedRows.length" compact description="승인된 수강생이 없습니다." />

    <!-- 칸 이름이 없어 숫자·막대가 무엇인지 알 수 없었다 — 행과 같은 폭으로 머리 행을 둔다.
         좁은 화면에선 행이 줄바꿈되며 흩어졌다 — 최소 폭을 두고 목록만 가로 스크롤한다 -->
    <div v-if="!loading && pagedRows.length" class="enroll-list">
      <div class="enroll-row enroll-row--head" aria-hidden="true">
        <div class="col-no">번호</div>
        <div class="col-student">수강생</div>
        <div class="col-status">이수</div>
        <div v-if="showOnline" class="col-progress">온라인 학습</div>
        <div class="col-progress">출석 현황</div>
        <div class="col-recent">최근 출석일</div>
        <div class="col-actions">관리</div>
      </div>

      <div v-for="(row, i) in pagedRows" :key="row.id" class="enroll-row">
        <div class="col-no">{{ (page - 1) * pageSize + i + 1 }}</div>

        <div class="col-student">
          <div class="cell-strong">{{ row.userName || '-' }}</div>
          <div class="cell-sub">
            {{ formatDateKst(row.birthDate, '-') }} · {{ row.phone || '-' }}
          </div>
        </div>

        <!-- 예전엔 늘 '수강중' 한 가지라 칸이 말해 주는 게 없었다 — 이수 상태를 보인다 -->
        <div class="col-status">
          <StatusBadge :status="completionKey(row)" :label="completionLabel(row)" size="sm" />
        </div>

        <!--
          TODO(backend): 이 칸(학습 현황)은 "온라인 n강 중 n강 이수" 축인데 그 데이터가 없다.
          ⚠ progressPercent 를 여기 쓰면 안 된다 — 실측으로 AttendanceSummary.attendanceRate 와
          값이 완전히 같다(출석 회차 / 전체 회차에서 파생). 옆 출석 현황 칸과 같은 숫자를
          "온라인" 라벨 아래 한 번 더 그리는 셈이 된다.
          온라인 축 자체가 백엔드에 없다: 회차에 온·오프라인 구분 필드가 없고(location 문자열과
          강좌 단위 educationType 뿐), 온라인 학습·영상 진도를 담는 테이블도 없다.
          필드 추가가 아니라 모델링이 필요한 건이라 하이픈을 유지한다.
        -->
        <!-- 온라인 강의는 기능 플래그로 꺼져 있다(오프라인만) — 늘 "- / -강"인 칸을 보이지 않는다 -->
        <div v-if="showOnline" class="col-progress">
          <div class="cell-sub">온라인: - / -강</div>
          <div class="cell-metric">-</div>
          <q-linear-progress :value="0" size="6px" rounded color="primary" />
        </div>

        <div class="col-progress">
          <div class="cell-sub">오프라인: {{ attendanceText(row) }}</div>
          <div class="cell-metric">{{ attendanceRate(row) }}%</div>
          <q-linear-progress :value="attendanceRate(row) / 100" size="6px" rounded color="primary" />
        </div>

        <!-- 최근 학습일: lastStudiedAt = 마지막 PRESENT·LATE 출석 시각. 이 칼럼은 규격에
             온·오프라인 라벨이 없어 출결 기준으로 두는 게 맞다(출석 없으면 null → 하이픈). -->
        <div class="col-recent">{{ formatDateKst(row.lastStudiedAt, '-') }}</div>

        <!-- 두 가지뿐인 동작을 ⋮ 안에 숨겼고, 이수 확정은 평가 전·미이수에도 눌려 서버가 거절했다.
             출결은 늘, 이수 확정은 이수 가능(ELIGIBLE)일 때만 줄에 바로 둔다 -->
        <div class="col-actions">
          <q-btn no-caps flat dense color="primary" label="출결" @click="goAttendance(row)" />
          <q-btn
            v-if="row.completion?.status === 'ELIGIBLE'"
            no-caps
            flat
            dense
            color="primary"
            label="이수 확정"
            class="action-strong"
            @click="confirm(row)"
          />
        </div>
      </div>
    </div>

    <ConsolePagination v-model="page" :total="rows.length" :page-size="pageSize" />
  </section>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsolePagination from '@/components/ConsolePagination.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { getAttendanceSummary } from '@/services/attendance-api';
import { confirmCompletion } from '@/services/completion-api';
import { listCourseEnrollments } from '@/services/enrollment-api';
import { FEATURES } from '@/config/features';
import { formatDateKst } from '@/utils/datetime';
import { completionKey, completionLabel, completionOrNull } from '@/utils/completion-status';
import { confirmDialog } from '@/utils/confirm-dialog';

const CONSOLE_PATHS = {
  admin: '/admin',
  institution: '/institution',
  instructor: '/instructor',
};

export default {
  name: 'CourseEnrollmentsTab',
  components: { AppEmptyState, AppErrorBanner, ConsolePagination, StatusBadge },
  props: {
    course: { type: Object, default: null },
    console: { type: String, default: 'admin' },
  },
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      page: 1,
      pageSize: 10,
    };
  },
  computed: {
    showOnline() {
      return FEATURES.enableOnlineCourses;
    },
    courseId() {
      return this.course?.id || this.$route.params.courseId;
    },
    completionLinkLabel() {
      if (this.console === 'instructor') return '출석 평가·이수 확정';
      if (this.console === 'institution') return '수료증 발급';
      return '이수 평가·수료증 발급';
    },
    completionLink() {
      const base = CONSOLE_PATHS[this.console] || CONSOLE_PATHS.instructor;
      return { path: `${base}/completion`, query: { courseId: this.courseId } };
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.rows.slice(start, start + this.pageSize);
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    // AttendanceSummary: totalSessions·presentCount·attendanceRate·completedMinutes
    // ⚠ completedMinutes 는 이수 평가 요청에 그대로 넣어야 하는 원본값이다 — row.attendance 에 보존한다
    attendanceText(row) {
      const s = row.attendance;
      return s ? `${s.presentCount ?? 0} / ${s.totalSessions ?? 0} 회` : '- / - 회';
    },
    completionKey,
    completionLabel,
    attendanceRate(row) {
      const s = row.attendance;
      if (!s) return 0;
      return Math.round(s.attendanceRate ?? 0);
    },
    async load() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseEnrollments(this.courseId, { page: 0, size: 100 });
        const approved = (Array.isArray(result.data) ? result.data : []).filter(
          (row) => row.status === 'APPROVED',
        );
        // 출결 요약·이수 기록은 수강생마다 1건씩 — 실패해도 행은 보여 준다
        this.rows = await Promise.all(
          approved.map(async (row) => {
            const [attendance, completion] = await Promise.all([
              getAttendanceSummary(row.id).catch(() => null),
              completionOrNull(row.id),
            ]);
            return { ...row, attendance, completion };
          }),
        );
        this.page = 1;
      } catch (error) {
        this.errorMessage = error.message || '수강 현황을 불러오지 못했습니다.';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    goAttendance(row) {
      // 콘솔마다 출결 화면 주소가 다르다 — 기관 관리자는 /instructor 경로 권한이 없다
      const ATTENDANCE_PATHS = {
        admin: '/admin/attendance',
        institution: '/institution/attendance',
        instructor: '/instructor/attendance',
      };
      const base = ATTENDANCE_PATHS[this.console] || ATTENDANCE_PATHS.instructor;
      this.$router.push({ path: base, query: { courseId: this.courseId, userId: row.userId } });
    },
    // 다른 확인 창과 같은 confirmDialog — 확정은 배지 발급으로 이어지고 강사는 되돌릴 수 없다고 먼저 알린다
    async confirm(row) {
      const ok = await confirmDialog(this.$q, {
        title: '이수 확정',
        message: `${row.userName || '이 수강생'}의 이수를 확정합니다. 확정하면 조건에 맞는 배지가 발급되고, 되돌리려면 관리자의 확정 취소가 필요합니다.`,
        ok: '이수 확정',
        color: 'primary',
        cancel: '닫기',
      });
      if (ok) this.runConfirm(row);
    },
    async runConfirm(row) {
      try {
        await confirmCompletion(row.id);
        this.$q.notify({ type: 'positive', message: '이수 확정했습니다.', position: 'top' });
        await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
  },
};
</script>

<style scoped>
.enrollments {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.enrollments__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

/* 77 = 진도 컬럼 51(18 + 27 + 6) + padding 26 */
.enroll-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-x: auto;
}

.enroll-row {
  display: flex;
  align-items: center;
  /* 노트북 콘솔 본문(약 830px)에 스크롤 없이 들어가게 칸·간격을 줄였다 — 그보다 좁을 때만 가로 스크롤 */
  gap: 14px;
  min-width: 760px;
  min-height: 77px;
  padding: 13px 24px;
  border: 1px solid var(--db-border);
  border-radius: 4px;
}

.enroll-row--head {
  min-height: 0;
  padding-top: 10px;
  padding-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
  background: var(--db-bg-table-head);
}

.col-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  width: 140px;
  flex-shrink: 0;
}

.action-strong {
  font-weight: 700;
}

.enrollments__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.enrollments__link {
  font-size: 14px;
  color: var(--db-accent);
  text-decoration: none;
}

.enrollments__link:hover,
.enrollments__link:focus-visible {
  text-decoration: underline;
}

.col-no {
  width: 48px;
  flex-shrink: 0;
  text-align: center;
}

.col-student {
  flex: 1;
  min-width: 120px;
}

.col-status {
  width: 84px;
  flex-shrink: 0;
}

.col-progress {
  width: 170px;
  flex-shrink: 0;
}

.col-recent {
  width: 100px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.cell-strong {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.cell-sub {
  font-size: 12px;
  line-height: 18px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.cell-metric {
  font-size: 18px;
  line-height: 27px;
  font-weight: 700;
  color: var(--db-text-title);
}

.col-progress :deep(.q-linear-progress) {
  border-radius: 3px;
}

.enrollments__state {
  padding: 40px 0;
  text-align: center;
  color: var(--db-text-sub);
}

</style>
