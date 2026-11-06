<template>
  <q-page padding>
    <!-- 오늘 날짜 — '오늘 수업'의 기준일이 분명하게 -->
    <AppPageHeader
      title="대시보드"
      :subtitle="todayLabel"
      show-refresh
      :loading="loading"
      @refresh="loadCourses"
    />

    <q-banner v-if="consoleAsAdmin" dense class="bg-blue-1 q-mb-md">
      관리자 계정이라 모든 기관의 강의를 보여 줍니다. 강사 계정은 배정된 강의만 봅니다.
    </q-banner>

    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadCourses" />
      </template>
    </AppErrorBanner>

    <!-- 할 일 숫자 — 전체·모집중·진행중 대신 오늘 처리할 것(hallmark) -->
    <DashboardTodoStats class="q-mb-md" :items="todoStats" />

    <!-- 매일 할 일 두 장 — 오늘 수업(출석)·수강 신청(승인). 넓은 화면은 나란히, 신청이 0건이면 오늘 수업만 -->
    <div class="dashboard__today q-mb-md">
      <TodaySessionsCard
        :items="todayItems"
        :loading="loading || todayLoading"
        :failed="todayCount === null && !todayLoading && !loading"
        :capped="todayCapped"
        :next-item="nextItem"
        @retry="retryToday"
        @session-updated="onSessionUpdated"
      />
      <PendingEnrollmentsCard
        v-if="!loading || rows.length"
        v-show="pendingCount !== 0"
        :courses="rows"
        :is-admin="consoleAsAdmin"
        @count="pendingCount = $event"
      />
    </div>

    <!-- 내 강의 — 상태별 숫자는 여기 한 줄로(예전엔 큰 카드 세 장이 화면 한 줄을 썼다) -->
    <div class="dashboard__courses-head">
      <h2 class="dashboard__courses-title">내 강의</h2>
      <span class="dashboard__courses-summary">{{ courseSummary }}</span>
      <q-space />
      <router-link to="/instructor/courses" class="dashboard__courses-all">내 강의 관리 ›</router-link>
    </div>

    <q-table
      flat
      bordered
      row-key="id"
      :rows="rows"
      :columns="columns"
      :loading="loading"
      :pagination="{ rowsPerPage: 10 }"
      :grid="$q.screen.xs"
      class="dashboard__course-table"
      @row-click="onCourseRowClick"
      no-data-label="배정된 강의가 없습니다. 기관 관리자가 강의에 강사로 배정하면 여기에 나타납니다."
    >
      <!-- 강의명 → 강의 상세 — 내 강의 관리와 같은 길 -->
      <template #body-cell-title="props">
        <q-td :props="props">
          <router-link :to="detailLink(props.row)" class="course-title-link">{{ props.value || '-' }}</router-link>
          <!-- 코드는 강의명 아래 — 기관 강의 관리와 같은 모양(코드가 첫 칸이라 강의명이 뒤로 밀렸다) -->
          <div v-if="props.row.courseCode" class="course-code">{{ props.row.courseCode }}</div>
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" />
        </q-td>
      </template>
      <template v-if="showEducationTypeUI" #body-cell-educationType="props">
        <q-td :props="props">
          <EducationTypeBadge :type="props.row.educationType" />
        </q-td>
      </template>
      <template #body-cell-students="props">
        <q-td :props="props" class="num">{{ studentsText(props.row) }}</q-td>
      </template>
      <template #body-cell-nextSession="props">
        <q-td :props="props">
          <span v-if="nextByCourse[props.row.id]" class="num">{{ nextSessionText(props.row.id) }}</span>
          <span v-else class="dashboard__muted">-</span>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <!-- 작은 글자 버튼이 표 오른쪽 끝에 있었다 — 테두리 단추로 -->
          <!-- 임시저장 강좌는 출결·이수가 없다 — [이어서 작성]·[삭제](주강사·관리자) -->
          <div v-if="props.row.status === 'DRAFT'" class="dashboard__actions">
            <q-btn outline no-caps color="primary" label="이어서 작성" :to="`/instructor/courses/${props.row.id}/edit`" />
            <q-btn
              v-if="canDeleteDraft(props.row)"
              outline
              no-caps
              color="negative"
              label="삭제"
              :loading="deletingId === props.row.id"
              @click="deleteDraft(props.row)"
            />
          </div>
          <div v-else class="dashboard__actions">
            <q-btn outline no-caps color="primary" label="출결" :to="attendanceCourseLink(props.row)" />
            <q-btn outline no-caps color="primary" label="이수" :to="completionLink(props.row)" />
          </div>
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <ConsoleCourseCard
            :course="props.row"
            :to="detailLink(props.row)"
            :meta-items="[props.row.courseCode, periodText(props.row)]"
          >
            <template v-if="props.row.status === 'DRAFT'">
              <q-btn
                flat
                dense
                no-caps
                color="primary"
                label="이어서 작성"
                :to="`/instructor/courses/${props.row.id}/edit`"
              />
              <q-btn
                v-if="canDeleteDraft(props.row)"
                flat
                dense
                no-caps
                color="negative"
                label="삭제"
                :loading="deletingId === props.row.id"
                @click.stop.prevent="deleteDraft(props.row)"
              />
            </template>
            <template v-else>
              <q-btn
                flat
                dense
                no-caps
                color="primary"
                label="출결"
                :to="attendanceCourseLink(props.row)"
              />
              <q-btn
                flat
                dense
                no-caps
                color="primary"
                label="이수"
                :to="completionLink(props.row)"
              />
            </template>
          </ConsoleCourseCard>
        </div>
      </template>
    </q-table>
    <div v-if="listCapped" class="dashboard__capped q-mt-xs">최근 {{ COURSE_PAGE_SIZE }}개 강의만 표시합니다.</div>
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import { formatDateKst, formatDatePeriodKst } from '@/utils/datetime';
import { listCourseSessions } from '@/services/session-api';
import { getInstructorCourseStats } from '@/services/instructor-api';
import { listInstructorInquiries } from '@/services/inquiry-api';
import { formatSessionWhen } from '@/utils/session-attendance';
import { confirmDeleteDraftCourse } from '@/utils/draft-course-delete';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleCourseCard from '@/components/console/ConsoleCourseCard.vue';
import DashboardTodoStats from '@/components/console/DashboardTodoStats.vue';
import PendingEnrollmentsCard from '@/components/console/PendingEnrollmentsCard.vue';
import TodaySessionsCard from '@/components/console/TodaySessionsCard.vue';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';
import { filterEducationTypeColumn } from '@/utils/course-mapper';
import educationTypeUi from '@/mixins/education-type-ui';

