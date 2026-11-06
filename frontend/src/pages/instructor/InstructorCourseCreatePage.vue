<template>
  <q-page class="wizard-page">
    <div class="wizard-section">
      <!-- [취소]가 하단 [다음 단계] 옆에 같은 무게로 있었다 — 나가는 길은 머리 글자 링크로.
           저장 안 한 내용이 있으면 나가기 확인(beforeRouteLeave)이 묻는다 -->
      <router-link to="/instructor/courses" class="wizard-back">
        <q-icon name="arrow_back" size="16px" aria-hidden="true" />
        내 강의 관리
      </router-link>
      <h1 class="wizard-title">{{ isEditMode ? '강의 수정' : '새 강의 등록' }}</h1>

      <WizardSteps :steps="WIZARD_STEPS" :step="step" @go="goToStep" />

      <!-- 단계 화면은 components/course-wizard/WizardStep* — 각자 본문(wizard-main)과 오른쪽(wizard-sub)을 그린다 -->
      <div class="wizard-body">
        <component :is="stepComponent" />
      </div>
    </div>

    <WizardFooter
      :step="step"
      :is-last="step === WIZARD_STEPS.length"
      :saving="saving"
      :submit-label="submitLabel"
      :draft-saved-at="draftSavedAt"
      :draft-saved-auto="draftSavedAuto"
      :auto-save-failed="autoSaveFailed"
      :auto-save-enabled="!isEditMode"
      @save-draft="saveDraft()"
      @prev="step -= 1"
      @next="goNext"
    />
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import WizardFooter from '@/components/course-wizard/WizardFooter.vue';
import WizardStepBasic from '@/components/course-wizard/WizardStepBasic.vue';
import WizardStepExtra from '@/components/course-wizard/WizardStepExtra.vue';
import WizardStepPreview from '@/components/course-wizard/WizardStepPreview.vue';
import WizardStepSchedule from '@/components/course-wizard/WizardStepSchedule.vue';
import WizardSteps from '@/components/course-wizard/WizardSteps.vue';
import { WIZARD_STEPS } from '@/config/course-options';
import courseWizardSave from '@/mixins/course-wizard-save';
import courseWizardSessions from '@/mixins/course-wizard-sessions';
import courseWizardValidation from '@/mixins/course-wizard-validation';
import { deleteDraftCourse, getCourse, listCourseAttachments } from '@/services/course-api';
import { deleteErrorMessage } from '@/utils/draft-course-delete';
import { storedFileContentUrl, uploadFile } from '@/services/file-api';
import { getInstitution, listInstitutionInstructors, listInstitutions } from '@/services/institution-api';
import { listCourseInstructors } from '@/services/instructor-api';
import { listCourseSessions } from '@/services/session-api';
import { useAuthStore } from '@/stores/auth-store';
import { pickAttachments } from '@/utils/attachments';
import { parseCourseDetail } from '@/utils/course-detail';
import { toInstitutionOptions } from '@/utils/institution-option';

const MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024;
// 자동 저장 간격 — 단계를 넘길 때도 저장한다
const AUTO_SAVE_MS = 60 * 1000;
/** 지운 첨부를 되돌릴 수 있는 시간 */
const UNDO_TIMEOUT_MS = 6000;
const STEP_COMPONENTS = [WizardStepBasic, WizardStepSchedule, WizardStepExtra, WizardStepPreview];

/** CourseSession.startsAt/endsAt(ISO, KST 저장) → 위저드 입력칸용 YYYY-MM-DD / HH:mm */
function isoToKstParts(iso) {
  if (!iso) return { date: '', time: '' };
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return { date: '', time: '' };
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || '';
  return { date: `${get('year')}-${get('month')}-${get('day')}`, time: `${get('hour')}:${get('minute')}` };
}

/*
 * 강의 등록 위저드 — 2942줄 한 파일이던 것을 나눴다.
 * 단계 화면: components/course-wizard/*, 회차·검증·저장: mixins/course-wizard-*.
 * 이 페이지는 입력 상태(form)·불러오기·업로드·나가기 확인만 들고, 단계 컴포넌트에 provide 로 넘긴다.
 */
