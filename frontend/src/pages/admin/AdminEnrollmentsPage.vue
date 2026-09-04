<template>
  <q-page padding>
    <q-btn flat dense color="primary" icon="arrow_back" label="강좌 목록" class="q-mb-md" @click="$router.push(coursesListPath)" />
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">수강 승인</div>
        <div class="app-page-header__subtitle text-grey-7">Course {{ courseId }} · approve / reject / proxy</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadEnrollments" />
        <q-btn color="primary" unelevated icon="person_add" label="대리 수강신청" @click="showProxy = true" />
      </div>
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
          <q-btn flat dense color="positive" label="승인" :disable="props.row.status === 'APPROVED'" @click="approve(props.row)" />
          <q-btn flat dense color="negative" label="거절" :disable="props.row.status === 'REJECTED'" @click="reject(props.row)" />
          <q-btn flat dense color="grey-8" label="취소" @click="cancel(props.row)" />
        </q-td>
      </template>
    </q-table>

    <q-dialog v-model="showProxy">
      <q-card style="min-width: 400px">
        <q-card-section class="text-h6">대리 수강신청</q-card-section>
        <q-card-section class="q-gutter-md">
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
import { DEFAULT_USER_ID } from '@/config/api';
import {
  approveEnrollment,
  cancelEnrollment,
  createProxyEnrollment,
  enrichEnrollmentsWithUsers,
  listCourseEnrollments,
  rejectEnrollment,
} from '@/services/enrollment-api';

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'AdminEnrollmentsPage',
  data() {
    return {
      rows: [],
      loading: false,
      saving: false,
      errorMessage: '',
      showProxy: false,
      proxyUserId: DEFAULT_USER_ID,
      columns: [
        { name: 'applicantLabel', label: '신청자', field: 'applicantLabel', align: 'left' },
        { name: 'applicationType', label: '유형', field: 'applicationType', align: 'center' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
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
    coursesListPath() {
      return this.$route.path.startsWith('/institution') ? '/institution/courses' : '/admin/courses';
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
    async approve(row) {
      try {
        await approveEnrollment(row.id);
        this.$q.notify({ type: 'positive', message: '승인 완료', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async reject(row) {
      try {
        await rejectEnrollment(row.id, { reason: '관리자 거절' });
        this.$q.notify({ type: 'info', message: '거절 처리되었습니다.', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async cancel(row) {
      try {
        await cancelEnrollment(row.id);
        this.$q.notify({ type: 'info', message: '취소 처리되었습니다.', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
    async proxyEnroll() {
      if (!this.proxyUserId || !String(this.proxyUserId).trim()) {
        this.$q.notify({ type: 'warning', message: 'userId를 입력해 주세요.', position: 'top' });
        return;
      }
      this.saving = true;
      try {
        await createProxyEnrollment(this.courseId, { userId: this.proxyUserId.trim() });
        this.showProxy = false;
        this.$q.notify({ type: 'positive', message: '대리 수강신청이 완료되었습니다.', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>
