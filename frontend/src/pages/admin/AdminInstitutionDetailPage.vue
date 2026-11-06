<template>
  <ConsoleDetailPage
    v-model="tab"
    title="기관 상세 정보"
    avatar-icon="apartment"
    :name="institution?.name"
    :subtitle="institution?.institutionCode"
    role="기관"
    :lines="profileLines"
    :fields="fields"
    :tabs="tabs"
    :two-column="tab === 'instructors'"
    :error="errorMessage"
  >
    <template #actions>
      <q-btn outline no-caps class="btn-md action-back" label="목록으로 돌아가기" @click="goList" />
      <q-btn flat no-caps class="btn-md action-edit" label="기관 정보 수정" @click="openEdit" />
      <q-btn
        flat
        no-caps
        class="btn-md action-danger"
        :label="suspended ? '운영 재개' : '운영 정지'"
        @click="confirmStatus"
      />
    </template>

    <template #field-status>
      <StatusBadge :status="institution?.status || 'ACTIVE'" />
    </template>

    <template #field-signingKey>
      <SigningKeyStatusField
        :institution-id="institutionId"
        :approved-at="institution?.createdAt || ''"
      />
    </template>

    <template #field-homepage="{ field }">
      <a v-if="field.value" :href="field.value" target="_blank" rel="noopener" class="field-link">
        {{ field.value }}
      </a>
      <span v-else>-</span>
    </template>

    <!-- 소속 강사 -->
    <ConsoleTabSection v-if="tab === 'instructors'" title="소속 강사" :count="instructors.length">
      <ConsoleTable
        :columns="instructorColumns"
        :rows="instructors"
        :loading="loading"
        :selectable="false"
        :paginated="false"
        empty-text="소속 강사가 없습니다."
      >
        <template #cell-user="{ row }">
          <button type="button" class="link-cell" @click="selectInstructor(row)">
            <span class="cell-title">{{ row.name || row.userName || '-' }}</span>
            <span class="cell-note cell-note--sub">{{ formatBirthDateCompact(row.birthDate) || '-' }}</span>
          </button>
        </template>
        <template #cell-contact="{ row }">
          <div>{{ row.email || '-' }}</div>
          <div class="cell-note cell-note--sub">{{ maskPhone(row.phone) }}</div>
        </template>
        <template #cell-createdAt="{ row }">{{ formatDateKst(row.joinedAt, '-') }}</template>
        <template #cell-status="{ row }">
          <StatusBadge :status="row.status || 'ACTIVE'" />
        </template>
        <template #cell-lastLoginAt="{ row }">{{ formatDateKst(row.lastLoginAt, '-') }}</template>
        <template #cell-courseCount="{ row }">{{ row.courseCount ?? '-' }}</template>
      </ConsoleTable>
    </ConsoleTabSection>

    <!-- 운영 강의 -->
    <ConsoleTabSection v-else-if="tab === 'courses'" title="운영 강의" :count="courses.length">
      <ConsoleTable
        tall-rows
        :columns="courseColumns"
        :rows="courses"
        :loading="loading"
        :selectable="false"
        :paginated="false"
        empty-text="운영 중인 강의가 없습니다."
      >
        <template #cell-course="{ row }">
          <CourseInfoCell :row="row" :to="`/admin/courses/${row.id}`" />
        </template>
        <template #cell-method>-</template>
        <template #cell-recruitPeriod="{ row }">
          {{ period(row.recruitStartDate, row.recruitEndDate) }}
        </template>
        <template #cell-applicants="{ row }">- / {{ row.capacity ?? '-' }}명</template>
        <template #cell-status="{ row }"><StatusBadge :status="row.status" /></template>
      </ConsoleTable>
    </ConsoleTabSection>

    <!-- 수강 현황 -->
    <ConsoleTabSection
      v-else-if="tab === 'enrollments'"
      title="수강 현황"
      :count="enrollments.length"
    >
      <ConsoleTable
        :columns="enrollmentColumns"
        :rows="enrollments"
        :loading="loading"
        :selectable="false"
        :paginated="false"
        empty-text="수강 내역이 없습니다."
      >
        <template #cell-user="{ row }">
          <div class="cell-title">{{ row.userName || '-' }}</div>
          <div class="cell-note cell-note--sub">{{ formatBirthDateCompact(row.birthDate) || '-' }}</div>
        </template>
        <template #cell-status="{ row }"><StatusBadge :status="row.status" /></template>
        <template #cell-progress="{ row }">
          <div class="progress-cell">
            <span class="cell-note cell-note--sub">{{ progressLabel(row) }}</span>
            <q-linear-progress
              :value="(row.progressPercent ?? 0) / 100"
              size="6px"
              rounded
              color="primary"
            />
          </div>
        </template>
        <template #cell-recent="{ row }">{{ formatDateKst(row.lastStudiedAt, '-') }}</template>
        <template #cell-appliedAt="{ row }">
          {{ formatDateTimeFields(row, 'appliedAt', 'createdAt') }}
        </template>
      </ConsoleTable>
    </ConsoleTabSection>

    <!-- 문의 내역 -->
    <ConsoleTabSection v-else-if="tab === 'inquiries'" title="문의 내역" :count="inquiries.length">
      <ConsoleTable
        :columns="inquiryColumns"
        :rows="inquiries"
        :loading="loading"
        :selectable="false"
        :paginated="false"
        empty-text="등록된 문의가 없습니다."
      >
        <template #cell-inquiry="{ row }">
          <div class="cell-title">{{ row.title || '-' }}</div>
          <div class="cell-note cell-note--sub">{{ row.content || '-' }}</div>
        </template>
        <template #cell-createdAt="{ row }">
          {{ formatDateTimeFields(row, 'createdAt', 'inquiredAt') }}
        </template>
        <template #cell-answered="{ row }">
          <StatusBadge
            :status="isAnswered(row) ? 'ANSWERED' : 'PENDING'"
            :label="isAnswered(row) ? '답변 완료' : '답변 대기'"
          />
        </template>
      </ConsoleTable>
    </ConsoleTabSection>

    <!-- 관리자 정보 -->
    <ConsoleTabSection v-else title="관리자 정보">
      <dl class="admin-info">
        <div v-for="row in adminInfoRows" :key="row.label" class="admin-info__row">
          <dt class="admin-info__label">{{ row.label }}</dt>
          <dd class="admin-info__value">{{ row.value || '-' }}</dd>
        </div>
      </dl>
    </ConsoleTabSection>

    <!-- 소속 강사 탭 우측 패널 -->
    <template #aside>
      <div class="detail-panel">
        <h3 class="detail-panel__title">회원 상세 정보</h3>
        <template v-if="selectedInstructor">
          <div class="detail-panel__name">
            {{ selectedInstructor.name || selectedInstructor.userName || '-' }}
          </div>
          <div class="detail-panel__row">
            <span>기관명</span><span>{{ institution?.name || '-' }}</span>
          </div>
          <div class="detail-panel__row">
            <span>가입일</span><span>{{ formatDateKst(selectedInstructor.joinedAt, '-') }}</span>
          </div>
          <div class="detail-panel__row">
            <span>최근 로그인</span><span>{{ formatDateKst(selectedInstructor.lastLoginAt, '-') }}</span>
          </div>
          <div class="detail-panel__row">
            <span>강의 수</span><span>{{ selectedInstructor.courseCount ?? '-' }}</span>
          </div>
          <div class="detail-panel__row">
            <span>메모</span><span>{{ selectedInstructor.memo || '-' }}</span>
          </div>

          <h4 class="detail-panel__subtitle">최근 강의 내역</h4>
          <AppEmptyState v-if="loadingRecentCourses" compact description="불러오는 중입니다." />
          <q-list v-else-if="recentCourses.length" dense separator>
            <q-item v-for="c in recentCourses" :key="c.id">
              <q-item-section>{{ c.title || '-' }}</q-item-section>
              <q-item-section side><StatusBadge :status="c.status" /></q-item-section>
            </q-item>
          </q-list>
          <AppEmptyState v-else compact description="강의 내역이 없습니다." />
          <q-btn
            flat
            no-caps
            class="btn-sm action-edit"
            label="강의 내역 전체 보기"
            @click="goInstructorDetail"
          />
        </template>
        <AppEmptyState v-else compact description="강사를 선택하세요." />
      </div>
    </template>

    <template #dialogs>
      <q-dialog v-model="showEdit">
        <q-card style="min-width: 400px">
          <q-card-section class="text-h6">기관 정보 수정</q-card-section>
          <q-card-section class="column q-gutter-sm">
            <q-input v-model="form.name" outlined dense label="기관명 *" maxlength="200" />
            <q-input v-model="form.address" outlined dense label="주소" />
            <q-input v-model="form.contactPhone" outlined dense label="연락처" maxlength="30" />
            <q-input
              v-model="form.homepageUrl"
              outlined
              dense
              type="url"
              label="홈페이지"
              placeholder="https://"
              maxlength="500"
              :error="!!homepageError"
              :error-message="homepageError"
            />
            <!-- 관리자명은 기관이 아니라 대표 기관 관리자 계정(administrators[0])의 이름 — PUT /users/{id} 로 바꾼다 -->
            <q-input
              v-if="primaryAdmin"
              v-model="form.adminName"
              outlined
              dense
              label="관리자명 *"
              maxlength="100"
              hint="대표 관리자 계정의 이름이 함께 바뀝니다"
            />
          </q-card-section>
          <q-card-actions align="right">
            <q-btn v-close-popup flat no-caps label="취소" />
            <q-btn
              unelevated
              no-caps
              color="primary"
              :loading="saving"
              label="저장"
              @click="saveEdit"
            />
          </q-card-actions>
        </q-card>
      </q-dialog>
    </template>
  </ConsoleDetailPage>
