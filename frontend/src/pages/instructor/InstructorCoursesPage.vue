<template>
  <q-page padding>
    <AppPageHeader
      :title="consoleAsAdmin ? '전체 강의 관리' : '내 강의 관리'"
      :subtitle="consoleAsAdmin ? '관리자 계정이라 모든 기관의 강의를 보여 줍니다.' : ''"
    >
      <template #actions>
        <q-btn outline color="primary" icon="refresh" :loading="loading" label="새로고침" @click="loadRows" />
        <q-btn color="primary" unelevated icon="add" label="강의 등록" to="/instructor/courses/create" />
      </template>
    </AppPageHeader>
    <ConsoleStatCards class="q-mb-lg" :items="stats" :loading="loading && !allRows.length" />
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadRows" />
      </template>
    </AppErrorBanner>
    <q-table
      flat
      bordered
      class="course-table"
      :pagination="{ rowsPerPage: 10 }"
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      :no-data-label="
        consoleAsAdmin
          ? '등록된 강의가 없습니다.'
          : '아직 맡은 강의가 없습니다. [강의 등록]으로 첫 강의를 만들거나 기관 관리자에게 배정을 요청해 주세요.'
      "
    >
      <!-- 강의명 → 강의 상세. 예전엔 이름이 글자뿐이라 상세로 가는 길이 [회차]·[수강생] 버튼뿐이었다 -->
      <template #body-cell-title="props">
        <q-td :props="props">
          <router-link :to="detailLink(props.row)" class="course-title-link">{{ props.value || '-' }}</router-link>
          <!-- 코드는 강의명 아래(대시보드·기관 강의 관리와 같은 모양) — 이 표엔 코드가 아예 없었다 -->
          <div v-if="props.row.courseCode" class="course-code">{{ props.row.courseCode }}</div>
        </q-td>
      </template>
      <template #body-cell-enrollment="props">
        <q-td :props="props">{{ enrollmentText(props.row) }}</q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props"><StatusBadge :status="props.value" /></q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            v-if="props.row.status === 'DRAFT'"
            flat
            dense
            no-caps
            color="primary"
            label="이어서 작성"
            :to="`/instructor/courses/${props.row.id}/edit`"
          />
          <!-- 임시저장 강좌는 지울 수 있다(주강사·관리자) — 자동 저장으로 생긴 강좌가 쌓였다 -->
          <q-btn
            v-if="canDeleteDraft(props.row)"
            flat
            dense
            no-caps
            color="negative"
            label="삭제"
            :loading="deletingId === props.row.id"
            @click="deleteDraft(props.row)"
          />
          <!-- [회차]를 누르면 '학습 목차' 탭이 열려 이름이 달랐다 — 여는 탭 이름 그대로 -->
          <q-btn flat dense no-caps color="primary" label="학습 목차" :to="detailLink(props.row, 'curriculum')" />
          <q-btn flat dense no-caps color="primary" label="수강 현황" :to="detailLink(props.row, 'enrollments')" />
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <ConsoleCourseCard
            :course="props.row"
            :to="detailLink(props.row)"
            :meta-items="cardMeta(props.row)"
          >
            <q-btn
              v-if="props.row.status === 'DRAFT'"
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
              @click="deleteDraft(props.row)"
            />
            <q-btn
              flat
              dense
              no-caps
              color="primary"
              label="학습 목차"
              :to="detailLink(props.row, 'curriculum')"
            />
            <q-btn
              flat
              dense
              no-caps
              color="primary"
              label="수강 현황"
              :to="detailLink(props.row, 'enrollments')"
            />
          </ConsoleCourseCard>
        </div>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import { classificationLeafName } from '@/utils/classification';
import { formatDatePeriodKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import ConsoleCourseCard from '@/components/console/ConsoleCourseCard.vue';
import ConsoleStatCards from '@/components/console/ConsoleStatCards.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { getInstructorCourseStats } from '@/services/instructor-api';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';
import { confirmDeleteDraftCourse } from '@/utils/draft-course-delete';

// 배정 역할 enum(CourseInstructorRole: MAIN | ASSISTANT)을 그대로 보이면 강사가 읽지 못한다 — 강의 강사 배정 화면과 같은 말로 바꾼다
const INSTRUCTOR_ROLE_LABEL = { MAIN: '주강사', ASSISTANT: '보조 강사' };
const COURSE_PAGE_SIZE = 50;

export default {
  components: { AppPageHeader, AppErrorBanner, ConsoleCourseCard, ConsoleStatCards, StatusBadge },
  name: 'InstructorCoursesPage',
  mixins: [instructorConsoleCourses],
  data() {
    return {
      allRows: [],
      // 지우는 중인 임시저장 강좌 id
      deletingId: '',
      courseStats: null,
      loading: false,
      errorMessage: '',
      columns: [
        { name: 'title', label: '강의명', field: 'title', align: 'left' },
        {
          name: 'classification',
          label: '분류',
          field: (row) => classificationLeafName(row.classificationPath, '미분류'),
          align: 'left',
        },
        {
          name: 'instructorRole',
          label: '내 역할',
          field: 'instructorRole',
          format: (value) => INSTRUCTOR_ROLE_LABEL[value] || value || '-',
          align: 'left',
        },
        // 시작·종료 두 칸 → 한 칸(표가 카드 밖으로 30px 넘쳐 바로가기가 잘렸다)
        { name: 'period', label: '운영 기간', field: (row) => formatDatePeriodKst(row.startDate, row.endDate), align: 'left' },
        { name: 'enrollment', label: '신청자', field: 'enrollment', align: 'right' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'actions', label: '바로가기', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    rows() {
      return this.allRows;
    },
    stats() {
      if (this.courseStats) {
        return [
          { label: '전체 강의', value: this.courseStats.total ?? 0 },
          { label: '모집중', value: this.courseStats.recruiting ?? 0 },
          { label: '진행중', value: this.courseStats.inProgress ?? 0 },
          { label: '종료', value: this.courseStats.completed ?? 0 },
        ];
      }
      const list = this.rows;
      return [
        // 목록은 COURSE_PAGE_SIZE 개까지라 통계를 못 받으면 덜 셀 수 있다 — 이름으로 구분한다
        {
          label: list.length >= COURSE_PAGE_SIZE ? '조회된 강의' : '전체 강의',
          value: list.length,
        },
        { label: '모집중', value: list.filter((r) => r.status === 'RECRUITING').length },
        { label: '진행중', value: list.filter((r) => r.status === 'IN_PROGRESS').length },
        { label: '종료', value: list.filter((r) => r.status === 'COMPLETED').length },
      ];
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    detailLink(row, tab) {
      return {
        name: 'instructor-course-detail',
        params: { courseId: row.id },
        query: tab ? { tab } : undefined,
      };
    },
    cardMeta(row) {
      return [
        // 표처럼 폰 카드에도 코드(대시보드 카드와 같게)
        row.courseCode,
        formatDatePeriodKst(row.startDate, row.endDate),
        INSTRUCTOR_ROLE_LABEL[row.instructorRole] || '',
        row.capacity ? `신청 ${this.enrollmentText(row)}` : '',
      ];
    },
    enrollmentText(row) {
      // capacity 가 0/null 이면 나누지 않고 '-' (00-common.md §6, plan-2 Task 5 Step 2)
      if (!row.capacity) return '-';
      return `${row.enrolledCount ?? 0} / ${row.capacity}명`;
    },
    /** 임시저장 삭제 — 서버 권한(과정 수정과 같음)에 맞춰 주강사·관리자만 단추를 보인다 */
    canDeleteDraft(row) {
      return row.status === 'DRAFT' && (this.consoleAsAdmin || row.instructorRole === 'MAIN');
    },
    async deleteDraft(row) {
      if (this.deletingId) return;
      this.deletingId = row.id;
      try {
        const deleted = await confirmDeleteDraftCourse(this.$q, row);
        if (deleted) this.allRows = this.allRows.filter((item) => item.id !== row.id);
      } finally {
        this.deletingId = '';
      }
    },
    async loadRows() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const [page, stats] = await Promise.all([
          // sort 생략: 측정 결과(2026-09-18) 기본 정렬과 동일 (00-common.md §3)
          this.fetchConsoleCourses({ page: 0, size: COURSE_PAGE_SIZE }),
          // 통계 API 는 '나에게 배정된' 기준 — 관리자는 목록으로 센다
          this.consoleAsAdmin ? null : getInstructorCourseStats().catch(() => null),
        ]);
        this.courseStats = stats;
        // 강사는 배정된 강의만 — 예전엔 배정이 0건이면 전체 강의 목록으로 대체해 남의 강의가 보였다
        this.allRows = (page.data || []).map((course) => ({
          ...course,
          instructorRole: course.instructorRole || '',
        }));
      } catch (error) {
        this.allRows = [];
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 칸 좌우 기본 16px 이라 바로가기가 카드 밖으로 넘쳤다 */
.course-table :deep(thead th),
.course-table :deep(tbody td) {
  padding-left: 10px;
  padding-right: 10px;
}

.course-title-link {
  font-weight: 600;
  /* 검은 글씨라 링크처럼 보이지 않았다 */
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
</style>