// 회차가 열릴 수 있는 과정 상태 — 준비(DRAFT)·종료·취소 과정은 오늘 수업이 없으니 부르지 않는다
const SESSION_COURSE_STATUSES = ['RECRUITING', 'RECRUITMENT_CLOSED', 'IN_PROGRESS'];
// 과정마다 회차 목록을 한 번씩 부르므로 개수·동시 요청 수를 묶어 둔다
const TODAY_COURSE_LIMIT = 20;
const TODAY_CONCURRENCY = 4;
const SESSION_PAGE_SIZE = 100;
const TODAY_LABEL = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'long',
  day: 'numeric',
  weekday: 'short',
});
// 표는 한 번에 이만큼만 받는다 — 꽉 차면 더 있을 수 있어 표 아래에 알린다
const COURSE_PAGE_SIZE = 50;

/** items 를 limit 개씩 나눠 차례로 부른다 — 한꺼번에 수십 건을 쏘지 않게 */
async function mapInBatches(items, limit, fn) {
  const results = [];
  for (let index = 0; index < items.length; index += limit) {
    const batch = await Promise.all(items.slice(index, index + limit).map(fn));
    results.push(...batch);
  }
  return results;
}

/** 오늘(KST) 시작하는, 취소되지 않은 회차만 */
function isTodaySession(session, todayKst) {
  if (!session?.startsAt || session.status === 'CANCELLED') return false;
  return formatDateKst(session.startsAt, '') === todayKst;
}

