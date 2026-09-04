<template>
  <q-page padding>
    <div class="app-page-header row justify-between items-center q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">전체 강의 관리</div>
        <div class="app-page-header__subtitle text-grey-7">GET /courses · 배정된 강사 과정은 강사 목록으로 표시</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" :loading="loading" label="새로고침" @click="loadRows" />
        <q-btn color="primary" icon="add" label="강의 등록" @click="$router.push('/instructor/courses/create')" />
      </div>
    </div>
    <div class="row q-col-gutter-md q-mb-lg">
      <div v-for="item in stats" :key="item.label" class="col-6 col-md-3">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-grey-7">{{ item.label }}</div>
            <div class="text-h5 text-weight-bold">{{ item.value }}</div>
          </q-card-section>
        </q-card>
      </div>
    </div>
    <AppErrorBanner :message="errorMessage" />
    <q-table flat bordered :rows="rows" :columns="columns" row-key="id" :loading="loading">
      <template #body-cell-status="props">
        <q-td :props="props"><q-badge color="primary">{{ props.value }}</q-badge></q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="primary" label="회차" @click="$router.push(`/instructor/courses/${props.row.id}/sessions`)" />
          <q-btn flat dense color="primary" label="수강생" @click="$router.push(`/instructor/courses/${props.row.id}/enrollments`)" />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import { qFormatDateKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { mapState } from 'pinia';
import { listCourses } from '@/services/course-api';
import { listCourseInstructors } from '@/services/instructor-api';
import { useAuthStore } from '@/stores/auth-store';

export default {
  components: { AppErrorBanner },
  name: 'InstructorCoursesPage',
  data() {
    return {
      allRows: [],
      loading: false,
      errorMessage: '',
      columns: [
        { name: 'title', label: '강의명', field: 'title', align: 'left' },
        { name: 'category', label: '카테고리', field: 'category' },
        { name: 'instructorRole', label: '배정', field: 'instructorRole' },
        { name: 'startDate', label: '시작', field: 'startDate', format: qFormatDateKst },
        { name: 'endDate', label: '종료', field: 'endDate', format: qFormatDateKst },
        { name: 'status', label: '상태', field: 'status' },
        { name: 'actions', label: '관리', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['userId', 'isAdmin']),
    rows() {
      if (this.isAdmin) return this.allRows;
      return this.allRows.filter((row) => row.instructorRole);
    },
    stats() {
      const list = this.rows;
      return [
        { label: '전체 강의', value: list.length },
        { label: '모집중', value: list.filter((r) => r.status === 'RECRUITING').length },
        { label: '진행중', value: list.filter((r) => r.status === 'IN_PROGRESS').length },
        { label: '종료', value: list.filter((r) => r.status === 'COMPLETED').length },
      ];
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
        const page = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        const courses = page.data || [];
        const withRoles = await Promise.all(
          courses.map(async (course) => {
            try {
              const instructors = await listCourseInstructors(course.id);
              const mine = (instructors || []).find((item) => item.userId === this.userId);
              return { ...course, instructorRole: mine?.role || '' };
            } catch {
              return { ...course, instructorRole: '' };
            }
          }),
        );
        this.allRows = withRoles;
      } catch (error) {
        this.allRows = [];
        this.errorMessage = error.message || '강의 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
