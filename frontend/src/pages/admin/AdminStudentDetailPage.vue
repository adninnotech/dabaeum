<template>
  <ConsoleDetailPage
    v-model="tab"
    title="수강생 상세 정보"
    :name="user?.name"
    :subtitle="formatDateKst(user?.birthDate, '-')"
    :avatar-url="profileImageUrl"
    role="일반 회원"
    :lines="profileLines"
    :fields="fields"
    :tabs="tabs"
    :error="errorMessage"
    two-column
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

    <ConsoleTabSection v-if="tab === 'inquiries'" :title="activeTabLabel" :count="inquiries.length">
      <AppEmptyState v-if="!inquiries.length" description="등록된 문의가 없습니다." />
      <q-list v-else separator>
        <q-item v-for="row in inquiries" :key="row.id">
          <q-item-section>
            <div class="cell-title">{{ row.title || '-' }}</div>
            <div class="cell-note cell-note--sub">
              {{ formatDateTimeKst(row.createdAt, '-') }}
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

    <ConsoleTabSection v-else :title="activeTabLabel" :count="tabCourses.length">
      <AppEmptyState v-if="!tabCourses.length" description="해당 강의가 없습니다." />
      <q-list v-else separator>
        <q-item v-for="row in tabCourses" :key="row.enrollmentId">
          <q-item-section>
            <CourseInfoCell :row="courseCellRow(row)" :to="`/admin/courses/${row.courseId}`" />
          </q-item-section>
          <q-item-section side>
            <div v-if="tab === 'applications'" class="cell-note cell-note--sub">
              신청 {{ formatDateKst(row.appliedAt, '-') }}
            </div>
            <div v-else class="cell-note cell-note--sub">
              {{ row.attendedCount }} / {{ row.sessionCount ?? 0 }}회
            </div>
          </q-item-section>
          <q-item-section side>
            <StatusBadge :status="row.status" :label="row.statusLabel" />
          </q-item-section>
        </q-item>
      </q-list>
    </ConsoleTabSection>

    <template #aside>
      <ConsoleTabSection title="관리자 메모">
        <q-card-section>
          <q-input
            v-model="memoDraft"
            outlined
            type="textarea"
            maxlength="1000"
            :rows="6"
            placeholder="이 회원에 대한 관리자 메모를 남겨 주세요."
          />
          <div class="row justify-end q-mt-sm">
            <q-btn unelevated no-caps color="primary" label="저장" :loading="savingMemo" @click="saveMemo" />
          </div>
        </q-card-section>
      </ConsoleTabSection>
    </template>

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
import ConsoleTabSection from '@/components/console/ConsoleTabSection.vue';
import CourseInfoCell from '@/components/console/CourseInfoCell.vue';
import PasswordResetDialog from '@/components/console/PasswordResetDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import userAdmin from '@/mixins/user-admin';
import {
  getUser,
  getUserLearningStats,
  listUserInquiries,
  listUserLearningCourses,
  updateUserMemo,
} from '@/services/user-api';
import { listEnrollmentsOfUser } from '@/services/enrollment-api';
import { enrollmentBadgeLabel } from '@/utils/enrollment-decision';
import { formatDateKst, formatDateTimeKst } from '@/utils/datetime';
import { toCourseCard } from '@/utils/course-fields';
import { storedFileContentUrl } from '@/services/file-api';

const TABS = [
  { label: '수강 중', value: 'active' },
  { label: '종료된 강의', value: 'finished' },
  { label: '신청 내역', value: 'applications' },
  { label: '문의 내역', value: 'inquiries' },
];

export default {
  name: 'AdminStudentDetailPage',
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
      applications: [],
      inquiries: [],
      stats: null,
      memoDraft: '',
      savingMemo: false,
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
    tabCourses() {
      if (this.tab === 'active') return this.courses.filter((c) => c.status === 'IN_PROGRESS');
      if (this.tab === 'finished') return this.courses.filter((c) => c.status === 'COMPLETED');
      // 학습자 본인 '수강 신청' 탭과 같은 데이터 — 처리 대기(APPLIED)만 걸러 0건이었다(QA ADM-07). 철회·취소 이력도 보인다
      if (this.tab === 'applications') return this.applications;
      return [];
    },
    profileLines() {
      return [this.user?.email || '-', this.user?.phone || '-'];
    },
    profileImageUrl() {
      return storedFileContentUrl(this.user?.profileImageId);
    },
    fields() {
      return [
        [
          { label: '총 수강 강의', value: this.stats ? String(this.stats.totalCount) : '-' },
          { label: '진행 중 강의', value: this.stats ? String(this.stats.inProgressCount) : '-' },
          { label: '수료 강의', value: this.stats ? String(this.stats.completedCount) : '-' },
          { label: '총 학습시간', value: this.studyTimeLabel },
          { label: '평균 진도율', value: this.stats != null ? `${this.stats.averageProgressPercent}%` : '-' },
        ],
        [
          { label: '가입일', value: formatDateTimeKst(this.user?.createdAt, '-') },
          { label: '최근 로그인', value: formatDateKst(this.user?.lastLoginAt, '-') },
          { label: '문의 수', value: this.stats ? String(this.stats.inquiryCount) : '-' },
          { label: '상태', key: 'status', value: '' },
        ],
      ];
    },
    studyTimeLabel() {
      if (!this.stats) return '-';
      const minutes = this.stats.totalStudyMinutes || 0;
      return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`;
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    formatDateTimeKst,
    courseCellRow(row) {
      return { ...toCourseCard(row), title: row.title };
    },
    async load() {
      this.errorMessage = '';
      try {
        const [user, coursePage, enrollmentPage, inquiryPage, stats] = await Promise.all([
          getUser(this.userId),
          listUserLearningCourses(this.userId, { size: 100 }),
          listEnrollmentsOfUser(this.userId, { size: 100, sort: 'createdAt,desc' }),
          listUserInquiries(this.userId, { size: 100 }),
          getUserLearningStats(this.userId),
        ]);
        this.user = user;
        this.memoDraft = user?.adminMemo || '';
        this.courses = coursePage.data || [];
        this.applications = (enrollmentPage.data || []).map((row) => ({
          ...row,
          enrollmentId: row.id,
          title: row.courseTitle,
          statusLabel: enrollmentBadgeLabel(row),
        }));
        this.inquiries = inquiryPage.data || [];
        this.stats = stats;
      } catch (error) {
        this.errorMessage = error?.message || '회원 정보를 불러오지 못했습니다.';
      }
    },
    async saveMemo() {
      this.savingMemo = true;
      try {
        this.user = await updateUserMemo(this.userId, this.memoDraft || null);
        this.$q.notify({ type: 'positive', message: '메모를 저장했습니다.', position: 'top' });
      } catch (error) {
        this.notifyError(error, '메모 저장에 실패했습니다.');
      } finally {
        this.savingMemo = false;
      }
    },
    goList() {
      this.$router.push('/admin/students');
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

/* CourseInfoCell 의 scoped 스타일이 여기엔 닿지 않아 문의 행 구분선을 따로 둔다 */
.cell-sep {
  display: inline-block;
  width: 1px;
  height: 10px;
  margin: 0 4px;
  vertical-align: middle;
  background: var(--db-border);
}
</style>
