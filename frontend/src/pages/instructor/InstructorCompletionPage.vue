<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">이수 처리</div>
        <div class="app-page-header__subtitle text-grey-7">과정 → 승인 수강생 선택 · evaluate · confirm</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="reloadAll" />
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-5">
        <q-select
          :model-value="selectedCourseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseOptions"
          label="과정 선택"
          :loading="coursesLoading"
          @update:model-value="onCourseChange"
        />
      </div>
      <div class="col-12 col-md-5">
        <q-select
          :model-value="selectedEnrollmentId"
          outlined
          dense
          emit-value
          map-options
          :options="enrollmentOptions"
          label="수강신청 선택 (APPROVED)"
          :disable="!selectedCourseId"
          :loading="enrollmentsLoading"
          @update:model-value="onEnrollmentChange"
        />
      </div>
      <div class="col-12 col-md-2 flex items-center">
        <q-btn
          color="primary"
          unelevated
          class="full-width"
          label="이수 조회"
          :disable="!selectedEnrollmentId"
          :loading="loading"
          @click="loadCompletion"
        />
      </div>
    </div>

    <q-card flat bordered>
      <q-card-section class="row items-center justify-between">
        <div class="text-subtitle1 text-weight-bold">이수 정보</div>
        <div class="row q-gutter-sm">
          <q-btn
            color="primary"
            unelevated
            label="평가"
            :disable="!selectedEnrollmentId"
            :loading="actionLoading === 'evaluate'"
            @click="doEvaluate"
          />
          <q-btn
            color="positive"
            unelevated
            label="확정"
            :disable="!canConfirm"
            :loading="actionLoading === 'confirm'"
            @click="doConfirm"
          />
        </div>
      </q-card-section>
      <q-separator />
      <q-card-section v-if="!completion && !loading" class="text-grey-7">
        승인된 수강신청을 선택한 뒤 이수 조회를 실행하세요.
      </q-card-section>
      <q-markup-table v-else flat bordered>
        <tbody>
          <tr v-for="row in detailRows" :key="row.label">
            <td class="text-grey-7" style="width: 180px">{{ row.label }}</td>
            <td>
              <q-badge v-if="row.label === 'status'" :color="statusColor[row.value] || 'grey'">{{ row.value }}</q-badge>
              <span v-else>{{ row.value }}</span>
            </td>
          </tr>
        </tbody>
      </q-markup-table>
      <q-inner-loading :showing="loading" />
    </q-card>
  </q-page>
</template>

