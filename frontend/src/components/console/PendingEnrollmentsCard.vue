<template>
  <!-- 강사 대시보드 '수강 신청' — 처리할 신청이 메뉴 안쪽 화면에만 있어 몇 건 쌓였는지 몰랐다.
       대기 건수·강의별 건수·오래 기다린 신청 몇 건을 보이고, 여기서 바로 승인·거절(사유)한다 -->
  <section class="pending-card" aria-labelledby="pending-card-title">
    <header class="pending-card__head">
      <h2 id="pending-card-title" class="pending-card__title">수강 신청</h2>
      <span v-if="!loading && !failed" class="pending-card__count" :class="{ 'pending-card__count--zero': !pending.length }">
        처리 대기 <strong>{{ pending.length }}</strong>건
      </span>
      <q-space />
      <router-link :to="listPath" class="pending-card__all">전체 보기 ›</router-link>
    </header>

    <div v-if="loading" class="pending-card__state">
      <q-spinner size="20px" color="primary" />
      수강 신청을 확인하는 중입니다
    </div>
    <div v-else-if="failed" class="pending-card__state">
      수강 신청을 불러오지 못했습니다.
      <q-btn flat dense no-caps color="primary" label="다시 시도" @click="loadRows" />
    </div>
    <div v-else-if="!pending.length" class="pending-card__state">처리할 수강 신청이 없습니다.</div>

    <template v-else>
      <!-- 강의별 대기 — 누르면 그 강의만 걸러 수강 신청 관리로 -->
      <div class="pending-card__courses">
        <router-link
          v-for="course in byCourse"
          :key="course.courseId"
          :to="{ path: listPath, query: { courseId: course.courseId } }"
          class="pending-card__course"
        >
          <span class="pending-card__course-title">{{ course.title }}</span>
          <strong>{{ course.count }}</strong>
        </router-link>
      </div>

      <ul class="pending-card__list">
        <li v-for="row in preview" :key="row.id" class="pending-row">
          <div class="pending-row__info">
            <div class="pending-row__name">
              {{ row.applicantLabel }}
              <span v-if="row.status === 'WAITLISTED'" class="pending-row__wait">대기</span>
            </div>
            <div class="pending-row__meta">{{ row.courseTitle }} · 신청 {{ row.appliedAtText }}</div>
          </div>
          <div v-if="row.canDecide" class="pending-row__actions">
            <q-btn
              unelevated
              no-caps
              color="primary"
              label="승인"
              class="pending-row__btn"
              :loading="busyId === row.id"
              :disable="!!busyId"
              @click="approve(row)"
            />
            <q-btn
              outline
              no-caps
              color="negative"
              label="거절"
              class="pending-row__btn"
              :disable="!!busyId"
              @click="openReject(row)"
            />
          </div>
          <span v-else class="pending-row__note">주강사만 처리할 수 있어요</span>
        </li>
      </ul>
      <p v-if="pending.length > preview.length" class="pending-card__more">
        오래 기다린 {{ preview.length }}건만 보입니다 —
        <router-link :to="listPath">나머지 {{ pending.length - preview.length }}건 보기</router-link>
      </p>
    </template>

    <!-- 거절 사유 — 수강 신청 관리와 같은 창(실패하면 사유를 들고 남는다) -->
    <CorrectionReasonDialog
      v-model="rejectOpen"
      title="수강신청 거절"
      :target="rejectTargetLabel"
      :notes="REJECT_NOTES"
      confirm-label="거절"
      reason-label="거절 사유"
      reason-hint="수강신청 기록에 남습니다."
      :action="runReject"
      @done="onRejected"
    />
  </section>
</template>

<script>
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import enrollmentDecisionPage from '@/mixins/enrollment-decision-page';
import { listCourseEnrollments } from '@/services/enrollment-api';
import { formatDateTimeFields } from '@/utils/datetime';
import { DECIDABLE_STATUSES } from '@/utils/enrollment-decision';
import { formatUserDisplayName } from '@/utils/user-display';

// 신청을 받는 강의 상태 — 준비(DRAFT)·종료·취소 강의는 부르지 않는다
const ENROLLING_STATUSES = ['RECRUITING', 'RECRUITMENT_CLOSED', 'IN_PROGRESS'];
const COURSE_LIMIT = 20;
const CONCURRENCY = 4;
const ENROLLMENT_PAGE_SIZE = 100;
const PREVIEW_LIMIT = 5;
// 대시보드를 켜 둔 채 새 신청이 오면 보이게 — 1분마다, 탭으로 돌아올 때도 다시 읽는다
const REFRESH_MS = 60 * 1000;

/** items 를 limit 개씩 차례로 — 한꺼번에 수십 건을 쏘지 않게 */
async function mapInBatches(items, limit, fn) {
  const results = [];
  for (let index = 0; index < items.length; index += limit) {
    results.push(...(await Promise.all(items.slice(index, index + limit).map(fn))));
  }
  return results;
}

const appliedTime = (row) => new Date(row.appliedAt || row.createdAt || 0).getTime();