</template>

<script>
import { formatBirthDateCompact, maskPhone } from '@/utils/user-display';
import AppEmptyState from '@/components/AppEmptyState.vue';
import ConsoleDetailPage from '@/components/console/ConsoleDetailPage.vue';
import ConsoleTable from '@/components/console/ConsoleTable.vue';
import ConsoleTabSection from '@/components/console/ConsoleTabSection.vue';
import CourseInfoCell from '@/components/console/CourseInfoCell.vue';
import SigningKeyStatusField from '@/components/console/SigningKeyStatusField.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { decorateCourseRows } from '@/services/course-rows';
import {
  getInstitution,
  listInstitutionCourses,
  listInstitutionEnrollments,
  listInstitutionInstructors,
  updateInstitution,
} from '@/services/institution-api';
import { listInstitutionInquiries } from '@/services/inquiry-api';
import { listInstructorCoursesOf } from '@/services/instructor-api';
import { updateUser } from '@/services/user-api';
import { formatDateKst, formatDateTimeFields, formatDateTimeKst } from '@/utils/datetime';

const PAGE = { page: 0, size: 100 };

function isHttpUrl(value) {
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
}

const INSTRUCTOR_COLUMNS = [
  { key: 'user', label: '회원 정보', width: 180 },
  { key: 'contact', label: '이메일·연락처', width: 220 },
  { key: 'createdAt', label: '가입일', width: 120, align: 'center' },
  { key: 'status', label: '상태', width: 100, align: 'center' },
  { key: 'lastLoginAt', label: '최근 로그인', width: 120, align: 'center' },
  { key: 'courseCount', label: '강의 수', width: 90, align: 'center' },
];

