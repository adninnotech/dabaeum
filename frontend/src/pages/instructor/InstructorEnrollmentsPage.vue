<template>
  <q-page padding>
    <q-btn
      flat
      dense
      color="primary"
      icon="arrow_back"
      label="강사 홈"
      class="q-mb-md"
      @click="$router.push('/instructor')"
    />
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">수강생 목록</div>
        <div class="app-page-header__subtitle text-grey-7">Course {{ courseId }} · 수강신청 현황 · 출결 요약</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadEnrollments" />
    </div>

    <AppErrorBanner :message="errorMessage" />

    <q-table
      flat
      bordered
      row-key="id"
      :rows="rows"
      :columns="columns"
      :loading="loading"
      :pagination="{ rowsPerPage: 10 }"
    >
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            flat
            dense
            color="primary"
            label="출결 요약"
            :loading="summaryLoadingId === props.row.id"
            @click="showSummary(props.row)"
          />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showSummaryDialog">
      <q-card style="min-width: 400px; max-width: 520px">
        <q-card-section class="text-h6">출결 요약</q-card-section>
        <q-card-section>
          <div class="text-caption text-grey-7 q-mb-sm">enrollmentId: {{ summaryEnrollmentId || '-' }}</div>
          <q-markup-table flat bordered dense>
            <tbody>
              <tr v-for="row in summaryRows" :key="row.label">
                <td class="text-grey-7" style="width: 160px">{{ row.label }}</td>
                <td>{{ row.value }}</td>
              </tr>
            </tbody>
          </q-markup-table>
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="닫기" v-close-popup />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { formatDateTimeFields } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { enrichEnrollmentsWithUsers, listCourseEnrollments } from '@/services/enrollment-api';
import { getAttendanceSummary } from '@/services/attendance-api';

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'InstructorEnrollmentsPage',
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      summaryLoadingId: null,
      showSummaryDialog: false,
      summaryEnrollmentId: null,
      summary: null,
      columns: [
        { name: 'applicantLabel', label: '신청자', field: 'applicantLabel', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'applicationType', label: '유형', field: 'applicationType', align: 'center' },
        {
          name: 'appliedAt',
          label: '신청일',
          field: (r) => formatDateTimeFields(r, 'appliedAt', 'createdAt'),
          align: 'left',
        },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    courseId() {
      return this.$route.params.courseId;
    },
    summaryRows() {
      const s = this.summary;
      if (!s) return [{ label: '결과', value: '-' }];
      const rate = s.attendanceRate;
      const rateText =
        rate == null ? '-' : typeof rate === 'number' && rate <= 1 ? `${Math.round(rate * 100)}%` : `${rate}%`;
      return [
        { label: 'attendanceRate', value: rateText },
        { label: 'presentCount', value: s.presentCount ?? s.present ?? '-' },
        { label: 'lateCount', value: s.lateCount ?? s.late ?? '-' },
        { label: 'absentCount', value: s.absentCount ?? s.absent ?? '-' },
        { label: 'excusedCount', value: s.excusedCount ?? s.excused ?? '-' },
        { label: 'totalSessions', value: s.totalSessions ?? s.total ?? '-' },
        { label: 'completedMinutes', value: s.completedMinutes ?? '-' },
      ];
    },
  },
  watch: {
    courseId: {
      immediate: true,
      handler() {
        this.loadEnrollments();
      },
    },
  },
  methods: {
    async loadEnrollments() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourseEnrollments(this.courseId, { page: 0, size: 100 });
        this.rows = await enrichEnrollmentsWithUsers(Array.isArray(result.data) ? result.data : []);
      } catch (error) {
        this.errorMessage = error.message || '수강신청 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async showSummary(row) {
      this.summaryLoadingId = row.id;
      try {
        const summary = await getAttendanceSummary(row.id);
        this.summary = summary;
        this.summaryEnrollmentId = row.id;
        this.showSummaryDialog = true;
        const rate = summary?.attendanceRate;
        const rateText =
          rate == null ? '-' : typeof rate === 'number' && rate <= 1 ? `${Math.round(rate * 100)}%` : `${rate}%`;
        this.$q.notify({
          type: 'info',
          message: `출결 요약 · 출석률 ${rateText}`,
          position: 'top',
        });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '출결 요약 조회 실패', position: 'top' });
      } finally {
        this.summaryLoadingId = null;
      }
    },
  },
};
</script>
