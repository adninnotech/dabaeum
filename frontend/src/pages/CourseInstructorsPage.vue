<template>
  <q-page padding>
    <q-btn flat dense color="primary" icon="arrow_back" label="돌아가기" class="q-mb-md" @click="goBack" />
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">과정 강사 배정</div>
        <div class="app-page-header__subtitle text-grey-7">GET/POST /courses/{id}/instructors · MAIN 1명</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadRows" />
        <q-btn color="primary" unelevated icon="person_add" label="강사 배정" @click="showAssign = true" />
      </div>
    </div>

    <AppErrorBanner :message="errorMessage" />

    <q-table flat bordered row-key="userId" :rows="rows" :columns="columns" :loading="loading">
      <template #body-cell-role="props">
        <q-td :props="props">
          <q-badge :color="props.value === 'MAIN' ? 'primary' : 'grey'">{{ props.value }}</q-badge>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            flat
            dense
            color="primary"
            :label="props.row.role === 'MAIN' ? '보조로' : '메인으로'"
            @click="toggleRole(props.row)"
          />
          <q-btn flat dense color="negative" label="해제" @click="remove(props.row)" />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showAssign">
      <q-card style="min-width: 420px">
        <q-card-section class="text-h6">강사 배정</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-select
            v-model="assignUserId"
            outlined
            dense
            emit-value
            map-options
            clearable
            :options="instructorOptions"
            :loading="loadingInstructors"
            label="강사 선택"
            hint="INSTRUCTOR 역할 사용자"
          />
          <q-input v-model="assignUserId" outlined dense label="또는 userId (UUID)" />
          <q-select
            v-model="assignRole"
            outlined
            dense
            emit-value
            map-options
            :options="roleOptions"
            label="역할"
          />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="saving" label="배정" @click="assign" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import {
  assignCourseInstructor,
  listCourseInstructors,
  removeCourseInstructor,
  updateCourseInstructor,
} from '@/services/instructor-api';
import { listUserRoles, listUsers } from '@/services/user-api';

export default {
  components: { AppErrorBanner },
  name: 'CourseInstructorsPage',
  data() {
    return {
      rows: [],
      loading: false,
      saving: false,
      errorMessage: '',
      showAssign: false,
      assignUserId: '',
      assignRole: 'ASSISTANT',
      instructorOptions: [],
      loadingInstructors: false,
      roleOptions: [
        { label: 'MAIN', value: 'MAIN' },
        { label: 'ASSISTANT', value: 'ASSISTANT' },
      ],
      columns: [
        { name: 'instructorName', label: '이름', field: (r) => r.instructorName || '-', align: 'left' },
        { name: 'instructorEmail', label: '이메일', field: (r) => r.instructorEmail || '-', align: 'left' },
        { name: 'userId', label: 'userId', field: 'userId', align: 'left' },
        { name: 'role', label: '역할', field: 'role', align: 'center' },
        { name: 'assignedAt', label: '배정일', field: 'assignedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'actions', label: '관리', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    courseId() {
      return this.$route.params.courseId;
    },
  },
  watch: {
    courseId: {
      immediate: true,
      handler() {
        this.loadRows();
      },
    },
    showAssign(value) {
      if (value) this.loadInstructorOptions();
    },
  },
  methods: {
    goBack() {
      if (this.$route.path.startsWith('/institution')) {
        this.$router.push('/institution/courses');
        return;
      }
      this.$router.push('/admin/courses');
    },
    async loadRows() {
      if (!this.courseId) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const list = await listCourseInstructors(this.courseId);
        this.rows = Array.isArray(list) ? list : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '강사 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadInstructorOptions() {
      this.loadingInstructors = true;
      try {
        const page = await listUsers({ page: 0, size: 50, status: 'ACTIVE', sort: 'name,asc' });
        const users = page.data || [];
        const options = [];
        await Promise.all(
          users.map(async (user) => {
            try {
              const roles = await listUserRoles(user.id);
              const isInstructor = (roles || []).some((item) => (item?.role || item) === 'INSTRUCTOR');
              if (isInstructor) {
                options.push({
                  label: `${user.name || '-'} (${user.email || user.id})`,
                  value: user.id,
                });
              }
            } catch {
              // 역할 조회 실패 사용자는 건너뜀
            }
          }),
        );
        options.sort((a, b) => a.label.localeCompare(b.label, 'ko'));
        this.instructorOptions = options;
      } catch {
        this.instructorOptions = [];
      } finally {
        this.loadingInstructors = false;
      }
    },
    async assign() {
      const userId = String(this.assignUserId || '').trim();
      if (!userId) {
        this.$q.notify({ type: 'warning', message: 'userId를 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await assignCourseInstructor(this.courseId, { userId, role: this.assignRole });
        this.showAssign = false;
        this.assignUserId = '';
        this.$q.notify({ type: 'positive', message: '강사를 배정했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async toggleRole(row) {
      const next = row.role === 'MAIN' ? 'ASSISTANT' : 'MAIN';
      try {
        await updateCourseInstructor(this.courseId, row.userId, { role: next });
        this.$q.notify({ type: 'positive', message: '역할을 변경했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async remove(row) {
      try {
        await removeCourseInstructor(this.courseId, row.userId);
        this.$q.notify({ type: 'info', message: '배정을 해제했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
  },
};
</script>