/** 오늘 뒤에 시작하는 예정 회차 — 오늘 수업이 없을 때 '다음 수업'으로 */
function isUpcomingSession(session, todayKst) {
  if (!session?.startsAt || session.status !== 'SCHEDULED') return false;
  return formatDateKst(session.startsAt, '') > todayKst;
}

const byStartsAt = (a, b) => String(a.session.startsAt).localeCompare(String(b.session.startsAt));

/** 강의의 다음 회차 — 아직 끝나지 않은(취소·종료 아님, 끝 시각이 지나지 않은) 가장 이른 회차 */
function nextSessionOf(sessions, now = Date.now()) {
  return (
    sessions
      .filter((session) => session.startsAt && !['CANCELLED', 'COMPLETED'].includes(session.status))
      .filter((session) => new Date(session.endsAt || session.startsAt).getTime() >= now)
      .sort((a, b) => String(a.startsAt).localeCompare(String(b.startsAt)))[0] || null
  );
}

function buildColumns() {
  return filterEducationTypeColumn([
    { name: 'title', label: '강의명', field: 'title', align: 'left' },
    { name: 'educationType', label: '유형', field: 'educationType', align: 'center' },
    { name: 'status', label: '상태', field: 'status', align: 'center' },
    { name: 'students', label: '수강생', field: 'enrolledCount', align: 'center' },
    { name: 'nextSession', label: '다음 수업', field: 'id', align: 'left' },
    {
      name: 'period',
      label: '기간',
      field: (r) => formatDatePeriodKst(r.startDate, r.endDate),
      align: 'left',
    },
    { name: 'actions', label: '바로가기', field: 'actions', align: 'left' },
  ]);
}

