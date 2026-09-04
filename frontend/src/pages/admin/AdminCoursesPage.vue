<template>
  <q-page padding>
    <AppPageHeader title="강좌 관리" subtitle="GET/POST /courses · publish/close · 실제 API 연동">
      <template #actions>
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadCourses" />
        <q-btn color="primary" unelevated icon="add" label="강좌 등록" @click="openCreate" />
      </template>
    </AppPageHeader>

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
      <template #body-cell-educationType="props">
        <q-td :props="props">
          <EducationTypeBadge :type="props.row.educationType" />
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="primary" label="세션" @click="$router.push(`/admin/courses/${props.row.id}/sessions`)" />
          <q-btn flat dense color="primary" label="수강생" @click="$router.push(`/admin/courses/${props.row.id}/enrollments`)" />
          <q-btn flat dense color="primary" label="강사" @click="$router.push(`/admin/courses/${props.row.id}/instructors`)" />
          <q-btn flat dense color="secondary" label="수정" @click="openEdit(props.row)" />
          <q-btn flat dense color="positive" label="게시" :loading="actionId === props.row.id" @click="doPublish(props.row)" />
          <q-btn flat dense color="negative" label="마감" :loading="actionId === props.row.id" @click="doClose(props.row)" />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showCreate">
      <q-card style="min-width: 440px; max-width: 520px">
        <q-card-section class="text-h6">{{ editingId ? '강좌 수정' : '강좌 등록' }}</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="form.courseCode" outlined dense label="courseCode *" />
          <q-input v-model="form.title" outlined dense label="title *" />
          <q-input v-model="form.description" outlined dense type="textarea" autogrow label="description" />
          <q-input v-model="form.category" outlined dense label="category" />
          <q-select
            :model-value="form.educationType"
            outlined
            dense
            emit-value
            map-options
            :options="eduOptions"
            label="교육 유형"
            @update:model-value="setEduType"
          />
          <q-input v-model.number="form.capacity" outlined dense type="number" label="capacity" />
          <q-input v-model="form.onlineUrl" outlined dense label="onlineUrl" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="saving" :label="editingId ? '수정' : '저장'" @click="doSave" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import AppPageHeader from '@/components/AppPageHeader.vue';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { EDUCATION_TYPE_OPTIONS } from '@/utils/status';
import { DEFAULT_INSTITUTION_ID } from '@/config/api';
import {
  closeCourse,
  createCourse,
  listCourses,
  publishCourse,
  updateCourse,
} from '@/services/course-api';

function defaultDates() {
  const today = new Date();
  const fmt = (d) => d.toISOString().slice(0, 10);
  const addDays = (n) => {
    const d = new Date(today);
    d.setDate(d.getDate() + n);
    return d;
  };
  return {
    recruitStartDate: fmt(today),
    recruitEndDate: fmt(addDays(30)),
    startDate: fmt(addDays(35)),
    endDate: fmt(addDays(95)),
  };
}

function emptyForm() {
  return {
    courseCode: '',
    title: '',
    description: '',
    category: 'IT/디지털',
    educationType: 'ONLINE',
    capacity: 30,
    onlineUrl: '',
  };
}

export default {
  name: 'AdminCoursesPage',
  components: { AppErrorBanner, AppPageHeader, EducationTypeBadge, StatusBadge },
  data() {
    return {
      rows: [],
      loading: false,
      saving: false,
      actionId: null,
      errorMessage: '',
      showCreate: false,
      editingId: null,
      form: emptyForm(),
      eduOptions: EDUCATION_TYPE_OPTIONS,
      columns: [
        { name: 'courseCode', label: '코드', field: 'courseCode', align: 'left' },
        { name: 'title', label: '강좌명', field: 'title', align: 'left' },
        { name: 'educationType', label: '유형', field: 'educationType', align: 'center' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'capacity', label: '정원', field: 'capacity', align: 'center' },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  mounted() {
    this.loadCourses();
  },
  methods: {
    setEduType(value) {
      this.form.educationType = value;
    },
    openCreate() {
      this.editingId = null;
      this.form = emptyForm();
      this.showCreate = true;
    },
    openEdit(row) {
      this.editingId = row.id;
      this.form = {
        courseCode: row.courseCode || '',
        title: row.title || '',
        description: row.description || '',
        category: row.category || '',
        educationType: row.educationType || 'ONLINE',
        capacity: row.capacity || 30,
        onlineUrl: row.onlineUrl || '',
        startDate: row.startDate,
        endDate: row.endDate,
        recruitStartDate: row.recruitStartDate,
        recruitEndDate: row.recruitEndDate,
        location: row.location || '',
      };
      this.showCreate = true;
    },
    async doSave() {
      if (this.editingId) {
        await this.doUpdate();
        return;
      }
      await this.doCreate();
    },
    async loadCourses() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.errorMessage = error.message || '강좌 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async doCreate() {
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '코드/제목을 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        const dates = defaultDates();
        await createCourse({
          institutionId: DEFAULT_INSTITUTION_ID,
          courseCode: this.form.courseCode.trim(),
          title: this.form.title.trim(),
          description: this.form.description || null,
          category: this.form.category || null,
          educationType: this.form.educationType || 'ONLINE',
          startDate: dates.startDate,
          endDate: dates.endDate,
          recruitStartDate: dates.recruitStartDate,
          recruitEndDate: dates.recruitEndDate,
          capacity: Number(this.form.capacity) || 30,
          location: this.form.educationType === 'ONLINE' ? null : '미정',
          onlineUrl: this.form.onlineUrl || null,
          creditBankEligible: false,
          creditValue: null,
        });
        this.showCreate = false;
        this.$q.notify({ type: 'positive', message: '강좌가 등록되었습니다.', position: 'top' });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async doUpdate() {
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '코드/제목을 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await updateCourse(this.editingId, {
          courseCode: this.form.courseCode.trim(),
          title: this.form.title.trim(),
          description: this.form.description || null,
          category: this.form.category || null,
          educationType: this.form.educationType || 'ONLINE',
          startDate: this.form.startDate,
          endDate: this.form.endDate,
          recruitStartDate: this.form.recruitStartDate || null,
          recruitEndDate: this.form.recruitEndDate || null,
          capacity: Number(this.form.capacity) || 30,
          location: this.form.location || null,
          onlineUrl: this.form.onlineUrl || null,
        });
        this.showCreate = false;
        this.editingId = null;
        this.$q.notify({ type: 'positive', message: '강좌를 수정했습니다.', position: 'top' });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
    async doPublish(row) {
      this.actionId = row.id;
      try {
        await publishCourse(row.id);
        this.$q.notify({ type: 'positive', message: '게시 처리되었습니다.', position: 'top' });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionId = null;
      }
    },
    async doClose(row) {
      this.actionId = row.id;
      try {
        await closeCourse(row.id);
        this.$q.notify({ type: 'info', message: '모집 마감 처리되었습니다.', position: 'top' });
        await this.loadCourses();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionId = null;
      }
    },
  },
};
</script>
