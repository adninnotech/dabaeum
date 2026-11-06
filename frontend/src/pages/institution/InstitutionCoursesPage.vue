<template>
  <q-page padding>
    <AppPageHeader title="강의 관리">
      <template #actions>
        <q-btn outline no-caps color="primary" icon="refresh" :loading="loading" label="새로고침" @click="loadRows" />
        <q-btn color="primary" unelevated no-caps icon="add" label="강의 등록" @click="openCreate" />
      </template>
    </AppPageHeader>
    <AppInstitutionMissingBanner :show="showInstitutionMissingBanner" />
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="loadRows" />
      </template>
    </AppErrorBanner>
    <!-- 상태는 서버(GET /institutions/{id}/courses?status=)로 거르고 주소(?status=)와 맞춘다 -->
    <div class="course-toolbar q-mb-md">
      <q-select
        :model-value="statusFilter"
        outlined
        dense
        emit-value
        map-options
        :options="statusOptions"
        label="강의 상태"
        class="course-status-select"
        @update:model-value="onStatusChange"
      />
    </div>
    <p v-if="capped" class="capped-note">최근 강의 {{ PAGE_SIZE }}개만 보여 줍니다. 상태로 걸러 보세요.</p>
    <q-table
      flat
      bordered
      class="course-table"
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      :no-data-label="statusFilter ? '이 상태의 강의가 없습니다.' : '아직 강의가 없습니다. [강의 등록]으로 첫 강의를 만들어 주세요.'"
      :pagination="{ rowsPerPage: 10 }"
      :rows-per-page-options="[10, 20, 50]"
      rows-per-page-label="한 번에 보기"
      :pagination-label="(first, last, total) => `총 ${total}개 중 ${first}-${last}`"
    >
      <!-- 표 기본 '데이터 없음'은 경고(⚠) 아이콘이라 비어 있는 것도 오류처럼 보였다 -->
      <template #no-data="{ message }">
        <div class="table-empty">{{ message }}</div>
      </template>
      <!-- 강의명 → 강의 상세(신청 승인·회차·수강 현황을 탭으로 한곳에서) -->
      <template #body-cell-title="props">
        <q-td :props="props">
          <router-link
            :to="{ name: 'institution-course-detail', params: { courseId: props.row.id } }"
            class="course-title-link"
          >
            {{ props.value || '-' }}
          </router-link>
          <!-- 코드는 강의명 아래 — 따로 한 칸이라 표가 카드 밖으로 넘쳤다 -->
          <div v-if="props.row.courseCode" class="course-code">{{ props.row.courseCode }}</div>
        </q-td>
      </template>
      <template #body-cell-classification="props">
        <q-td :props="props" :title="classificationPathText(props.row.classificationPath, '미분류')">
          {{ props.value }}
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.value" />
        </q-td>
      </template>
      <template v-if="showEducationTypeUI" #body-cell-educationType="props">
        <q-td :props="props">
          <EducationTypeBadge :type="props.row.educationType" />
        </q-td>
      </template>
      <!-- 버튼 다섯이 늘어서 폰에서 화면 밖으로 밀렸다. [회차]·[수강]은 옛 화면으로 가 강의명(상세 탭)과 길이 둘이었다 —
           상세 탭으로 바로 가고, 수정·마감은 ⋮ 메뉴로. 공개 전(DRAFT)이면 [게시]만 꺼내 둔다 -->
      <template #body-cell-actions="props">
        <q-td :props="props">
          <CourseRowActions
            :course="props.row"
            :busy="actionId === props.row.id"
            :can-publish="canPublish(props.row)"
            :can-close="canClose(props.row)"
            :can-delete="props.row.status === 'DRAFT'"
            @edit="openEdit"
            @publish="confirmPublish"
            @close-recruit="confirmClose"
            @delete="deleteDraft"
          />
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <ConsoleCourseCard :course="props.row" :to="detailLink(props.row)" :meta-items="cardMeta(props.row)">
            <CourseRowActions
              :course="props.row"
              :busy="actionId === props.row.id"
              :can-publish="canPublish(props.row)"
              :can-close="canClose(props.row)"
              :can-delete="props.row.status === 'DRAFT'"
              @edit="openEdit"
              @publish="confirmPublish"
              @close-recruit="confirmClose"
              @delete="deleteDraft"
            />
          </ConsoleCourseCard>
        </div>
      </template>
    </q-table>

    <q-dialog v-model="showForm">
      <q-card class="course-form">
        <q-card-section class="text-h6">{{ editingId ? '강의 수정' : '강의 등록' }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input
            v-model="form.courseCode"
            outlined
            dense
            label="강의 코드 *"
            hint="기관 안에서 강의를 구분하는 코드입니다. 예: EXCEL-2026-1"
          />
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
            :model-value="form.educationType"
            outlined
            dense
            emit-value
            map-options
            :options="eduOptions"
            label="교육 유형"
            @update:model-value="setEduType"
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
          <q-btn flat label="취소" v-close-popup />
          <q-btn
            color="primary"
            unelevated
            :loading="saving"
            :label="editingId ? '수정' : '저장'"
            @click="doSave"
          />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { confirmDeleteDraftCourse } from '@/utils/draft-course-delete';
import AppDateInput from '@/components/AppDateInput.vue';
import { formatDatePeriodKst, toDateTimeLocalKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import ConsoleCourseCard from '@/components/console/ConsoleCourseCard.vue';
import CourseRowActions from '@/components/institution/CourseRowActions.vue';
import ClassificationPicker from '@/components/course/ClassificationPicker.vue';
import { classificationLeafName, classificationPathText, courseSaveErrorMessage } from '@/utils/classification';
import institutionScope from '@/mixins/institution-scope';
import { listInstitutionCourses } from '@/services/institution-api';
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
  publishCourse,
  updateCourse,
} from '@/services/course-api';
import { EDITOR_TOOLBAR } from '@/utils/rich-text';
import { pasteMarkdownAsRichText } from '@/utils/markdown-paste';
import { getEducationTypeOptions, STATUS_LABEL } from '@/utils/status';
import { filterEducationTypeColumn } from '@/utils/course-mapper';
import educationTypeUi from '@/mixins/education-type-ui';
import { integerInputError } from '@/utils/integer-input';

// 상태 필터로 고를 수 있는 값 — ?status= 도 이 목록 안의 값만 받는다
const COURSE_STATUS_VALUES = ['DRAFT', 'RECRUITING', 'RECRUITMENT_CLOSED', 'IN_PROGRESS', 'COMPLETED'];

function normalizeStatus(value) {
  return COURSE_STATUS_VALUES.includes(value) ? value : null;
}

function buildColumns() {
  return filterEducationTypeColumn([
    { name: 'title', label: '강의명', field: 'title', align: 'left' },
    {
      // 마지막 단계만 — 전체 경로는 칸에 마우스를 올리면(title)
      name: 'classification',
      label: '분류',
      field: (row) => classificationLeafName(row.classificationPath, '미분류'),
      align: 'left',
    },
    { name: 'educationType', label: '유형', field: 'educationType', align: 'center' },
    // 시작·종료 두 칸 → 한 칸
    { name: 'period', label: '운영 기간', field: (row) => formatDatePeriodKst(row.startDate, row.endDate), align: 'left' },
    { name: 'capacity', label: '정원', field: 'capacity' },
    { name: 'status', label: '상태', field: 'status', align: 'center' },
    { name: 'actions', label: '바로가기', field: 'actions', align: 'right' },
  ]);
}

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

const PAGE_SIZE = 50;

function emptyForm() {
  return {
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
  name: 'InstitutionCoursesPage',
  components: {
    AppErrorBanner,
    AppInstitutionMissingBanner,
    AppPageHeader,
    ClassificationPicker,
    EducationTypeBadge,
    StatusBadge,
    AppDateInput,
    ConsoleCourseCard,
    CourseRowActions,
  },
  mixins: [institutionScope, educationTypeUi],
  data() {
    return {
      PAGE_SIZE,
      rows: [],
      capped: false,
      loading: false,
      saving: false,
      actionId: null,
      errorMessage: '',
      showForm: false,
      editingId: null,
      form: emptyForm(),
      eduOptions: getEducationTypeOptions(),
      editorToolbar: EDITOR_TOOLBAR,
      dateFields: DATE_FIELDS,
      columns: buildColumns(),
      // 대시보드 '공개 대기 강의' 링크가 ?status=DRAFT 로 들어온다
      statusFilter: normalizeStatus(this.$route.query.status),
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
  },
  watch: {
    // 같은 화면에 머문 채 다른 ?status= 링크로 들어오거나 선택을 바꾸면 그 상태로 다시 부른다
    '$route.query.status'(value) {
      if (this.$route.path !== this.ownPath) return;
      const next = normalizeStatus(value);
      if (next === this.statusFilter) return;
      this.statusFilter = next;
      this.loadRows();
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    classificationPathText,
    // 마크다운 글을 붙여 넣으면 제목·목록·표로 바꿔 넣는다
    pasteMarkdown(event) {
      pasteMarkdownAsRichText(event);
    },
    /** 선택은 주소에만 쓰고, 다시 부르는 일은 위 $route 감시가 한 번만 한다 */
    onStatusChange(value) {
      const status = normalizeStatus(value);
      const query = { ...this.$route.query };
      delete query.status;
      this.$router.replace({ query: status ? { ...query, status } : query });
    },
    setEduType(value) {
      this.form.educationType = value;
    },
    openCreate() {
      this.editingId = null;
      this.form = emptyForm();
      this.showForm = true;
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
      this.showForm = true;
    },
    async doSave() {
      const datesError = courseDatesError(this.form);
      if (datesError) {
        this.$q.notify({ type: 'warning', message: datesError, position: 'top' });
        return;
      }
      if (this.editingId) {
        await this.doUpdate();
        return;
      }
      await this.doCreate();
    },
    async deleteDraft(row) {
      if (this.actionId) return;
      this.actionId = row.id;
      try {
        if (await confirmDeleteDraftCourse(this.$q, row)) this.rows = this.rows.filter((item) => item.id !== row.id);
      } finally {
        this.actionId = null;
      }
    },
    async loadRows() {
      this.loading = true;
      this.errorMessage = '';
      try {
        // 기관이 없으면(관리자가 아직 안 고름·소속 없음) 비운다 — 예전엔 전체 강의를 보여 기관 콘솔에 남의 기관 강의가 섞였다
        if (!this.institutionId) {
          this.rows = [];
          return;
        }
        // sort: 측정 결과 서버 기본 정렬이 이미 createdAt desc — 보내지 않는다(00-common §3)
        const page = await listInstitutionCourses(this.institutionId, {
          page: 0,
          size: PAGE_SIZE,
          status: this.statusFilter || undefined,
        });
        this.rows = page.data || [];
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async doCreate() {
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '강의 코드와 강의명을 입력해 주세요.', position: 'top' });
        return;
      }
      if (this.capacityError) {
        this.$q.notify({ type: 'warning', message: `정원: ${this.capacityError}`, position: 'top' });
        return;
      }
      if (!this.institutionId) {
        this.$q.notify({ type: 'warning', message: '기관을 먼저 선택해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        const educationType = resolveEducationType(this.form.educationType);
        await createCourse({
          institutionId: this.institutionId,
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
          creditBankEligible: false,
          creditValue: null,
        });
        this.showForm = false;
        this.$q.notify({ type: 'positive', message: '강의를 등록했습니다.', position: 'top' });
        await this.loadRows();
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
    async doUpdate() {
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '강의 코드와 강의명을 입력해 주세요.', position: 'top' });
        return;
      }
      if (this.capacityError) {
        this.$q.notify({ type: 'warning', message: `정원: ${this.capacityError}`, position: 'top' });
        return;
      }
      this.saving = true;
      try {
        const educationType = resolveEducationType(this.form.educationType);
        await updateCourse(this.editingId, {
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
          location: resolveCourseLocation(educationType, this.form.location, this.form.location || '미정'),
          onlineUrl: resolveOnlineUrl(educationType, this.form.onlineUrl),
        });
        this.showForm = false;
        this.editingId = null;
        this.$q.notify({ type: 'positive', message: '강의를 수정했습니다.', position: 'top' });
        await this.loadRows();
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
    detailLink(row, tab) {
      return { name: 'institution-course-detail', params: { courseId: row.id }, query: tab ? { tab } : undefined };
    },
    cardMeta(row) {
      return [row.courseCode, formatDatePeriodKst(row.startDate, row.endDate), row.capacity ? `정원 ${row.capacity}명` : ''];
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
      this.actionId = row.id;
      try {
        await publishCourse(row.id);
        this.$q.notify({ type: 'positive', message: `'${row.title}' 강의를 게시했습니다.`, position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: courseSaveErrorMessage(error, '요청에 실패했습니다.'),
          position: 'top',
        });
      } finally {
        this.actionId = null;
      }
    },
    async doClose(row) {
      this.actionId = row.id;
      try {
        await closeCourse(row.id);
        this.$q.notify({ type: 'positive', message: `'${row.title}' 강의의 모집을 마감했습니다.`, position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: courseSaveErrorMessage(error, '요청에 실패했습니다.'),
          position: 'top',
        });
      } finally {
        this.actionId = null;
      }
    },
  },
};
</script>

<style scoped>
.course-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.course-status-select {
  width: 180px;
  max-width: 100%;
}

/* 칸 여덟에 바로가기 넷이 한 줄로 들어가게 — 기본 좌우 16px 이라 카드 밖으로 14px 넘쳤다 */
.course-table :deep(thead th),
.course-table :deep(tbody td) {
  padding-left: 10px;
  padding-right: 10px;
}

.course-code {
  margin-top: 2px;
  font-size: 12px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.table-empty {
  width: 100%;
  padding: 24px 0;
  text-align: center;
  font-size: 14px;
  color: var(--db-text-sub);
}

.course-title-link {
  color: var(--db-accent);
  font-weight: 600;
  text-decoration: none;
}

.course-title-link:hover,
.course-title-link:focus-visible {
  text-decoration: underline;
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

.capped-note {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--db-text-sub);
}
</style>
