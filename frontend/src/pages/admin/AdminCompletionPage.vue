<template>
  <!-- 다른 관리자 화면과 같은 여백 — 위가 좁고 폰 좌우가 8px 이었다 -->
  <q-page class="completion-page">
    <AppPageHeader
      :title="consoleScope === 'institution' ? '수료증 발급' : '이수 처리'"
      :subtitle="consoleScope === 'institution' ? '출석 평가·이수 확정 뒤 수료증(VC)을 발급합니다. 강사가 확정한 건은 바로 발급할 수 있습니다.' : ''"
      show-refresh
      :loading="coursesLoading"
      @refresh="reloadAll"
    />

    <AppInstitutionMissingBanner
      :show="consoleScope === 'institution' && showInstitutionMissingBanner"
    />
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="reloadAll" />
      </template>
    </AppErrorBanner>

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-6">
        <q-select
          :model-value="selectedCourseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseOptions"
          label="강의 선택"
          :loading="coursesLoading"
          :disable="rosterBusy"
          @update:model-value="onCourseChange"
        />
        <!-- 처리 중엔 강의를 바꿀 수 없다 — 꺼진 이유를 글자로 -->
        <div v-if="rosterBusy" class="field-hint">처리가 끝나면 강의를 바꿀 수 있습니다.</div>
        <div v-else-if="!coursesLoading && !coursesError && !courseOptions.length" class="field-hint">
          등록된 강의가 없습니다.
        </div>
        <div v-else-if="coursesCapped" class="field-hint">최근 강의 {{ COURSE_PAGE_SIZE }}개만 고를 수 있습니다.</div>
      </div>
    </div>

    <CompletionRoster
      ref="roster"
      :course-id="selectedCourseId"
      :course-title="selectedCourseTitle"
      :criteria-text="selectedCriteria"
      can-issue
      :can-revert="canRevert"
      @busy-change="rosterBusy = $event"
      @error="errorMessage = $event"
    />
  </q-page>
</template>

<script>
import { completionCriteriaText, filterCompletionCourses, pickCompletionCourseId } from '@/utils/completion-course';
import { formatCourseOption } from '@/utils/status';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppInstitutionMissingBanner from '@/components/AppInstitutionMissingBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CompletionRoster from '@/components/completion/CompletionRoster.vue';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';

const COURSE_PAGE_SIZE = 50;

export default {
  name: 'AdminCompletionPage',
  components: { AppErrorBanner, AppInstitutionMissingBanner, AppPageHeader, CompletionRoster },
  // 관리자·기관 콘솔이 같이 쓴다 — 과정 목록 범위는 라우트 meta.consoleScope.
  // 역할 나눔: 강사는 출석 평가·이수 확정, 기관은 확정된 수강생에게 수료증(VC) 발급, 플랫폼 관리자는 전부
  mixins: [instructorConsoleCourses],
  data() {
    return {
      courses: [],
      selectedCourseId: null,
      coursesLoading: false,
      rosterBusy: false,
      errorMessage: '',
      coursesError: false,
      coursesCapped: false,
      COURSE_PAGE_SIZE,
    };
  },
  computed: {
    /** 확정 취소(POST …/completion/revert)는 플랫폼 관리자만 — 기관 관리자에겐 버튼을 그리지 않는다 */
    canRevert() {
      return this.consoleAsAdmin;
    },
    courseOptions() {
      return this.courses.map((c) => ({ label: formatCourseOption(c), value: c.id }));
    },
    selectedCriteria() {
      return completionCriteriaText(this.courses.find((course) => course.id === this.selectedCourseId));
    },
    selectedCourseTitle() {
      return this.courses.find((course) => course.id === this.selectedCourseId)?.title || '';
    },
  },
  watch: {
    // 기관 콘솔: 기관 선택기로 기관을 바꾸면 그 기관 과정으로 다시 읽는다
    institutionId(next, prev) {
      if (this.consoleScope !== 'institution' || next === prev) return;
      this.selectedCourseId = null;
      this.loadCourses();
    },
    // 같은 화면에서 다른 과정 링크로 들어오면(?courseId 만 바뀜) 그 과정으로 맞춘다
    '$route.query.courseId'(courseId) {
      if (
        courseId &&
        courseId !== this.selectedCourseId &&
        this.courses.some((c) => c.id === courseId)
      ) {
        this.selectedCourseId = courseId;
      }
    },
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    async reloadAll() {
      const before = this.selectedCourseId;
      await this.loadCourses();
      // 과정이 그대로면 명단은 저절로 다시 읽히지 않는다 — 직접 다시 읽는다
      if (before && before === this.selectedCourseId) this.$refs.roster?.reload();
    },
    async loadCourses() {
      this.coursesLoading = true;
      this.errorMessage = '';
      this.coursesError = false;
      try {
        const result = await this.fetchConsoleCourses({ page: 0, size: COURSE_PAGE_SIZE });
        // 작성 중·취소 강의는 이수할 수강생이 없다 — 목록에서 뺀다
        this.courses = filterCompletionCourses(Array.isArray(result.data) ? result.data : []);
        this.coursesCapped = (result.page?.totalElements || 0) > COURSE_PAGE_SIZE;
        const stillListed = this.courses.some((course) => course.id === this.selectedCourseId);
        // 메뉴·강의 상세에서 ?courseId 로 넘어오면 그 과정을 연다(예전엔 첫 과정으로 초기화됐다)
        // 처음엔 할 일이 있을 법한 강의부터(수업이 끝났는데 종료 전 → 진행 중) — 최근에 만든 임시저장 강의가 먼저 열렸다
        if (!stillListed) this.selectedCourseId = pickCompletionCourseId(this.courses, this.$route.query.courseId);
      } catch (error) {
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
        this.coursesError = true;
        this.courses = [];
        this.selectedCourseId = null;
      } finally {
        this.coursesLoading = false;
      }
    },
    onCourseChange(courseId) {
      this.errorMessage = '';
      this.selectedCourseId = courseId;
      this.syncCourseQuery(courseId);
    },
  },
};
</script>

<style scoped>
.completion-page {
  padding: 48px 40px 32px;
}

@media (max-width: 1023px) {
  .completion-page {
    padding: 24px 16px;
  }
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