export default {
  components: {
    AppPageHeader,
    AppErrorBanner,
    ConsoleCourseCard,
    DashboardTodoStats,
    EducationTypeBadge,
    PendingEnrollmentsCard,
    StatusBadge,
    TodaySessionsCard,
  },
  mixins: [instructorConsoleCourses, educationTypeUi],
  name: 'InstructorDashboardPage',
  data() {
    return {
      loading: false,
      errorMessage: '',
      rows: [],
      columns: buildColumns(),
      TODAY_COURSE_LIMIT,
      COURSE_PAGE_SIZE,
      // 통계 API(배정 기준) 결과 — 관리자이거나 실패하면 null 이라 목록으로 센다
      courseStats: null,
      todayLoading: false,
      // null = 아직 못 셈/실패 — 0건과 구분해 '불러오지 못함'으로 보인다
      todayCount: null,
      todayItems: [],
      todayCapped: false,
      nextItem: null,
      // 강의별 다음 회차 { courseId: session } — 오늘 회차를 셀 때 같이 고른다
      nextByCourse: {},
      // 지우는 중인 임시저장 강좌 id
      deletingId: '',
      // 처리 대기 수강 신청(카드가 센다) · 답변 대기 문의 — null 은 모름
      pendingCount: undefined,
      inquiryCount: null,
    };
  },
  computed: {
    listCapped() {
      return this.rows.length >= COURSE_PAGE_SIZE;
    },
    todayLabel() {
      return TODAY_LABEL.format(new Date());
    },
    // 수업 기간이 지났는데 아직 '종료'가 아닌 강의 — 이수 처리를 할 때일 수 있다(이수 상태는 수강생마다 따로라 강의로 센다)
    endedCourses() {
      const today = formatDateKst(new Date(), '');
      return this.rows.filter(
        (row) => SESSION_COURSE_STATUSES.includes(row.status) && row.endDate && formatDateKst(row.endDate, '') < today,
      );
    },
    todoStats() {
      const first = this.todayItems[0];
      const ended = this.endedCourses;
      return [
        {
          key: 'today',
          label: '오늘 수업',
          icon: 'o_event',
          value: this.todayCount,
          unit: '회차',
          to: first ? { path: '/instructor/attendance', query: { courseId: first.courseId, sessionId: first.session.id } } : null,
        },
        {
          key: 'enrollments',
          label: '승인 대기 신청',
          icon: 'o_how_to_reg',
          value: this.pendingCount === undefined ? null : this.pendingCount,
          unit: '건',
          to: '/instructor/enrollment-apps',
        },
        {
          key: 'inquiries',
          label: '답변 대기 문의',
          icon: 'o_forum',
          value: this.inquiryCount,
          unit: '건',
          to: '/instructor/inquiries',
        },
        {
          key: 'completion',
          label: '수업 끝난 강의',
          icon: 'o_task_alt',
          value: this.loading ? null : ended.length,
          unit: '개',
          hint: ended.length ? '이수 처리를 확인해 주세요' : '',
          to: { path: '/instructor/completion', query: ended[0] ? { courseId: ended[0].id } : {} },
        },
      ];
    },
    courseSummary() {
      const count = (status) => this.rows.filter((row) => row.status === status).length;
      const total = this.courseStats?.total ?? this.rows.length;
      return `전체 ${total} · 모집중 ${count('RECRUITING')} · 진행중 ${count('IN_PROGRESS')}`;
    },
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    async loadCourses() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const [result, stats] = await Promise.all([
          this.fetchConsoleCourses({ page: 0, size: COURSE_PAGE_SIZE }),
          // 통계 API 는 '나에게 배정된' 기준 — 관리자는 목록으로 센다
          this.consoleAsAdmin ? null : getInstructorCourseStats().catch(() => null),
        ]);
        this.courseStats = stats;
        this.rows = Array.isArray(result.data) ? result.data : [];
        this.loadInquiryCount();
      } catch (error) {
        this.rows = [];
        this.courseStats = null;
        this.todayCount = null;
        this.todayItems = [];
        this.errorMessage = error.message || '강의 목록 조회 실패';
      } finally {
        this.loading = false;
      }
      // 과정 표는 먼저 보이고, 오늘 회차는 뒤이어 센다
      if (!this.errorMessage) this.loadTodaySessions(this.rows);
    },
    /** 과정 목록부터 실패했으면 그것부터, 아니면 오늘 회차만 다시 센다 */
    retryToday() {
      if (this.errorMessage) {
        this.loadCourses();
        return;
      }
      this.loadTodaySessions(this.rows);
    },
    /** 수업 시작으로 바뀐 회차를 목록에 바꿔 끼운다(불변) — 버튼이 바로 'QR 띄우기'로 바뀐다 */
    onSessionUpdated(session) {
      this.todayItems = this.todayItems.map((item) =>
        item.session.id === session.id ? { ...item, session } : item,
      );
    },
    /**
     * GET /courses/{courseId}/sessions 에 날짜 필터가 없어 과정별로 받아 오늘 것만 고른다.
     * 한 과정이라도 실패하면 건수가 틀리므로 지어내지 않고 '확인 불가'로 둔다.
     */
    async loadTodaySessions(courses) {
      const active = courses.filter((course) => SESSION_COURSE_STATUSES.includes(course.status));
      const targets = active.slice(0, TODAY_COURSE_LIMIT);
      const todayKst = formatDateKst(new Date(), '');
      this.todayLoading = true;
      this.todayCapped = active.length > targets.length;
      try {
        const perCourse = await mapInBatches(targets, TODAY_CONCURRENCY, async (course) => {
          const page = await listCourseSessions(course.id, { page: 0, size: SESSION_PAGE_SIZE });
          const sessions = Array.isArray(page.data) ? page.data : [];
          const toItem = (session) => ({
            courseId: course.id,
            courseTitle: course.title || '-',
            // QR 창의 '출석 n / 전체' — 승인 인원(목록 응답에 있으면)
            totalCount: Number(course.enrolledCount) || 0,
            session,
          });
          return {
            courseId: course.id,
            today: sessions.filter((session) => isTodaySession(session, todayKst)).map(toItem),
            upcoming: sessions.filter((session) => isUpcomingSession(session, todayKst)).map(toItem),
            next: nextSessionOf(sessions),
          };
        });
        const items = perCourse.flatMap((entry) => entry.today).sort(byStartsAt);
        this.todayItems = items;
        this.todayCount = items.length;
        this.nextItem = perCourse.flatMap((entry) => entry.upcoming).sort(byStartsAt)[0] || null;
        this.nextByCourse = Object.fromEntries(
          perCourse.filter((entry) => entry.next).map((entry) => [entry.courseId, entry.next]),
        );
      } catch {
        this.todayItems = [];
        this.todayCount = null;
        this.nextItem = null;
        this.nextByCourse = {};
      } finally {
        this.todayLoading = false;
      }
    },
    /** 답변 대기 문의 수 — 관리자 열람은 '내 문의' 가 없어 세지 않는다. 실패하면 '-' */
    async loadInquiryCount() {
      if (this.consoleAsAdmin) {
        this.inquiryCount = null;
        return;
      }
      try {
        const page = await listInstructorInquiries({ page: 0, size: 1, status: 'PENDING' });
        this.inquiryCount = page.page?.totalElements ?? (page.data || []).length;
      } catch {
        this.inquiryCount = null;
      }
    },
    studentsText(row) {
      const enrolled = Number(row.enrolledCount) || 0;
      return row.capacity ? `${enrolled} / ${row.capacity}명` : `${enrolled}명`;
    },
    nextSessionText(courseId) {
      const session = this.nextByCourse[courseId];
      return session ? `${session.sessionNo}회차 · ${formatSessionWhen(session)}` : '-';
    },
    /** 임시저장 삭제 — 서버 권한(과정 수정과 같음)에 맞춰 주강사·관리자만 */
    canDeleteDraft(row) {
      return row.status === 'DRAFT' && (this.consoleAsAdmin || row.instructorRole === 'MAIN');
    },
    async deleteDraft(row) {
      if (this.deletingId) return;
      this.deletingId = row.id;
      try {
        if (await confirmDeleteDraftCourse(this.$q, row)) this.rows = this.rows.filter((item) => item.id !== row.id);
      } finally {
        this.deletingId = '';
      }
    },
    // 행 아무 곳이나 누르면 강의 상세 — 행 안 링크(강의명)·[출결][이수] 단추는 제 일을 한다
    onCourseRowClick(event, row) {
      if (event.target.closest('a, button, .q-btn')) return;
      if (window.getSelection?.().toString()) return;
      this.$router.push(this.detailLink(row));
    },
    detailLink(row, tab) {
      return {
        name: 'instructor-course-detail',
        params: { courseId: row.id },
        query: tab ? { tab } : undefined,
      };
    },
    periodText(row) {
      return formatDatePeriodKst(row.startDate, row.endDate);
    },
    // 오늘 회차가 있는 강의는 그 회차가 골라진 채로 — 강의만 골라 주면 회차를 또 골라야 했다
    attendanceCourseLink(row) {
      const today = this.todayItems.find((item) => item.courseId === row.id);
      const query = today ? { courseId: row.id, sessionId: today.session.id } : { courseId: row.id };
      return { path: '/instructor/attendance', query };
    },
    completionLink(row) {
      return { path: '/instructor/completion', query: { courseId: row.id } };
    },
  },
};
</script>

<style scoped>
.course-title-link {
  font-weight: 600;
  /* 검은 글씨라 링크처럼 보이지 않았다 — 기관 강의 관리와 같은 색 */
  color: var(--db-accent);
  text-decoration: none;
}

.course-code {
  margin-top: 2px;
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.course-title-link:hover,
.course-title-link:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.dashboard__course-table :deep(tbody tr) {
  cursor: pointer;
}

.dashboard__course-table :deep(tbody tr:hover td) {
  background: var(--db-bg-section);
}

.dashboard__courses-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 12px;
  margin-bottom: 8px;
}

.dashboard__courses-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.dashboard__courses-summary {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.dashboard__courses-all {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.dashboard__courses-all:hover {
  text-decoration: underline;
}

.dashboard__actions {
  display: inline-flex;
  gap: 6px;
}

.dashboard__muted {
  color: var(--db-text-sub);
}

.dashboard__today {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 520px), 1fr));
  gap: 16px;
  align-items: start;
}

.dashboard__capped {
  font-size: 12px;
  color: var(--db-text-sub);
}
</style>
