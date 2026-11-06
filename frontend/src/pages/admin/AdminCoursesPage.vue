<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="전체 강의 관리"
    list-title="강의 목록"
    unit="개"
    tall-rows
    empty-text="등록된 강의가 없습니다."
    search-placeholder="강의명, 기관명, 강사명으로 검색"
    :stats="stats"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    @search="onSearch"
  >
    <!-- 강의 상태는 서버(GET /courses?status=)로 거른다 — 100건 상한 밖의 비공개 강의도 나오게, 주소(?status=)와 맞춘다.
         다른 필터처럼 고르기만 하고 [검색]을 눌러야 반영된다(고르자마자 다시 부르던 것) -->
    <template #filter-extra>
      <q-select
        :model-value="statusDraft"
        outlined
        dense
        emit-value
        map-options
        :options="statusOptions"
        label="강의 상태"
        class="course-status-select"
        @update:model-value="statusDraft = normalizeStatus($event)"
      />
    </template>

    <template #header-actions>
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="btn-md"
        label="강의 등록"
        @click="openCreate"
      />
    </template>

    <template #cell-course="{ row }">
      <CourseInfoCell :row="row" :to="`/admin/courses/${row.id}`" />
    </template>

    <template #cell-method="{ row }">{{ selectionMethodLabel(row) }}</template>

    <template #cell-recruitPeriod="{ row }">
      {{ period(row.recruitStartDate, row.recruitEndDate) }}
    </template>

    <template #cell-applicants="{ row }">{{ row.enrolledCount ?? 0 }} / {{ row.capacity ?? '-' }}명</template>

    <template #cell-status="{ row }">
      <!-- 마감임박은 서버 상태가 아니다 — 실제 상태(모집중)를 가리지 않게 뱃지 옆에 작은 표시로 붙인다 -->
      <div class="course-status-cell">
        <StatusBadge :status="row.status" />
        <span v-if="closingSoon(row)" class="closing-soon-tag">마감임박</span>
      </div>
    </template>

    <template #cell-createdAt="{ row }">{{ formatDateKst(row.createdAt, '-') }}</template>

    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="$router.push(`/admin/courses/${row.id}`)">
        <q-item-section>상세 보기</q-item-section>
      </q-item>
      <q-item v-close-popup clickable @click="openEdit(row)">
        <q-item-section>수정</q-item-section>
      </q-item>
      <!-- 서버가 받는 상태에서만 보인다 — 게시는 DRAFT, 마감은 RECRUITING 만 허용(CourseStatusPolicy) -->
      <q-item v-if="canPublish(row)" v-close-popup clickable @click="confirmPublish(row)">
        <q-item-section>게시</q-item-section>
      </q-item>
      <q-item v-if="canClose(row)" v-close-popup clickable @click="confirmClose(row)">
        <q-item-section>마감</q-item-section>
      </q-item>
      <!-- 임시저장(DRAFT)만 지울 수 있다 — 게시된 강좌는 서버가 409 -->
      <q-item v-if="row.status === 'DRAFT'" v-close-popup clickable class="text-negative" @click="deleteDraft(row)">
        <q-item-section>임시저장 삭제</q-item-section>
      </q-item>
    </template>

    <template #default>
      <!-- 기존 강의 생성·수정 다이얼로그 이관 -->
      <q-dialog v-model="showCreate">
        <q-card class="course-form">
          <q-card-section class="text-h6">{{
            editingId ? '강의 수정' : '강의 등록'
          }}</q-card-section>
          <q-card-section class="q-gutter-md">
            <!-- 전에는 기관을 고를 수 없어 모든 강의가 개발용 기본 기관으로 들어갔다 — 등록할 때 기관을 고른다 -->
            <q-select
              v-if="!editingId"
              v-model="form.institutionId"
              outlined
              dense
              emit-value
              map-options
              :options="institutionOptions"
              :loading="institutionsLoading"
              label="기관 *"
            />
            <q-input v-model="form.courseCode" outlined dense label="강의 코드 *" />
            <q-input v-model="form.title" outlined dense label="강의명 *" />
            <!-- 강의 소개는 HTML(강사 위저드의 q-editor 로 쓴 본문)이다 — 글상자에 넣으면 태그가 그대로 보였다 -->
            <div class="course-form__field">
              <div class="course-form__label">강의 소개</div>
              <q-editor
                @paste="pasteMarkdown"
                v-model="form.description"
                :toolbar="editorToolbar"
                min-height="220px"
                max-height="420px"
                class="course-form__editor"
                placeholder="강의 목표, 주요 내용 등을 적어 주세요."
              />
            </div>
            <div class="course-form__classification">
              <div class="course-form__label">강좌 분류 (공개하려면 필수)</div>
              <ClassificationPicker
                v-model="form.classificationCode"
                :current-path="form.classificationPath"
              />
            </div>
            <q-select
              v-if="showEducationTypeSelect"
              v-model="form.educationType"
              outlined
              dense
              emit-value
              map-options
              :options="eduOptions"
              label="교육 유형"
            />
            <!-- 전에는 날짜 칸 없이 오늘 기준 임의 날짜로 등록됐다 — 직접 입력받는다 -->
            <div class="row q-col-gutter-sm">
              <div v-for="field in dateFields" :key="field.key" class="col-12 col-sm-6">
                <AppDateInput
                  v-model="form[field.key]"
                  outlined
                  dense
                  stack-label
                  :label="field.label"
                />
              </div>
            </div>
            <q-input
              v-model.number="form.capacity"
              outlined
              dense
              type="number"
              label="정원"
              :error="!!capacityError"
              :error-message="capacityError"
            />
            <q-input
              v-if="showOnlineUrlField"
              v-model="form.onlineUrl"
              outlined
              dense
              label="온라인 강의 주소"
            />
          </q-card-section>
          <q-card-actions align="right">
            <q-btn v-close-popup flat no-caps label="취소" />
            <q-btn
              unelevated
              no-caps
              color="primary"
              :loading="saving"
              :label="editingId ? '수정' : '저장'"
              @click="doSave"
            />
          </q-card-actions>
        </q-card>
      </q-dialog>
    </template>
  </ConsoleListPage>
