<template>
  <ConsoleDetailPage
    v-model="tab"
    title="강사 상세 정보"
    :name="user?.name"
    :subtitle="formatDateKst(user?.birthDate, '-')"
    role="강사"
    :lines="profileLines"
    :fields="fields"
    :tabs="tabs"
    :error="errorMessage"
  >
    <template #actions>
      <q-btn outline no-caps class="btn-md action-back" label="목록으로 돌아가기" @click="goList" />
      <q-btn
        flat
        no-caps
        class="btn-md action-muted"
        label="비밀번호 초기화"
        @click="confirmReset"
      />
      <q-btn flat no-caps class="btn-md action-edit" label="회원 정보 수정" @click="openEdit" />
      <q-btn
        flat
        no-caps
        class="btn-md action-danger"
        label="회원 탈퇴 처리"
        @click="confirmWithdraw"
      />
    </template>

    <template #field-status>
      <StatusBadge
        :status="user?.status"
        :label="statusBadge(user?.status).label"
        :color="statusBadge(user?.status).color"
      />
    </template>

    <ConsoleTabSection v-if="isCourseTab" :title="activeTabLabel" :count="tabCourses.length">
      <AppEmptyState v-if="!tabCourses.length" description="해당 강의가 없습니다." />
      <q-list v-else separator>
        <q-item v-for="row in tabCourses" :key="row.id">
          <q-item-section>
            <CourseInfoCell :row="courseCellRow(row)" :to="`/admin/courses/${row.id}`" />
          </q-item-section>
          <q-item-section side>
            <div class="cell-note cell-note--sub">신청자 {{ row.enrolledCount }}명</div>
          </q-item-section>
          <q-item-section side>
            <q-badge outline color="primary">
              {{ row.instructorRole === 'MAIN' ? '주강사' : '보조강사' }}
            </q-badge>
          </q-item-section>
          <q-item-section side>
            <StatusBadge :status="row.status" />
          </q-item-section>
        </q-item>
      </q-list>
    </ConsoleTabSection>

    <ConsoleTabSection
      v-else-if="tab === 'applications'"
      :title="activeTabLabel"
      :count="enrollments.length"
    >
      <AppEmptyState v-if="!enrollments.length" description="신청 내역이 없습니다." />
      <q-list v-else separator>
        <q-item v-for="row in enrollments" :key="row.enrollmentId">
          <q-item-section>
            <div class="cell-title">{{ row.userName || '-' }}</div>
            <div class="cell-note cell-note--sub">
              {{ row.courseTitle || '-' }}
              <i class="cell-sep" />
              최근 학습 {{ formatDateKst(row.lastStudiedAt, '-') }}
            </div>
          </q-item-section>
          <q-item-section side>
            <div class="cell-note cell-note--sub">
              <!-- 출석률은 attendanceRate(학습자 출결 요약과 같은 계산) — progressPercent 는 진도라 숫자가 달랐다 -->
              {{ row.attendedCount }} / {{ row.sessionCount ?? 0 }}회 · 출석률 {{ formatAttendanceRate(row.attendanceRate) }}
            </div>
          </q-item-section>
          <q-item-section side>
            <StatusBadge :status="row.status" />
          </q-item-section>
        </q-item>
      </q-list>
    </ConsoleTabSection>

    <ConsoleTabSection v-else :title="activeTabLabel" :count="inquiries.length">
      <AppEmptyState v-if="!inquiries.length" description="등록된 문의가 없습니다." />
      <q-list v-else separator>
        <q-item v-for="row in inquiries" :key="row.id">
          <q-item-section>
            <div class="cell-title">{{ row.title || '-' }}</div>
            <div class="cell-note cell-note--sub">
              {{ formatDateTimeKst(row.createdAt, '-') }}
              <i class="cell-sep" />
              {{ row.courseTitle || '-' }}
              <i class="cell-sep" />
              {{ INQUIRY_TYPE_LABEL[row.inquiryType] || '문의' }}
            </div>
          </q-item-section>
          <q-item-section side>
            <StatusBadge :status="row.status" />
          </q-item-section>
        </q-item>
      </q-list>
    </ConsoleTabSection>

    <template #dialogs>
      <MemberEditDialog v-model="showEdit" :user="user" @saved="load" />

      <PasswordResetDialog
        v-model="resetDialogOpen"
        :name="resetTargetName"
        :password="temporaryPassword"
      />
    </template>
  </ConsoleDetailPage>
</template>

<script>
import { INQUIRY_TYPE_LABEL } from '@/utils/status';
import AppEmptyState from '@/components/AppEmptyState.vue';
import ConsoleDetailPage from '@/components/console/ConsoleDetailPage.vue';
import MemberEditDialog from '@/components/console/MemberEditDialog.vue';
import CourseInfoCell from '@/components/console/CourseInfoCell.vue';
import ConsoleTabSection from '@/components/console/ConsoleTabSection.vue';
import PasswordResetDialog from '@/components/console/PasswordResetDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { storedFileContentUrl } from '@/services/file-api';
import { listInstructorInquiriesOf } from '@/services/inquiry-api';
import {
  getInstructorCourseStatsOf,
  listInstructorCoursesOf,
  listInstructorEnrollmentStatusOf,
} from '@/services/instructor-api';
import userAdmin from '@/mixins/user-admin';
import { getUser } from '@/services/user-api';
import { formatDateKst, formatDateTimeKst } from '@/utils/datetime';
import { formatAttendanceRate } from '@/utils/attendance-rate';