<script>
import { formatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { statusColor } from '@/utils/status';
import { listCourses } from '@/services/course-api';
import { enrichEnrollmentsWithUsers, listCourseEnrollments } from '@/services/enrollment-api';
import { getAttendanceSummary } from '@/services/attendance-api';
import { listCourseSessions } from '@/services/session-api';
import { confirmCompletion, evaluateCompletion, getCompletion } from '@/services/completion-api';

export default {
  components: { AppErrorBanner },
  name: 'InstructorCompletionPage',
  data() {
    return {
      statusColor,
      courses: [],
      enrollments: [],
      selectedCourseId: null,
      selectedEnrollmentId: null,
      completion: null,
      coursesLoading: false,
      enrollmentsLoading: false,
      loading: false,
      actionLoading: null,
      errorMessage: '',
    };
  },
  computed: {
    courseOptions() {
      return this.courses.map((c) => ({
        label: `${c.courseCode || ''} ${c.title || c.id}`.trim(),
        value: c.id,
      }));
    },
    enrollmentOptions() {
      return this.enrollments.map((e) => ({
        label: `${e.applicantLabel || e.userName || e.userId || e.id} · ${e.status || ''}`.trim(),
        value: e.id,
      }));
    },
    completionId() {
      return this.completion?.id || this.completion?.completionId || null;
    },
    canConfirm() {
      return !!this.selectedEnrollmentId && this.completion?.status === 'ELIGIBLE';
    },
    detailRows() {
      const c = this.completion;
      if (!c) return [];
      const rate = c.attendanceRate;
      const rateText =
        rate == null ? '-' : typeof rate === 'number' && rate <= 1 ? `${Math.round(rate * 100)}%` : `${rate}%`;
      return [
        { label: 'completionId', value: this.completionId || '-' },
        { label: 'enrollmentId', value: c.enrollmentId || this.selectedEnrollmentId || '-' },
        { label: 'status', value: c.status || '-' },
        { label: 'attendanceRate', value: rateText },
        { label: 'completedMinutes', value: c.completedMinutes ?? '-' },
        { label: 'evaluatedAt', value: formatDateTimeKst(c.evaluatedAt) },
        { label: 'completedAt', value: formatDateTimeKst(c.completedAt) },
        { label: 'confirmedAt', value: formatDateTimeKst(c.confirmedAt) },
      ];
    },
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    async reloadAll() {
      await this.loadCourses();
      if (this.selectedCourseId) await this.loadEnrollments(this.selectedCourseId);
      if (this.selectedEnrollmentId) await this.loadCompletion();
    },
    async loadCourses() {
      this.coursesLoading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.courses = Array.isArray(result.data) ? result.data : [];
        const queryCourseId = this.$route.query.courseId;
        const preferred =
          queryCourseId && this.courses.some((c) => c.id === queryCourseId)
            ? queryCourseId
            : this.selectedCourseId && this.courses.some((c) => c.id === this.selectedCourseId)
              ? this.selectedCourseId
              : this.courses[0]?.id || null;
        if (preferred) {
          await this.onCourseChange(preferred);
        }
      } catch (error) {
        this.errorMessage = error.message || '과정 목록 조회 실패';
        this.courses = [];
      } finally {
        this.coursesLoading = false;
      }
    },
    async onCourseChange(courseId) {
      this.selectedCourseId = courseId;
      this.selectedEnrollmentId = null;
      this.enrollments = [];
      this.completion = null;
      if (this.$route.query.courseId !== courseId) {
        this.$router.replace({ query: { ...this.$route.query, courseId } }).catch(() => {});
      }
      await this.loadEnrollments(courseId);
    },
    async loadEnrollments(courseId) {
      if (!courseId) return;
      this.enrollmentsLoading = true;
      try {
        const result = await listCourseEnrollments(courseId, { page: 0, size: 100 });
        const all = await enrichEnrollmentsWithUsers(Array.isArray(result.data) ? result.data : []);
        this.enrollments = all.filter((e) => e.status === 'APPROVED');
        if (this.enrollments.length) {
          await this.onEnrollmentChange(this.enrollments[0].id);
        }
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '수강신청 목록 조회 실패', position: 'top' });
        this.enrollments = [];
      } finally {
        this.enrollmentsLoading = false;
      }
    },
    async onEnrollmentChange(enrollmentId) {
      this.selectedEnrollmentId = enrollmentId;
      this.completion = null;
      if (enrollmentId) await this.loadCompletion();
    },
    async loadCompletion() {
      if (!this.selectedEnrollmentId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        this.completion = await getCompletion(this.selectedEnrollmentId);
      } catch (error) {
        this.completion = null;
        this.errorMessage = error.message || '이수 조회 실패';
      } finally {
        this.loading = false;
      }
    },
    async buildEvaluationBody() {
      let attendanceRate = 100;
      let completedMinutes = 0;
      try {
        const summary = await getAttendanceSummary(this.selectedEnrollmentId);
        if (summary?.attendanceRate != null) {
          const rate = Number(summary.attendanceRate);
          attendanceRate = Number.isFinite(rate) ? (rate <= 1 ? Math.round(rate * 100) : rate) : 100;
        }
        if (summary?.completedMinutes != null) {
          completedMinutes = Number(summary.completedMinutes) || 0;
        } else {
          const totalSessions = Number(summary?.totalSessions ?? summary?.total ?? 0);
          if (totalSessions > 0) {
            completedMinutes = totalSessions * 60;
          } else if (this.selectedCourseId) {
            try {
              const sessions = await listCourseSessions(this.selectedCourseId, { page: 0, size: 100 });
              const count = Array.isArray(sessions.data) ? sessions.data.length : 0;
              completedMinutes = count * 60;
            } catch {
              completedMinutes = 0;
            }
          }
        }
      } catch {
        attendanceRate = 100;
        completedMinutes = 0;
      }
      return { attendanceRate, completedMinutes };
    },
    async doEvaluate() {
      if (!this.selectedEnrollmentId) return;
      this.actionLoading = 'evaluate';
      try {
        const body = await this.buildEvaluationBody();
        this.completion = await evaluateCompletion(this.selectedEnrollmentId, body);
        this.$q.notify({
          type: 'positive',
          message: `이수 평가 완료 (출석률 ${body.attendanceRate}%, ${body.completedMinutes}분)`,
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionLoading = null;
      }
    },
    async doConfirm() {
      if (!this.canConfirm) {
        this.$q.notify({ type: 'warning', message: 'ELIGIBLE 상태에서만 확정할 수 있습니다.', position: 'top' });
        return;
      }
      this.actionLoading = 'confirm';
      try {
        this.completion = await confirmCompletion(this.selectedEnrollmentId);
        this.$q.notify({ type: 'positive', message: '이수가 확정되었습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionLoading = null;
      }
    },
  },
};
</script>
