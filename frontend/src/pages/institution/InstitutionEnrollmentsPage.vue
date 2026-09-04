<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">수강 관리</div>
        <div class="app-page-header__subtitle text-grey-7">소속 과정 수강신청 승인/거절</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" :loading="loading" label="새로고침" @click="loadRows" />
        <q-btn color="primary" unelevated icon="person_add" label="대리 수강신청" @click="openProxy" />
      </div>
    </div>
    <AppErrorBanner :message="errorMessage" />
    <q-table flat bordered :rows="rows" :columns="columns" row-key="id" :loading="loading">
      <template #body-cell-status="props">
        <q-td :props="props"><StatusBadge :status="props.value" /></q-td>
      </template>
      <template #body-cell-action="props">
        <q-td :props="props">
          <q-btn flat dense color="positive" label="승인" :disable="props.row.status === 'APPROVED'" @click="approve(props.row)" />
          <q-btn flat dense color="negative" label="거절" :disable="props.row.status === 'REJECTED'" @click="reject(props.row)" />
          <q-btn flat dense color="grey-8" label="취소" @click="cancel(props.row)" />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showProxy">
      <q-card style="min-width: 420px">
        <q-card-section class="text-h6">대리 수강신청</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-select
            v-model="proxyCourseId"
            outlined
            dense
            emit-value
            map-options
            :options="courseOptions"
            label="과정 *"
          />
          <q-input v-model="proxyUserId" outlined dense label="userId *" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn flat label="취소" v-close-popup />
          <q-btn color="primary" unelevated :loading="saving" label="신청" @click="proxyEnroll" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { formatDateTimeFields } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import institutionScope from '@/mixins/institution-scope';
import { listCourses } from '@/services/course-api';
import { approveEnrollment, cancelEnrollment, createProxyEnrollment, enrichEnrollmentsWithUsers, listCourseEnrollments, rejectEnrollment } from '@/services/enrollment-api';
import { formatCourseOption } from '@/utils/status';

export default {
  name: 'InstitutionEnrollmentsPage',
  components: { AppErrorBanner, StatusBadge },
  mixins: [institutionScope],
  data() {
    return {
      loading: false,
      saving: false,
      errorMessage: '',
      rows: [],
      courses: [],
      showProxy: false,
      proxyCourseId: null,
      proxyUserId: '',
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
    courseOptions() {
      return (this.courses || []).map((item) => ({
        label: formatCourseOption(item),
        value: item.id,
      }));
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
        const coursesPage = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        let courses = coursesPage.data || [];
        if (!this.isAdmin && this.primaryInstitutionId) {
          courses = courses.filter((item) => item.institutionId === this.primaryInstitutionId);
        }
        this.courses = courses;
        const chunks = await Promise.all(
          courses.map(async (course) => {
            try {
              const page = await listCourseEnrollments(course.id, { page: 0, size: 100 });
              return (page.data || []).map((row) => ({ ...row, courseTitle: course.title }));
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
        await rejectEnrollment(row.id, { reason: '기관 거절' });
        this.$q.notify({ type: 'info', message: '거절했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async cancel(row) {
      try {
        await cancelEnrollment(row.id);
        this.$q.notify({ type: 'info', message: '취소했습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    openProxy() {
      this.proxyCourseId = this.courses[0]?.id || null;
      this.proxyUserId = '';
      this.showProxy = true;
    },
    async proxyEnroll() {
      if (!this.proxyCourseId || !this.proxyUserId.trim()) {
        this.$q.notify({ type: 'warning', message: '과정과 userId를 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await createProxyEnrollment(this.proxyCourseId, { userId: this.proxyUserId.trim() });
        this.showProxy = false;
        this.$q.notify({ type: 'positive', message: '대리 수강신청이 완료되었습니다.', position: 'top' });
        await this.loadRows();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