const TABS = [
  { label: '진행중인 강의', value: 'active' },
  { label: '종료된 강의', value: 'finished' },
  { label: '진행 예정 강의', value: 'upcoming' },
  // 이 강사 강의에 들어온 수강생 신청이다 — '신청 내역'은 강사 본인의 신청처럼 읽혔다
  { label: '수강생 신청 현황', value: 'applications' },
  { label: '문의 내역', value: 'inquiries' },
];

export default {
  name: 'AdminInstructorDetailPage',
  components: {
    AppEmptyState,
    ConsoleDetailPage,
    MemberEditDialog,
    ConsoleTabSection,
    CourseInfoCell,
    PasswordResetDialog,
    StatusBadge,
  },
  mixins: [userAdmin],
  data() {
    return {
      INQUIRY_TYPE_LABEL,
      tab: TABS[0].value,
      tabs: TABS,
      user: null,
      courses: [],
      enrollments: [],
      inquiries: [],
      // { total, recruiting, inProgress, completed, studentCount, inquiryCount }
      stats: null,
      errorMessage: '',
      showEdit: false,
    };
  },
  computed: {
    userId() {
      return this.$route.params.userId;
    },
    activeTabLabel() {
      return TABS.find((item) => item.value === this.tab)?.label || '';
    },
    isCourseTab() {
      return ['active', 'finished', 'upcoming'].includes(this.tab);
    },
    tabCourses() {
      if (this.tab === 'active') return this.courses.filter((c) => c.status === 'IN_PROGRESS');
      if (this.tab === 'finished') return this.courses.filter((c) => c.status === 'COMPLETED');
      if (this.tab === 'upcoming') {
        return this.courses.filter((c) => c.status === 'RECRUITING' || c.status === 'DRAFT');
      }
      return [];
    },
    profileLines() {
      return [this.user?.email || '-', this.user?.phone || '-'];
    },
    fields() {
      return [
        [
          { label: '진행 중 강의', value: this.stats ? String(this.stats.inProgress) : '-' },
          { label: '종료된 강의', value: this.stats ? String(this.stats.completed) : '-' },
          // studentCount 는 승인된 수강신청 건수 — 한 사람이 두 강의를 들으면 2로 센다
          { label: '총 수강생 수', value: this.stats ? String(this.stats.studentCount) : '-' },
          { label: '문의 수', value: this.stats ? String(this.stats.inquiryCount) : '-' },
        ],
        [
          { label: '가입일', value: formatDateTimeKst(this.user?.createdAt, '-') },
          { label: '최근 로그인', value: formatDateKst(this.user?.lastLoginAt, '-') },
          { label: '메모', value: this.user?.adminMemo || '-' },
          { label: '상태', key: 'status', value: '' },
        ],
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatAttendanceRate,
    formatDateKst,
    formatDateTimeKst,
    courseCellRow(course) {
      // 목록 응답은 mainInstructorName 으로 주고 CourseInfoCell 은 instructorName 을 읽는다
      return {
        ...course,
        instructorName: course.mainInstructorName || '',
        thumbnailUrl: course.thumbnailFileId
          ? storedFileContentUrl(course.thumbnailFileId)
          : '',
      };
    },
    async load() {
      this.errorMessage = '';
      try {
        const [user, coursePage, stats] = await Promise.all([
          getUser(this.userId),
          listInstructorCoursesOf(this.userId, { size: 100 }),
          getInstructorCourseStatsOf(this.userId),
        ]);
        this.user = user;
        this.courses = coursePage.data || [];
        this.stats = stats;
      } catch (error) {
        this.errorMessage = error?.message || '회원 정보를 불러오지 못했습니다.';
      }
      await this.loadActivity();
    },
    // 신청·문의 내역은 본 정보와 따로 받는다 — 실패해도 강사 정보·강의 탭은 그대로 보인다
    async loadActivity() {
      try {
        const [enrollmentPage, inquiryPage] = await Promise.all([
          listInstructorEnrollmentStatusOf(this.userId, { size: 100 }),
          listInstructorInquiriesOf(this.userId, { size: 100 }),
        ]);
        this.enrollments = enrollmentPage.data;
        this.inquiries = inquiryPage.data;
      } catch (error) {
        this.notifyError(error, '신청·문의 내역을 불러오지 못했습니다.');
      }
    },
    goList() {
      this.$router.push('/admin/instructors');
    },
    // 확인은 user-admin mixin 이 대상 이름과 함께 묻는다(여기서 한 번 더 묻지 않는다)
    confirmReset() {
      this.resetPassword(this.user);
    },
    confirmWithdraw() {
      this.changeStatus(this.user, 'WITHDRAWN');
    },
    openEdit() {
      this.showEdit = true;
    },
    notifyError(error, fallback) {
      this.$q.notify({ type: 'negative', message: error?.message || fallback, position: 'top' });
    },
  },
};
</script>

<style scoped>

.action-back,
.action-muted {
  color: var(--db-text-sub);
}

.action-edit {
  color: var(--db-accent);
}

.action-danger {
  color: var(--db-status-pink);
}

/* CourseInfoCell 의 scoped 스타일이 여기엔 닿지 않아 신청·문의 행 구분선을 따로 둔다 */
.cell-sep {
  display: inline-block;
  width: 1px;
  height: 10px;
  margin: 0 4px;
  vertical-align: middle;
  background: var(--db-border);
}
</style>