export default {
  name: 'PendingEnrollmentsCard',
  components: { CorrectionReasonDialog },
  mixins: [enrollmentDecisionPage],
  props: {
    /** 대시보드가 받아 둔 강의 목록 */
    courses: { type: Array, default: () => [] },
    /** 관리자 열람 — 모든 강의를 처리할 수 있다(강사는 주강사인 강의만) */
    isAdmin: { type: Boolean, default: false },
    listPath: { type: String, default: '/instructor/enrollment-apps' },
  },
  /** count(n | null): 처리 대기 건수 — 대시보드 '할 일' 숫자. 못 셌으면 null */
  emits: ['count'],
  data() {
    return { rows: [], loading: false, failed: false, refreshTimer: null };
  },
  computed: {
    // 오래 기다린 순 — 먼저 신청한 사람부터 처리하게
    pending() {
      return this.rows
        .filter((row) => DECIDABLE_STATUSES.includes(row.status))
        .sort((a, b) => appliedTime(a) - appliedTime(b));
    },
    preview() {
      return this.pending.slice(0, PREVIEW_LIMIT);
    },
    byCourse() {
      const counts = new Map();
      this.pending.forEach((row) => {
        const entry = counts.get(row.courseId) || { courseId: row.courseId, title: row.courseTitle, count: 0 };
        counts.set(row.courseId, { ...entry, count: entry.count + 1 });
      });
      return [...counts.values()].sort((a, b) => b.count - a.count);
    },
  },
  watch: {
    courses: {
      immediate: true,
      handler() {
        this.loadRows();
      },
    },
    pending() {
      this.emitCount();
    },
    failed() {
      this.emitCount();
    },
  },
  mounted() {
    this.refreshTimer = window.setInterval(() => this.refreshQuietly(), REFRESH_MS);
    document.addEventListener('visibilitychange', this.onVisibility);
  },
  beforeUnmount() {
    window.clearInterval(this.refreshTimer);
    document.removeEventListener('visibilitychange', this.onVisibility);
  },
  methods: {
    emitCount() {
      this.$emit('count', this.failed ? null : this.pending.length);
    },
    // 승인·거절 창이 열려 있거나 처리 중이면 건너뛴다 — 보던 목록이 바뀌지 않게
    refreshQuietly() {
      if (document.hidden || this.loading || this.busyId || this.rejectOpen) return;
      this.loadRows();
    },
    onVisibility() {
      if (!document.hidden) this.refreshQuietly();
    },
    toRow(row, course) {
      return {
        ...row,
        courseId: course.id,
        courseTitle: course.title || '이름 없는 강의',
        canDecide: this.isAdmin || course.instructorRole === 'MAIN',
        applicantLabel: formatUserDisplayName(row),
        appliedAtText: formatDateTimeFields(row, 'appliedAt', 'createdAt'),
      };
    },
    /** enrollment-decision-page 가 승인·거절 뒤 부른다 */
    async loadRows() {
      const targets = this.courses.filter((course) => ENROLLING_STATUSES.includes(course.status)).slice(0, COURSE_LIMIT);
      if (!targets.length) {
        this.rows = [];
        return;
      }
      this.loading = !this.rows.length;
      this.failed = false;
      try {
        const perCourse = await mapInBatches(targets, CONCURRENCY, async (course) => {
          const page = await listCourseEnrollments(course.id, { page: 0, size: ENROLLMENT_PAGE_SIZE });
          return (page.data || []).map((row) => this.toRow(row, course));
        });
        this.rows = perCourse.flat();
      } catch (error) {
        console.warn('[PendingEnrollmentsCard] 수강 신청을 불러오지 못했습니다', error);
        this.failed = true;
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.pending-card {
  padding: 20px 24px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.pending-card__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.pending-card__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--db-text-title);
}

.pending-card__count {
  font-size: 15px;
  color: var(--db-text-body);
}

.pending-card__count strong {
  font-size: 18px;
  color: var(--db-status-pink);
}

.pending-card__count--zero strong {
  color: var(--db-text-sub);
}

.pending-card__all {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
  white-space: nowrap;
}

.pending-card__all:hover {
  text-decoration: underline;
}

.pending-card__state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  font-size: 15px;
  color: var(--db-text-body);
}

.pending-card__courses {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.pending-card__course {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  font-size: 13px;
  color: var(--db-text-body);
  text-decoration: none;
}

.pending-card__course:hover {
  border-color: var(--db-accent);
}

.pending-card__course-title {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-card__course strong {
  color: var(--db-status-pink);
}

.pending-card__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.pending-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  padding: 10px 0;
  border-top: 1px solid var(--db-border-light);
}

.pending-row__info {
  min-width: 0;
  flex: 1 1 240px;
}

.pending-row__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pending-row__wait {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--db-status-orange-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--db-status-orange-text);
}

.pending-row__meta {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--db-text-sub);
}

.pending-row__actions {
  display: flex;
  gap: 6px;
}

.pending-row__btn {
  min-width: 64px;
  min-height: 40px;
  font-size: 14px;
}

.pending-row__note {
  font-size: 13px;
  color: var(--db-text-sub);
}

.pending-card__more {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.pending-card__more a {
  color: var(--db-accent);
}

@media (max-width: 599px) {
  .pending-card {
    padding: 16px;
  }

  .pending-row__actions {
    width: 100%;
  }

  .pending-row__btn {
    flex: 1;
    min-height: 44px;
  }
}
</style>