export default {
  name: 'InstructorCourseCreatePage',
  components: { WizardFooter, WizardSteps },
  mixins: [courseWizardSessions, courseWizardValidation, courseWizardSave],
  provide() {
    return { wizard: this };
  },
  data() {
    return {
      WIZARD_STEPS,
      allInstitutionOptions: [],
      step: 1,
      // [다음 단계]에서 걸린 항목별 오류 — { 항목키: 문구 }. 칸 아래에 표시한다
      fieldErrors: {},
      saving: false,
      uploading: false,
      // 첨부 업로드 진행 — 끝나기 전 이동·저장을 막는다
      attaching: false,
      attachDone: 0,
      attachTotal: 0,
      institutionName: '',
      institutionRegion: '',
      // 기능 B — 임시저장 멱등 처리
      draftCourseId: null,
      draftSavedAt: null,
      // 자동 저장 — 새 강의 등록에서만(공개된 강의 수정은 저장 즉시 반영되므로 자동으로 하지 않는다)
      draftSavedAuto: false,
      autoSaveFailed: false,
      autoSaveTimer: null,
      // 나가기 확인 — 마지막으로 저장(또는 불러온) 시점의 입력값. 이것과 다를 때만 묻는다
      savedSnapshot: '',
      // 등록이 끝나 화면을 떠나는 이동, 또는 [취소]에서 이미 확인받은 이동은 다시 묻지 않는다
      skipLeaveGuard: false,
      // 기능 A — 관리자 대행 등록 시 담당 강사 배정
      instructorOptions: [],
      loadingInstructors: false,
      selectedInstructorId: null,
      instructorAssigned: false,
      form: {
        // step 1
        institutionId: null,
        classificationCode: null,
        // 불러온 강좌의 분류 이름 — 코드가 사용 중지됐을 때 피커가 이름을 보여 준다
        classificationPath: [],
        title: '',
        description: '',
        tags: '',
        targetAudiences: ['ANYONE'],
        visibility: 'RECRUITING',
        educationType: 'OFFLINE',
        instructorProfileVisible: true,
        thumbnail: null,
        // step 2
        scheduleMode: 'REPEAT',
        repeat: { weekdays: [], startTime: '', endTime: '', firstDate: '', count: null },
        sessions: [],
        location: '',
        startDate: '',
        endDate: '',
        recruitStartDate: '',
        recruitEndDate: '',
        // 비어 있으면 매번 채워야 했다 — 흔한 정원으로 시작, 강사가 고친다(수정 화면은 불러온 값)
        capacity: 20,
        selectionMethod: 'FCFS',
        waitlistCapacity: null,
        // step 3 — 전부 백엔드 없음
        completionEnabled: true,
        completionCriteria: 'PROGRESS',
        requiredProgressRate: 80,
        issueCertificate: true,
        tuitionType: 'FREE',
        tuitionAmount: null,
        materialFeeType: 'NONE',
        materialFeeAmount: null,
        notes: '',
        // 위저드에 입력칸이 없는 필드. 편집 모드에서 기존 값을 그대로 다시 보내기 위해 들고 있는다.
        // PUT 은 detail 을 통째로 교체하므로, 비워 보내면 학습자 강좌 상세의 학습목표·준비물이 지워진다.
        materials: '',
        learningObjective: '',
        attachments: [],
      },
    };
  },
  computed: {
    stepComponent() {
      return STEP_COMPONENTS[this.step - 1];
    },
    /** 비공개는 저장만, 그 밖엔 등록 즉시 공개 — 버튼 이름을 실제 동작에 맞춘다 */
    submitLabel() {
      if (this.isEditMode) return '수정 저장';
      if (!this.canPublishCourse) return '등록';
      return this.form.visibility === 'DRAFT' ? '비공개로 저장' : '등록';
    },
    ...mapState(useAuthStore, ['primaryInstitutionId', 'myInstitutions', 'session']),
    /** 임시저장한 강의를 이어서 쓰는 경로 — /instructor/courses/:courseId/edit */
    editCourseId() {
      return this.$route.params.courseId || null;
    },
    isEditMode() {
      return !!this.editCourseId;
    },
    /** 소속이 없는 계정(플랫폼 관리자) — 전체 기관에서 골라야 한다. 전에는 개발용 기본 기관으로 조용히 등록됐다 */
    needsInstitutionPick() {
      return !(this.myInstitutions || []).length && !this.primaryInstitutionId;
    },
    /** 소속 기관 select 옵션 — 소속 2개 이상이거나, 소속이 없으면 전체 기관 */
    institutionOptions() {
      if (this.needsInstitutionPick) return this.allInstitutionOptions;
      return toInstitutionOptions(this.myInstitutions);
    },
    /** 강좌가 속할 기관 id — 등록 바디·강사 조회·기관명 로딩이 모두 이 값을 쓴다 */
    courseInstitutionId() {
      return this.form.institutionId || this.primaryInstitutionId || null;
    },
    /** 로그인 사용자가 이 강좌 기관의 강사인가 — 맞으면 백엔드가 자동으로 MAIN 배정한다 */
    isInstructorAtCourseInstitution() {
      const roles = this.session?.roles;
      if (!Array.isArray(roles)) return false;
      const institutionId = this.courseInstitutionId;
      return roles.some((r) => r?.role === 'INSTRUCTOR' && r?.institutionId === institutionId);
    },
    /** 공개(POST /courses/{id}/publish)는 플랫폼 관리자·그 기관 관리자만 가능 — 강사가 부르면 403 */
    canPublishCourse() {
      const roles = this.session?.roles;
      if (!Array.isArray(roles)) return false;
      return roles.some(
        (r) =>
          r?.role === 'PLATFORM_ADMIN' ||
          (r?.role === 'INSTITUTION_ADMIN' && r?.institutionId === this.courseInstitutionId),
      );
    },
    /** 관리자 대행 등록 등 — 자동 배정이 안 되는 경우에만 담당 강사 선택 UI 를 보인다 */
    showInstructorAssignField() {
      return !this.isInstructorAtCourseInstitution;
    },
    /** 하이브리드는 프레임이 없어 오프라인으로 취급 */
    isOnlineCourse() {
      return this.form.educationType === 'ONLINE';
    },
    sessionDates() {
      return this.form.sessions
        .map((s) => s.date)
        .filter(Boolean)
        .sort();
    },
    previewStartDate() {
      return this.isOnlineCourse ? this.form.startDate : this.sessionDates[0] || '';
    },
    previewEndDate() {
      return this.isOnlineCourse ? this.form.endDate : this.sessionDates.at(-1) || '';
    },
    previewInstructor() {
      return this.form.instructorProfileVisible ? useAuthStore().user : null;
    },
  },
  watch: {
    // 1단계는 편집기로 길어 다음 단계가 중간부터 보였다 — 단계가 바뀌면 맨 위로
    step() {
      this.autoSave();
      this.fieldErrors = {};
      this.$nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }));
    },
    // 표시 중인 오류만 다시 따져 고친 칸의 문구를 지운다 — 손대지 않은 칸에 새 오류를 띄우지는 않는다
    form: {
      deep: true,
      handler() {
        const shownKeys = Object.keys(this.fieldErrors);
        if (!shownKeys.length) return;
        const current = this.stepErrors(this.step);
        this.fieldErrors = Object.fromEntries(
          shownKeys.filter((key) => current[key]).map((key) => [key, current[key]]),
        );
      },
    },
    // '누구나'는 다른 대상과 배타 — 백엔드 규칙 미정, 프론트 UX만
    'form.targetAudiences'(value, previous) {
      const hasAnyone = value.includes('ANYONE');
      if (!hasAnyone || value.length <= 1) return;
      const justChecked = !(previous || []).includes('ANYONE');
      this.form.targetAudiences = justChecked ? ['ANYONE'] : value.filter((v) => v !== 'ANYONE');
    },
    // 다중 소속 관리자가 소속 기관을 바꾸면 담당 강사 후보 목록도 그 기관 것으로 다시 받는다
    courseInstitutionId() {
      this.loadInstitutionName();
      if (this.showInstructorAssignField) this.loadInstructorOptions();
    },
  },
  created() {
    if (this.needsInstitutionPick) this.loadAllInstitutions();
    this.loadInstitutionName();
    if (this.showInstructorAssignField) this.loadInstructorOptions();
    if (this.isEditMode) this.loadForEdit(this.editCourseId);
    // 대상 watch 등이 초기값을 고친 뒤에 찍어야 아무것도 안 만졌는데 "변경됨"으로 잡히지 않는다
    this.$nextTick(this.markSaved);
  },
  mounted() {
    window.addEventListener('beforeunload', this.onBeforeUnload);
    this.autoSaveTimer = window.setInterval(() => this.autoSave(), AUTO_SAVE_MS);
  },
  beforeUnmount() {
    window.removeEventListener('beforeunload', this.onBeforeUnload);
    window.clearInterval(this.autoSaveTimer);
  },
  // 사이드바·뒤로가기로 나가면 입력한 위저드 내용이 통째로 사라졌다 — 저장 안 한 변경이 있을 때만 묻는다
  beforeRouteLeave(to, from, next) {
    if (this.skipLeaveGuard || !this.isDirty()) {
      next();
      return;
    }
    // 임시저장(자동 저장 포함)된 강좌가 있으면 그것을 지우고 나갈지도 고르게 한다 — 버려진 임시저장이 쌓였다
    const draftId = this.draftCourseId;
    this.$q
      .dialog({
        title: '페이지 나가기',
        message: draftId
          ? '마지막 저장 뒤에 바꾼 내용은 사라집니다. 임시저장된 강좌는 어떻게 할까요?'
          : '작성 중인 내용이 저장되지 않았습니다. 나가시겠어요?',
        ...(draftId
          ? {
              options: {
                type: 'radio',
                model: 'keep',
                items: [
                  { label: '임시저장은 남겨 두고 나가기 (내 강의 관리에서 이어서 작성)', value: 'keep' },
                  { label: '임시저장 강좌를 지우고 나가기', value: 'delete', color: 'negative' },
                ],
              },
            }
          : {}),
        ok: { unelevated: true, noCaps: true, color: 'primary', label: '나가기' },
        cancel: { flat: true, noCaps: true, label: '계속 작성' },
        persistent: true,
      })
      .onOk(async (choice) => {
        if (draftId && choice === 'delete') await this.deleteDraftOnLeave(draftId);
        next();
      })
      .onCancel(() => next(false));
  },
  methods: {
    /** 화면에 보이는 입력 전체 + 담당 강사 선택 — 파일 객체는 직렬화되지 않지만 목록 길이·이름 변화는 잡힌다 */
    formSnapshot() {
      return JSON.stringify({ form: this.form, instructorId: this.selectedInstructorId });
    },
    /**
     * 나가면서 임시저장 강좌 지우기 — 409(이미 게시됨)·404(이미 없음)는 지울 게 없으니 조용히 나간다.
     * 그 밖의 실패는 알리고 나간다(내 강의 관리에서 다시 지울 수 있다)
     */
    async deleteDraftOnLeave(courseId) {
      try {
        await deleteDraftCourse(courseId);
        this.$q.notify({ type: 'positive', message: '임시저장 강좌를 지웠습니다.', position: 'top' });
      } catch (error) {
        if (error?.status === 409 || error?.status === 404) return;
        this.$q.notify({
          type: 'negative',
          message: `${deleteErrorMessage(error)} 내 강의 관리에서 다시 지울 수 있습니다.`,
          position: 'top',
        });
      }
    },
    /** 자동 저장 — 새 강의 등록에서, 바뀐 게 있고 기관·제목이 있을 때만 조용히(임시저장과 같은 강좌로) */
    autoSave() {
      if (this.isEditMode || this.saving || !this.isDirty()) return;
      // 1단계 필수(기관·분류·제목·설명·수강 대상)가 다 찬 뒤부터 — 강의명만 써 보고 넘겨도 서버에 강좌가 생겼다
      if (Object.keys(this.stepErrors(1)).length) return;
      this.saveDraft({ quiet: true });
    },
    markSaved() {
      this.savedSnapshot = this.formSnapshot();
    },
    isDirty() {
      return this.formSnapshot() !== this.savedSnapshot;
    },
    // 새로고침·탭 닫기는 브라우저 기본 확인창만 띄울 수 있다(문구 지정 불가)
    onBeforeUnload(event) {
      if (this.skipLeaveGuard || !this.isDirty()) return;
      event.preventDefault();
      event.returnValue = '';
    },
    async loadAllInstitutions() {
      try {
        const result = await listInstitutions({ page: 0, size: 100, status: 'ACTIVE' });
        this.allInstitutionOptions = toInstitutionOptions(result.data);
      } catch (error) {
        this.allInstitutionOptions = [];
        this.$q.notify({ type: 'negative', message: error?.message || '기관 목록을 불러오지 못했습니다.' });
      }
    },
    /**
     * 임시저장한 강의를 이어서 작성하는 경로 — course.detail 을 form 으로 역매핑하고
     * draftCourseId 를 세팅해 이후 모든 저장을 PUT 으로 돌린다.
     * DRAFT 가 아닌 강좌는 위저드가 detail 을 통째로 덮어써 공개 상태가 꼬이므로 콘솔로 돌려보낸다.
     */
    async loadForEdit(courseId) {
      let course;
      try {
        course = await getCourse(courseId);
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '강의 정보를 불러오지 못했습니다.',
          position: 'top',
        });
        this.$router.replace('/instructor/courses');
        return;
      }
      if (course.status !== 'DRAFT') {
        this.$q.notify({
          type: 'info',
          message: '이미 공개된 강의는 강의 상세에서 관리해 주세요.',
          position: 'top',
        });
        this.$router.replace(`/instructor/courses/${courseId}`);
        return;
      }
      try {
        const [sessionsPage, attachments, instructors] = await Promise.all([
          listCourseSessions(courseId, { sort: 'sessionNo,asc' }),
          listCourseAttachments(courseId),
          listCourseInstructors(courseId).catch(() => []),
        ]);
        this.applyCourseToForm(course);
        this.form.sessions = (sessionsPage.data || []).map((s) => {
          const { date, time: startTime } = isoToKstParts(s.startsAt);
          const { time: endTime } = isoToKstParts(s.endsAt);
          return {
            id: s.id,
            sessionNo: s.sessionNo,
            date,
            startTime,
            endTime,
            topic: s.topic || '',
            videoFile: null,
            videoDurationSec: null,
          };
        });
        this.form.attachments = (attachments || []).map((a) => ({
          attachmentId: a.id,
          fileId: a.fileId,
          name: a.fileName,
          size: a.byteSize,
        }));
        const main = instructors.find((item) => item.role === 'MAIN');
        if (main) {
          // 위저드는 MAIN 만 배정한다 — MAIN 이 이미 있으면 다시 호출하지 않는다(과정당 1명, 중복 시 409)
          this.instructorAssigned = true;
          this.selectedInstructorId = main.userId;
        }
        this.draftCourseId = courseId;
        this.draftSavedAt = course.updatedAt;
        this.loadInstitutionName();
        // 불러온 값이 기준이다 — 열어 보기만 하고 나갈 때 묻지 않게
        this.$nextTick(this.markSaved);
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '강의 정보를 불러오지 못했습니다.',
          position: 'top',
        });
        this.$router.replace('/instructor/courses');
      }
    },
    /** Course + CourseDetail → 위저드 form. buildCourseDetail 의 역방향(course-detail.js 참고) */
    applyCourseToForm(course) {
      const f = this.form;
      f.institutionId = course.institutionId;
      f.classificationCode = course.classificationCode || null;
      f.classificationPath = Array.isArray(course.classificationPath) ? course.classificationPath : [];
      f.title = course.title || '';
      f.description = course.description || '';
      f.educationType = course.educationType || 'OFFLINE';
      f.visibility = course.status;
      f.startDate = course.startDate ?? '';
      f.endDate = course.endDate ?? '';
      f.recruitStartDate = course.recruitStartDate ?? '';
      f.recruitEndDate = course.recruitEndDate ?? '';
      f.capacity = course.capacity ?? null;
      f.location = course.location || '';
      f.thumbnail = course.thumbnailFileId
        ? {
            id: course.thumbnailFileId,
            url: storedFileContentUrl(course.thumbnailFileId),
          }
        : null;
      Object.assign(f, parseCourseDetail(course.detail));
    },
    /** 미리보기 제목 아래 기관명 — 실패해도 줄만 비므로 조용히 넘긴다 */
    async loadInstitutionName() {
      const institutionId = this.courseInstitutionId;
      if (!institutionId) return;
      try {
        const institution = await getInstitution(institutionId);
        this.institutionName = institution?.name || '';
        this.institutionRegion = institution?.region || '';
      } catch {
        this.institutionName = '';
        this.institutionRegion = '';
      }
    },
    /* ---------- 담당 강사 (관리자 대행 등록) ---------- */
    async loadInstructorOptions() {
      const institutionId = this.courseInstitutionId;
      if (!institutionId) return;
      this.loadingInstructors = true;
      try {
        const page = await listInstitutionInstructors(institutionId, { status: 'ACTIVE' });
        this.instructorOptions = (page.data || []).map((row) => ({
          label: row.email ? `${row.name} (${row.email})` : row.name,
          value: row.userId,
        }));
      } catch {
        // 목록이 비어도 등록 자체는 막지 않는다 — 안내만 보이고 선택 없이 진행
        this.instructorOptions = [];
      } finally {
        this.loadingInstructors = false;
      }
    },
    /* ---------- 대표 이미지 ---------- */
    async onThumbnailChange(event) {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      if (!['image/jpeg', 'image/png'].includes(file.type)) {
        this.$q.notify({ type: 'warning', message: 'JPG 또는 PNG 이미지만 올릴 수 있습니다.' });
        return;
      }
      if (file.size > MAX_THUMBNAIL_BYTES) {
        this.$q.notify({ type: 'warning', message: '이미지 용량은 5MB 이하여야 합니다.' });
        return;
      }
      this.uploading = true;
      try {
        const { id, url } = await uploadFile(file, 'COURSE_THUMBNAIL');
        this.form.thumbnail = { id, url };
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '이미지 업로드에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.uploading = false;
      }
    },

    /* ---------- 첨부파일 ---------- */
    // 선택 시점에 업로드까지 끝낸다. 강좌가 아직 없어 연결(attachCourseFile)은 submit()에서 한다.
    async onAttachmentsChange(event) {
      const { accepted, rejected } = pickAttachments(event.target.files);
      event.target.value = '';
      if (rejected.length) {
        this.$q.notify({ type: 'warning', message: `첨부 제외 — ${rejected.join(', ')}` });
      }
      if (!accepted.length) return;
      this.attaching = true;
      this.attachDone = 0;
      this.attachTotal = accepted.length;
      try {
        for (const file of accepted) {
          try {
            const { id } = await uploadFile(file, 'COURSE_ATTACHMENT');
            this.form.attachments.push({ fileId: id, name: file.name, size: file.size });
          } catch (error) {
            this.$q.notify({
              type: 'negative',
              message: `${file.name} 업로드 실패: ${error.message || ''}`.trim(),
            });
          }
          this.attachDone += 1;
        }
      } finally {
        this.attaching = false;
        this.attachDone = 0;
        this.attachTotal = 0;
      }
    },
    /** 첨부 업로드 중이면 안내하고 true — 도중에 등록하면 올라가던 파일이 빠졌다 */
    blockWhileAttaching() {
      if (!this.attaching) return false;
      this.$q.notify({ type: 'warning', message: '첨부파일 업로드가 끝난 뒤 진행해 주세요.' });
      return true;
    },

    /** 첨부도 확인 없이 지워졌다 — 회차와 같이 [되돌리기] */
    removeAttachment(index) {
      const removed = this.form.attachments[index];
      if (!removed) return;
      this.form.attachments = this.form.attachments.filter((_, i) => i !== index);
      this.$q.notify({
        message: `${removed.name} 첨부를 지웠습니다.`,
        // 아래에 띄우면 하단 [다음 단계]를 가렸다
        position: 'top',
        timeout: UNDO_TIMEOUT_MS,
        actions: [
          {
            label: '되돌리기',
            color: 'yellow',
            handler: () => {
              const next = [...this.form.attachments];
              next.splice(Math.min(index, next.length), 0, removed);
              this.form.attachments = next;
            },
          },
        ],
      });
    },
  },
};
</script>

<style lang="scss" src="@/components/course-wizard/course-wizard.scss"></style>
<style lang="scss" src="@/components/course-wizard/course-wizard-steps.scss"></style>
