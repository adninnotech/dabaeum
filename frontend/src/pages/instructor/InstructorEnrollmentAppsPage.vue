<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">수강 신청 관리</div>
        <div class="app-page-header__subtitle text-grey-7">과정별 수강신청 목록 · 승인/거절</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadRows" />
    </div>
    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-md q-mb-md">
      <div class="col-12 col-md-4">
        <q-select
          v-model="selectedCourseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseFilterOptions"
          label="강의"
        />
      </div>
    </div>

    <q-table flat bordered :rows="filteredRows" :columns="columns" row-key="id" :loading="loading">
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.value" />
        </q-td>
      </template>
      <template #body-cell-action="props">
        <q-td :props="props">
          <q-btn
            flat
            dense
            color="positive"
            label="승인"
            :disable="props.row.status === 'APPROVED'"
            @click="approve(props.row)"
          />
          <q-btn
            flat
            dense
            color="negative"
            label="거절"
            :disable="props.row.status === 'REJECTED'"
            @click="reject(props.row)"
          />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import { formatDateTimeFields } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listCourses } from '@/services/course-api';
import { approveEnrollment, enrichEnrollmentsWithUsers, listCourseEnrollments, rejectEnrollment } from '@/services/enrollment-api';
import { formatCourseOption } from '@/utils/status';

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'InstructorEnrollmentAppsPage',
  data() {
    return {
      loading: false,
      errorMessage: '',
      rows: [],
      courses: [],
      selectedCourseId: null,
      columns: [
        { name: 'applicantLabel', label: '신청자', field: 'applicantLabel', align: 'left' },
        { name: 'courseTitle', label: '강의명', field: 'courseTitle', align: 'left' },
        { name: 'status', label: '상태', field: 'status' },
        { name: 'appliedAt', label: '신청일', field: (r) => formatDateTimeFields(r, 'appliedAt', 'createdAt'), align: 'left' },
        { name: 'action', label: '관리', field: 'action' },
      ],
    };
  },
  computed: {
    courseFilterOptions() {
      return [
        { label: '전체', value: null },
        ...(this.courses || []).map((item) => ({
          label: formatCourseOption(item),
          value: item.id,
        })),
      ];
    },
    filteredRows() {
      if (!this.selectedCourseId) return this.rows;
      return this.rows.filter((row) => row.courseId === this.selectedCourseId);
    },
  },
  mounted() {
    this.loadRows();
  },
  methods: {
    async loadRows() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const courses = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.courses = courses.data || [];
        const chunks = await Promise.all(
          this.courses.map(async (course) => {
            try {
              const page = await listCourseEnrollments(course.id, { page: 0, size: 100 });
              return (page.data || []).map((row) => ({ ...row, courseTitle: course.title, courseId: course.id }));
            } catch {
              return [];
            }
          }),
        );
        this.rows = await enrichEnrollmentsWithUsers(chunks.flat());
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강 신청을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async approve(row) {
      try {
        await approveEnrollment(row.id);
        this.$q.notify({ type: 'positive', message: '승인했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async reject(row) {
      try {
        await rejectEnrollment(row.id, { reason: '강사 거절' });
        this.$q.notify({ type: 'info', message: '거절했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
  },
};
</script>