</template>

<script>
import { confirmDeleteDraftCourse } from '@/utils/draft-course-delete';
import AppDateInput from '@/components/AppDateInput.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import CourseInfoCell from '@/components/console/CourseInfoCell.vue';
import ClassificationPicker from '@/components/course/ClassificationPicker.vue';
import { courseSaveErrorMessage } from '@/utils/classification';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { listInstitutions } from '@/services/institution-api';
import {
  DEFAULT_EDUCATION_TYPE,
  isOnlineCoursesEnabled,
  resolveCourseLocation,
  resolveEducationType,
  resolveOnlineUrl,
} from '@/config/features';
import {
  closeCourse,
  createCourse,
  listCourses,
  publishCourse,
  updateCourse,
} from '@/services/course-api';
import { decorateCourseRows } from '@/services/course-rows';
import { daysFromToday, formatDateKst, toDateTimeLocalKst } from '@/utils/datetime';
import { EDUCATION_TYPE_OPTIONS_ALL, getEducationTypeOptions, STATUS_LABEL } from '@/utils/status';
import { SELECTION_METHOD_OPTIONS } from '@/config/course-options';
import { toInstitutionOptions } from '@/utils/institution-option';
import { EDITOR_TOOLBAR } from '@/utils/rich-text';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { integerInputError } from '@/utils/integer-input';

const CLOSING_SOON_DAYS = 3;

// 상태 필터로 고를 수 있는 값 — ?status= 도 이 목록 안의 값만 받는다
const COURSE_STATUS_VALUES = ['RECRUITING', 'RECRUITMENT_CLOSED', 'IN_PROGRESS', 'COMPLETED', 'DRAFT'];

function normalizeStatus(value) {
  return COURSE_STATUS_VALUES.includes(value) ? value : null;
}

const COLUMNS = [
  { key: 'course', label: '강의 정보', minWidth: 240 },
  { key: 'method', label: '모집 방식', width: 110, align: 'center' },
  { key: 'recruitPeriod', label: '모집 기간', width: 170, align: 'center' },
  { key: 'applicants', label: '신청·정원', width: 100, align: 'center' },
  { key: 'status', label: '상태', width: 90, align: 'center' },
  { key: 'createdAt', label: '등록일', width: 110, align: 'center' },
];

