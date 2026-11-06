<template>
  <q-page padding>
    <AppPageHeader
      title="이수 처리"
      show-refresh
      :loading="coursesLoading"
      @refresh="reloadAll"
    />

    <AppErrorBanner :message="errorMessage" />

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
        <div v-else-if="!coursesLoading && !courseOptions.length" class="field-hint">
          {{
            consoleAsAdmin
              ? '등록된 강의가 없습니다.'
              : '배정된 강의가 없습니다. 기관 관리자가 강의에 강사로 배정하면 여기에 나타납니다.'
          }}
        </div>
      </div>
    </div>

    <!-- 강사는 수료증 발급·확정 취소 권한이 없다 — 평가·확정만 -->
    <CompletionRoster
      ref="roster"
      :course-id="selectedCourseId"
      :course-title="selectedCourseTitle"
      :criteria-text="selectedCriteria"
      @busy-change="rosterBusy = $event"
      @error="errorMessage = $event"
    />
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import CompletionRoster from '@/components/completion/CompletionRoster.vue';
import instructorConsoleCourses from '@/mixins/instructor-console-courses';
import { completionCriteriaText, filterCompletionCourses, pickCompletionCourseId } from '@/utils/completion-course';
import { formatCourseOption } from '@/utils/status';

export default {
  name: 'InstructorCompletionPage',
  components: { AppErrorBanner, AppPageHeader, CompletionRoster },
  mixins: [instructorConsoleCourses],
  data() {
    return {
      courses: [],
      selectedCourseId: null,
      coursesLoading: false,
      rosterBusy: false,
      errorMessage: '',
    };
  },
  computed: {
    courseOptions() {
      // 출결 화면과 같은 '강의명 · 코드' — 코드가 앞이라 강의명이 밀렸다
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
      try {
        const result = await this.fetchConsoleCourses({ page: 0, size: 50 });
        // 작성 중·취소 강의는 이수할 수강생이 없다 — 목록에서 뺀다
        this.courses = filterCompletionCourses(Array.isArray(result.data) ? result.data : []);
        const stillListed = this.courses.some((course) => course.id === this.selectedCourseId);
        // 메뉴·강의 상세에서 ?courseId 로 넘어오면 그 과정을 연다
        // 처음엔 할 일이 있을 법한 강의부터(수업이 끝났는데 종료 전 → 진행 중) — 최근에 만든 임시저장 강의가 먼저 열렸다
        if (!stillListed) this.selectedCourseId = pickCompletionCourseId(this.courses, this.$route.query.courseId);
      } catch (error) {
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
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
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