const COURSE_COLUMNS = [
  { key: 'course', label: '강의 정보', width: 494 },
  { key: 'method', label: '모집 방식', width: 174, align: 'center' },
  { key: 'recruitPeriod', label: '모집 기간', width: 220, align: 'center' },
  { key: 'applicants', label: '신청·정원', width: 220, align: 'center' },
  { key: 'status', label: '상태', width: 170, align: 'center' },
];

const ENROLLMENT_COLUMNS = [
  { key: 'user', label: '수강생', width: 260 },
  { key: 'status', label: '수강 상태', width: 120, align: 'center' },
  { key: 'progress', label: '학습 현황', width: 200, align: 'center' },
  { key: 'appliedAt', label: '신청 일시', width: 180, align: 'center' },
  { key: 'recent', label: '최근 학습일', width: 140, align: 'center' },
];

const INQUIRY_COLUMNS = [
  { key: 'inquiry', label: '문의 정보', width: 500 },
  { key: 'createdAt', label: '문의 일시', width: 180, align: 'center' },
  { key: 'answered', label: '답변 상태', width: 120, align: 'center' },
];

export default {
  name: 'AdminInstitutionDetailPage',
  components: {
    AppEmptyState,
    ConsoleDetailPage,
    ConsoleTabSection,
    ConsoleTable,
    CourseInfoCell,
    SigningKeyStatusField,
    StatusBadge,
  },
  data() {
    return {
      tab: 'instructors',
      loading: false,
      saving: false,
      errorMessage: '',
      showEdit: false,
      institution: null,
      instructors: [],
      courses: [],
      enrollments: [],
      inquiries: [],
      selectedInstructor: null,
      recentCourses: [],
      loadingRecentCourses: false,
      form: { name: '', address: '', contactPhone: '', homepageUrl: '', adminName: '' },
      instructorColumns: INSTRUCTOR_COLUMNS,
      courseColumns: COURSE_COLUMNS,
      enrollmentColumns: ENROLLMENT_COLUMNS,
      inquiryColumns: INQUIRY_COLUMNS,
    };
  },
  computed: {
    institutionId() {
      return this.$route.params.institutionId;
    },
    // 대표 기관 관리자 — 상세 응답 administrators[0] (id 는 사용자 id)
    primaryAdmin() {
      return (this.institution?.administrators || [])[0] || null;
    },
    // 비워 두거나 http(s) 주소만 — 학습자 화면이 이 주소로 링크를 건다
    homepageError() {
      const url = (this.form.homepageUrl || '').trim();
      if (!url) return '';
      return isHttpUrl(url) ? '' : 'http:// 또는 https:// 로 시작하는 주소를 입력해 주세요.';
    },
    suspended() {
      return this.institution?.status === 'SUSPENDED';
    },
    profileLines() {
      const i = this.institution || {};
      return [i.contactEmail || '-', i.address || '-', i.contactPhone || '-'];
    },
    fields() {
      const i = this.institution || {};
      const admin = (i.administrators || [])[0];
      return [
        [
          { label: '대표자', value: i.representativeName },
          { label: '등록번호', value: i.businessNumber },
          { label: '홈페이지', key: 'homepage', value: i.homepageUrl },
          { label: '가입일', value: formatDateTimeKst(i.createdAt, '-') },
          { label: '상태', key: 'status', value: '' },
          // 수료증 서명키 — 승인 때 워커가 자동으로 만든다. 상세 응답에는 없어 따로 조회한다
          { label: '서명키', key: 'signingKey', value: '' },
        ],
        [
          // 관리자명은 administrators[0](상세 조회에서만 온다). 메모 필드는 계약에 없다 — '-' 유지.
          { label: '관리자명', value: admin?.name || '-' },
          { label: '휴대전화', value: admin?.phone || i.contactPhone },
          { label: '이메일', value: admin?.email || i.contactEmail },
          { label: '메모', value: '-' },
        ],
      ];
    },
    tabs() {
      return [
        { label: `소속 강사 (${this.instructors.length})`, value: 'instructors' },
        { label: `운영 강의 (${this.courses.length})`, value: 'courses' },
        { label: `수강 현황 (${this.enrollments.length})`, value: 'enrollments' },
        { label: `문의 내역 (${this.inquiries.length})`, value: 'inquiries' },
        { label: '관리자 정보', value: 'manager' },
      ];
    },
    adminInfoRows() {
      const i = this.institution || {};
      const admin = (i.administrators || [])[0];
      return [
        { label: '대표자', value: i.representativeName },
        { label: '관리자명', value: admin?.name || '-' },
        { label: '휴대전화', value: admin?.phone || i.contactPhone },
        { label: '이메일', value: admin?.email || i.contactEmail },
      ];
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    formatDateTimeFields,
    formatBirthDateCompact,
    maskPhone,
    isAnswered(row) {
      return !!(row.answeredAt || row.replyContent || row.status === 'ANSWERED');
    },
    period(from, to) {
      if (!from && !to) return '-';
      return `${formatDateKst(from, '-')} ~ ${formatDateKst(to, '-')}`;
    },
    progressLabel(row) {
      const value = row.progressPercent ?? '-';
      return value === '-' ? value : `${value}%`;
    },
    rowsOf(result) {
      return Array.isArray(result?.data) ? result.data : [];
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const [institution, instructors, courses, enrollments, inquiries] = await Promise.all([
          getInstitution(this.institutionId),
          listInstitutionInstructors(this.institutionId, PAGE).catch(() => null),
          listInstitutionCourses(this.institutionId, PAGE).catch(() => null),
          listInstitutionEnrollments(this.institutionId, PAGE).catch(() => null),
          listInstitutionInquiries(this.institutionId, PAGE).catch(() => null),
        ]);
        this.institution = institution;
        this.instructors = this.rowsOf(instructors);
        this.courses = await decorateCourseRows(this.rowsOf(courses));
        this.enrollments = this.rowsOf(enrollments);
        this.inquiries = this.rowsOf(inquiries);
      } catch (error) {
        this.errorMessage = error?.message || '기관 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    goList() {
      this.$router.push('/admin/institutions');
    },
    async selectInstructor(row) {
      this.selectedInstructor = row;
      this.recentCourses = [];
      const userId = row?.userId;
      if (!userId) return;
      this.loadingRecentCourses = true;
      try {
        const page = await listInstructorCoursesOf(userId, { size: 5 });
        // 응답이 늦게 와도 그 사이 다른 강사를 고르면 버린다 — 남의 강의를 이 이름 밑에 그리면 안 된다
        if (this.selectedInstructor?.userId !== userId) return;
        this.recentCourses = this.rowsOf(page);
      } catch {
        this.recentCourses = [];
      } finally {
        this.loadingRecentCourses = false;
      }
    },
    goInstructorDetail() {
      const userId = this.selectedInstructor?.userId || this.selectedInstructor?.id;
      if (userId) this.$router.push(`/admin/instructors/${userId}`);
    },
    openEdit() {
      const i = this.institution || {};
      this.form = {
        name: i.name || '',
        address: i.address || '',
        contactPhone: i.contactPhone || '',
        homepageUrl: i.homepageUrl || '',
        adminName: this.primaryAdmin?.name || '',
      };
      this.showEdit = true;
    },
    editFormProblem() {
      if (!this.form.name.trim()) return '기관명을 입력해 주세요.';
      if (this.homepageError) return this.homepageError;
      if (this.primaryAdmin && !this.form.adminName.trim()) return '관리자명을 입력해 주세요.';
      return '';
    },
    async saveEdit() {
      const problem = this.editFormProblem();
      if (problem) {
        this.$q.notify({ type: 'warning', message: problem, position: 'top' });
        return;
      }
      const { adminName, ...fields } = this.form;
      const nextAdminName = adminName.trim();
      const adminChanged = Boolean(this.primaryAdmin) && nextAdminName !== this.primaryAdmin.name;
      this.saving = true;
      try {
        await updateInstitution(this.institutionId, {
          ...fields,
          name: fields.name.trim(),
          homepageUrl: fields.homepageUrl.trim() || null,
        });
      } catch (error) {
        this.saving = false;
        this.$q.notify({ type: 'negative', message: error?.message || '기관 정보를 수정하지 못했습니다.', position: 'top' });
        return;
      }
      // 기관은 저장됐다 — 관리자 이름만 실패하면 그렇게 알리고 창은 열어 둔다(고친 값 그대로 다시 저장하면 된다)
      try {
        if (adminChanged) await updateUser(this.primaryAdmin.id, { name: nextAdminName });
        this.showEdit = false;
        this.$q.notify({ type: 'positive', message: '기관 정보를 수정했습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: `기관 정보는 저장했지만 관리자명은 바꾸지 못했습니다. ${error?.message || ''}`.trim(),
          position: 'top',
        });
      } finally {
        this.saving = false;
        await this.load();
      }
    },
    confirmStatus() {
      const next = this.suspended ? 'ACTIVE' : 'SUSPENDED';
      this.$q
        .dialog({
          title: this.suspended ? '운영 재개' : '운영 정지',
          message: this.suspended
            ? '이 기관의 운영을 다시 시작할까요?'
            : '이 기관의 운영을 정지할까요?',
          cancel: true,
        })
        .onOk(async () => {
          try {
            await updateInstitution(this.institutionId, { status: next });
            this.$q.notify({ type: 'positive', message: '상태를 변경했습니다.', position: 'top' });
            await this.load();
          } catch (error) {
            this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
          }
        });
    },
  },
};
</script>

<style scoped>
.field-link {
  color: var(--db-accent);
  word-break: break-all;
}

.action-back {
  color: var(--db-text-sub);
}

.action-edit {
  color: var(--db-accent);
}

.action-danger {
  color: var(--db-status-pink);
}

.link-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
}

.admin-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0 24px 24px;
}

.admin-info__row {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.admin-info__label {
  width: 100px;
  flex-shrink: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.admin-info__value {
  margin: 0;
  font-size: 16px;
  color: var(--db-text-sub);
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.detail-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-body);
}

.detail-panel__name {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-panel__subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-body);
}

.detail-panel__row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  color: var(--db-text-sub);
}

.progress-cell {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}
</style>