const DATE_FIELDS = [
  { key: 'recruitStartDate', label: '모집 시작일 *' },
  { key: 'recruitEndDate', label: '모집 마감일 *' },
  { key: 'startDate', label: '운영 시작일 *' },
  { key: 'endDate', label: '운영 종료일 *' },
];

/** API 날짜(YYYY-MM-DD 또는 date-time) → `<input type="date">` 값. 없으면 '' */
function toDateInput(value) {
  if (!value) return '';
  const str = String(value);
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) return str;
  return toDateTimeLocalKst(str).slice(0, 10);
}

/** 모집 시작 ≤ 모집 마감 ≤ 운영 시작 ≤ 운영 종료. 문제 없으면 '' — YYYY-MM-DD 는 문자열 비교로 순서가 맞다 */
function courseDatesError(form) {
  const values = DATE_FIELDS.map((field) => form[field.key]);
  if (values.some((value) => !value)) return '모집·운영 기간을 모두 입력해 주세요.';
  const ordered = values.every((value, index) => index === 0 || values[index - 1] <= value);
  return ordered ? '' : '날짜 순서를 확인해 주세요. 모집 시작 ≤ 모집 마감 ≤ 운영 시작 ≤ 운영 종료';
}

function emptyForm() {
  return {
    institutionId: null,
    courseCode: '',
    title: '',
    description: '',
    classificationCode: null,
    classificationPath: [],
    educationType: DEFAULT_EDUCATION_TYPE,
    capacity: 30,
    onlineUrl: '',
    recruitStartDate: '',
    recruitEndDate: '',
    startDate: '',
    endDate: '',
  };
}

