<template>
  <q-page padding>
    <q-btn flat dense color="primary" icon="arrow_back" label="돌아가기" class="q-mb-md" @click="goBack" />
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">강의 강사 배정</div>
        <div class="app-page-header__subtitle text-grey-7">강의를 맡을 강사를 배정합니다. 주강사는 한 명만 둘 수 있습니다.</div>
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
          <q-badge :color="props.value === 'MAIN' ? 'primary' : 'grey'">{{ ROLE_LABEL[props.value] || props.value }}</q-badge>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn
            flat
            dense
            color="primary"
            :label="props.row.role === 'MAIN' ? '보조 강사로' : '주강사로'"
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
            :hint="instructorOptionsError ? '' : '이 강의 기관의 소속 강사'"
            :error="!!instructorOptionsError"
            :error-message="instructorOptionsError"
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
import { getCourse } from '@/services/course-api';
import { listInstitutionInstructors } from '@/services/institution-api';

// 기관 소속 강사 목록 API 의 size 상한
const INSTRUCTOR_OPTION_LIMIT = 100;

// 과정 강사 역할(CourseInstructor.role)
const ROLE_LABEL = { MAIN: '주강사', ASSISTANT: '보조 강사' };

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
      instructorOptionsError: '',
      ROLE_LABEL,
      roleOptions: Object.entries(ROLE_LABEL).map(([value, label]) => ({ label, value })),
      columns: [
        { name: 'instructorName', label: '이름', field: (r) => r.instructorName || '-', align: 'left' },
        { name: 'instructorEmail', label: '이메일', field: (r) => r.instructorEmail || '-', align: 'left' },
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
    /* 플랫폼 관리자 전용 GET /users·/users/{id}/roles 를 불러 기관 관리자에게 403 빈 목록이었다.
       강의 기관의 소속 강사 명단(GET /institutions/{id}/instructors)은 두 콘솔 모두 쓸 수 있다 */
    async loadInstructorOptions() {
      this.loadingInstructors = true;
      this.instructorOptionsError = '';
      try {
        const course = await getCourse(this.courseId);
        if (!course?.institutionId) throw new Error('강의의 기관 정보를 찾지 못했습니다.');
        const page = await listInstitutionInstructors(course.institutionId, {
          page: 0,
          size: INSTRUCTOR_OPTION_LIMIT,
          status: 'ACTIVE',
          sort: 'name,asc',
        });
        this.instructorOptions = (page.data || []).map((row) => ({
          label: `${row.name || '-'} (${row.email || row.userId})`,
          value: row.userId,
        }));
      } catch (error) {
        this.instructorOptions = [];
        this.instructorOptionsError = `강사 목록을 불러오지 못했습니다. ${error.message || ''}`.trim();
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
