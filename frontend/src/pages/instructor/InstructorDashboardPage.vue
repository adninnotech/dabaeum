<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">강사 홈</div>
        <div class="app-page-header__subtitle text-grey-7">배정 과정 기준으로 회차·수강생·출결·이수를 관리합니다</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadCourses" />
    </div>

    <q-banner dense class="bg-blue-1 q-mb-md">
      API상 강사 전용 과정 목록이 없어 전체 과정을 조회합니다. 권한이 없는 과정 작업은 403이 발생할 수 있습니다.
    </q-banner>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-md q-mb-md">
      <div v-for="card in kpiCards" :key="card.label" class="col-12 col-sm-4">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-caption text-grey-7">{{ card.label }}</div>
            <div class="text-h5 text-weight-bold">{{ card.value }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>

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
      <template #body-cell-educationType="props">
        <q-td :props="props">
          <EducationTypeBadge :type="props.row.educationType" />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="primary" label="회차" @click="goSessions(props.row.id)" />
          <q-btn flat dense color="primary" label="수강생" @click="goEnrollments(props.row.id)" />
          <q-btn flat dense color="secondary" label="출결" @click="goAttendance(props.row.id)" />
          <q-btn flat dense color="secondary" label="이수" @click="goCompletion(props.row.id)" />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import { formatDatePeriodKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listCourses } from '@/services/course-api';

export default {
  components: { AppErrorBanner, EducationTypeBadge, StatusBadge },
  name: 'InstructorDashboardPage',
  data() {
    return {
      loading: false,
      errorMessage: '',
      rows: [],
      columns: [
        { name: 'courseCode', label: '코드', field: 'courseCode', align: 'left' },
        { name: 'title', label: '강좌명', field: 'title', align: 'left' },
        { name: 'educationType', label: '유형', field: 'educationType', align: 'center' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        {
          name: 'period',
          label: '기간',
          field: (r) => formatDatePeriodKst(r.startDate, r.endDate),
          align: 'left',
        },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    kpiCards() {
      const recruiting = this.rows.filter((r) => r.status === 'RECRUITING').length;
      const inProgress = this.rows.filter((r) => r.status === 'IN_PROGRESS').length;
      return [
        { label: '조회된 과정', value: String(this.rows.length) },
        { label: '모집중', value: String(recruiting) },
        { label: '진행중', value: String(inProgress) },
      ];
    },
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    async loadCourses() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '과정 목록 조회 실패';
      } finally {
        this.loading = false;
      }
    },
    goSessions(courseId) {
      this.$router.push(`/instructor/courses/${courseId}/sessions`);
    },
    goEnrollments(courseId) {
      this.$router.push(`/instructor/courses/${courseId}/enrollments`);
    },
    goAttendance(courseId) {
      this.$router.push({ path: '/instructor/attendance', query: { courseId } });
    },
    goCompletion(courseId) {
      this.$router.push({ path: '/instructor/completion', query: { courseId } });
    },
  },
};
</script>