export default {
  name: 'AdminCoursesPage',
  components: { ClassificationPicker, ConsoleListPage, CourseInfoCell, StatusBadge, AppDateInput },
  mixins: [consoleList],
  data() {
    return {
      saving: false,
      showCreate: false,
      editingId: null,
      form: emptyForm(),
      editorToolbar: EDITOR_TOOLBAR,
      institutionOptions: [],
      institutionsLoading: false,
      eduOptions: getEducationTypeOptions(),
      dateFields: DATE_FIELDS,
      columns: COLUMNS,
      searchFields: ['title', 'institutionName', 'instructorName'],
      serverSearch: true,
      // 대시보드 '공개 대기 강의' 링크가 ?status=DRAFT 로 들어온다
      statusFilter: normalizeStatus(this.$route.query.status),
      // 고른 값 — [검색]을 눌러야 statusFilter 로 넘어간다
      statusDraft: normalizeStatus(this.$route.query.status),
      // 다른 화면으로 떠나는 순간에도 $route 감시가 돌므로, 이 화면 주소일 때만 쿼리를 읽고 쓴다
      ownPath: this.$route.path,
      statusOptions: [
        { label: '전체', value: null },
        ...COURSE_STATUS_VALUES.map((value) => ({ label: STATUS_LABEL[value], value })),
      ],
    };
  },
  computed: {
    capacityError() {
      return integerInputError(this.form.capacity, { min: 1 });
    },
    showEducationTypeSelect() {
      return isOnlineCoursesEnabled() && this.eduOptions.length > 1;
    },
    showOnlineUrlField() {
      return isOnlineCoursesEnabled() && this.form.educationType !== 'OFFLINE';
    },
    // 강의 상태는 위 #filter-extra 선택으로 서버에서 거른다(여기 두면 화면 안에서만 걸러 주소와 어긋난다)
    filters() {
      return [
        // 콘솔 필터는 기능 플래그와 무관하게 3종을 모두 노출한다
        { key: 'educationType', label: '수업 방식', options: EDUCATION_TYPE_OPTIONS_ALL },
      ];
    },
    stats() {
      return [
        // 상태로 거른 목록이면 '전체'가 아니다
        { label: this.statusFilter ? '조회된 강의 수' : '전체 강의 수', value: this.rows.length, unit: '개' },
        { label: '모집중', value: this.countBy('status', 'RECRUITING'), unit: '개' },
        { label: '수강중', value: this.countBy('status', 'IN_PROGRESS'), unit: '개' },
        { label: '강의종료', value: this.countBy('status', 'COMPLETED'), unit: '개' },
      ];
    },
  },
  watch: {
    // 같은 화면에 머문 채 다른 ?status= 링크로 들어오거나 선택을 바꾸면 그 상태로 다시 부른다
    '$route.query.status'(value) {
      if (this.$route.path !== this.ownPath) return;
      const next = normalizeStatus(value);
      if (next === this.statusFilter) return;
      this.statusFilter = next;
      this.statusDraft = next;
      this.applyServerSearch();
    },
  },
  created() {
    this.loadCourses();
  },
  methods: {
    // 마크다운 글을 붙여 넣으면 제목·목록·표로 바꿔 넣는다
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
    formatDateKst,
    period(from, to) {
      if (!from && !to) return '-';
      return `${formatDateKst(from, '-')} ~ ${formatDateKst(to, '-')}`;
    },
    selectionMethodLabel(row) {
      const value = row.detail?.selectionMethod;
      return SELECTION_METHOD_OPTIONS.find((o) => o.value === value)?.label || '-';
    },
    /** 모집 마감 3일 이내 — 백엔드 상태가 아니라 프론트 판정 */
    closingSoon(row) {
      if (row.status !== 'RECRUITING') return false;
      const left = daysFromToday(row.recruitEndDate);
      return left !== null && left >= 0 && left <= CLOSING_SOON_DAYS;
    },
    normalizeStatus,
    // 상태는 주소에도 남긴다. 주소를 먼저 바꿔 두면 $route 감시가 같은 값이라 다시 부르지 않는다
    onSearch(payload) {
      this.search = payload;
      if (this.statusDraft !== this.statusFilter) {
        this.statusFilter = this.statusDraft;
        const query = { ...this.$route.query };
        delete query.status;
        this.$router.replace({ query: this.statusFilter ? { ...query, status: this.statusFilter } : query });
      }
      this.applyServerSearch();
    },
    /** applyServerSearch()가 재조회할 때 부르는 이름 — 이 페이지의 실제 로더는 loadCourses() */
    async deleteDraft(row) {
      if (await confirmDeleteDraftCourse(this.$q, row)) this.rows = this.rows.filter((item) => item.id !== row.id);
    },
    load() {
      return this.loadCourses();
    },
    async loadCourses() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({
          page: 0,
          size: 100,
          q: this.search.q || undefined,
          status: this.statusFilter || undefined,
        });
        this.rows = await decorateCourseRows(Array.isArray(result.data) ? result.data : []);
      } catch (error) {
        this.handleLoadError(error, '강의 목록 조회 실패');
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.editingId = null;
      this.form = emptyForm();
      this.showCreate = true;
      if (!this.institutionOptions.length) this.loadInstitutionOptions();
    },
    async loadInstitutionOptions() {
      this.institutionsLoading = true;
      try {
        const result = await listInstitutions({ page: 0, size: 100, status: 'ACTIVE' });
        this.institutionOptions = toInstitutionOptions(result.data);
      } catch (error) {
        this.institutionOptions = [];
        this.$q.notify({ type: 'negative', message: error?.message || '기관 목록을 불러오지 못했습니다.', position: 'top' });
      } finally {
        this.institutionsLoading = false;
      }
    },
    openEdit(row) {
      this.editingId = row.id;
      this.form = {
        courseCode: row.courseCode || '',
        title: row.title || '',
        description: row.description || '',
        classificationCode: row.classificationCode || null,
        classificationPath: Array.isArray(row.classificationPath) ? row.classificationPath : [],
        educationType: resolveEducationType(row.educationType || DEFAULT_EDUCATION_TYPE),
        capacity: row.capacity || 30,
        onlineUrl: row.onlineUrl || '',
        startDate: toDateInput(row.startDate),
        endDate: toDateInput(row.endDate),
        recruitStartDate: toDateInput(row.recruitStartDate),
        recruitEndDate: toDateInput(row.recruitEndDate),
        location: row.location || '',
      };
      this.showCreate = true;
    },
    async doSave() {
      if (!this.editingId && !this.form.institutionId) {
        this.$q.notify({ type: 'warning', message: '강의를 등록할 기관을 골라 주세요.', position: 'top' });
        return;
      }
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '강의 코드와 강의명을 입력해 주세요.', position: 'top' });
        return;
      }
      if (this.capacityError) {
        this.$q.notify({ type: 'warning', message: `정원: ${this.capacityError}`, position: 'top' });
        return;
      }
      const datesError = courseDatesError(this.form);
      if (datesError) {
        this.$q.notify({ type: 'warning', message: datesError, position: 'top' });
        return;
      }
      const educationType = resolveEducationType(this.form.educationType);
      const isEdit = !!this.editingId;
      this.saving = true;
      try {
        const body = {
          courseCode: this.form.courseCode.trim(),
          title: this.form.title.trim(),
          description: this.form.description || null,
          classificationCode: this.form.classificationCode || null,
          educationType,
          startDate: this.form.startDate,
          endDate: this.form.endDate,
          recruitStartDate: this.form.recruitStartDate,
          recruitEndDate: this.form.recruitEndDate,
          capacity: Number(this.form.capacity) || 30,
          location: resolveCourseLocation(educationType, this.form.location),
          onlineUrl: resolveOnlineUrl(educationType, this.form.onlineUrl),
        };
        if (isEdit) {
          await updateCourse(this.editingId, body);
        } else {
          await createCourse({
            ...body,
            institutionId: this.form.institutionId,
            creditBankEligible: false,
            creditValue: null,
          });
        }
        this.showCreate = false;
        this.editingId = null;
        this.$q.notify({
          type: 'positive',
          message: isEdit ? '강의를 수정했습니다.' : '강의가 등록되었습니다.',
          position: 'top',
        });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: courseSaveErrorMessage(error, '요청에 실패했습니다.'),
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
    canPublish(row) {
      return row.status === 'DRAFT';
    },
    canClose(row) {
      return row.status === 'RECRUITING';
    },
    confirmPublish(row) {
      this.$q
        .dialog({
          title: '강의 게시',
          message: `'${row.title}' 강의를 게시할까요? 게시하면 학습자가 강의를 보고 수강 신청할 수 있습니다.`,
          cancel: true,
        })
        .onOk(() => this.doPublish(row));
    },
    // 마감을 되돌리는 reopen 은 플랫폼 관리자 정정(사유 필수)뿐이라 미리 알린다
    confirmClose(row) {
      this.$q
        .dialog({
          title: '모집 마감',
          message: `'${row.title}' 강의의 모집을 마감할까요? 마감하면 더 이상 수강 신청을 받지 않으며, 되돌리려면 플랫폼 관리자의 정정이 필요합니다.`,
          cancel: true,
        })
        .onOk(() => this.doClose(row));
    },
    async doPublish(row) {
      await this.runAction(() => publishCourse(row.id), '게시 처리되었습니다.');
    },
    async doClose(row) {
      await this.runAction(() => closeCourse(row.id), '마감 처리되었습니다.');
    },
    async runAction(call, message) {
      try {
        await call();
        this.$q.notify({ type: 'positive', message, position: 'top' });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: courseSaveErrorMessage(error, '요청에 실패했습니다.'),
          position: 'top',
        });
      }
    },
  },
};
</script>

<style scoped>
.course-status-cell {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

/* 상태 뱃지보다 한 단계 약한 주황 테두리 표시 — 취소(분홍)와 헷갈리지 않게 대기 계열 색을 쓴다 */
.closing-soon-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border: 1px solid var(--db-status-orange);
  border-radius: 4px;
  /* 흰 바탕에 #fd9001 글자는 대비가 약해 틴트 바탕 + 진한 주황 글자로 */
  background: var(--db-status-orange-soft);
  color: var(--db-status-orange-text);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

/* 목록 머리의 다른 필터 선택(.filter-select)과 높이를 맞춘다 */
.course-status-select {
  width: 140px;
}

.course-status-select :deep(.q-field__control) {
  height: 36px;
  min-height: 36px;
  border-radius: 6px;
}

/* 본문 편집기가 들어가 글상자 시절 폭(520)보다 넓힌다 */
.course-form {
  width: 720px;
  max-width: calc(100vw - 32px);
}

.course-form__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.course-form__classification {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.course-form__label {
  font-size: 13px;
  line-height: 18px;
  color: var(--db-text-sub);
}
</style>
