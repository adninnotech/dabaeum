<template>
  <q-page padding>
    <div class="page-inner">
      <div class="app-page-header row items-center justify-between q-mb-lg">
        <div>
          <div class="app-page-header__title text-h5 text-weight-bold">수강신청</div>
          <div class="app-page-header__subtitle text-grey-7">내 신청 현황</div>
        </div>
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadEnrollments" />
      </div>

      <q-banner v-if="!isLoggedIn" class="bg-blue-1 q-mb-md rounded-borders">
        로그인 후 본인 신청 내역을 확인할 수 있습니다.
        <template #action>
          <q-btn flat color="primary" label="로그인" @click="goLogin" />
        </template>
      </q-banner>

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadEnrollments" />
        </template>
      </q-banner>

      <q-table
        flat
        bordered
        row-key="id"
        :rows="rows"
        :columns="columns"
        :loading="loading"
        :pagination="{ rowsPerPage: 10 }"
        no-data-label="신청 내역이 없습니다."
      >
        <template #body-cell-status="props">
          <q-td :props="props">
            <StatusBadge :status="props.row.status" />
          </q-td>
        </template>
        <template #body-cell-actions="props">
          <q-td :props="props">
            <q-btn flat dense color="primary" label="상세" @click="goDetail(props.row.id)" />
            <q-btn
              v-if="canCancel(props.row.status)"
              flat
              dense
              color="negative"
              label="취소"
              :loading="actionId === props.row.id"
              @click="onCancel(props.row)"
            />
            <q-btn
              v-if="canWithdraw(props.row.status)"
              flat
              dense
              color="warning"
              label="철회"
              :loading="actionId === props.row.id"
              @click="onWithdraw(props.row)"
            />
          </q-td>
        </template>
      </q-table>
    </div>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import StatusBadge from '@/components/StatusBadge.vue';
import { mapState } from 'pinia';
import {
  cancelEnrollment,
  listUserEnrollments,
  withdrawEnrollment,
} from '@/services/enrollment-api';
import { useAuthStore } from '@/stores/auth-store';

export default {
  components: { StatusBadge },
  name: 'EnrollmentPage',
  data() {
    return {
      rows: [],
      loading: false,
      actionId: '',
      errorMessage: '',
      columns: [
        { name: 'courseTitle', label: '강좌명', field: 'courseTitle', align: 'left' },
        { name: 'applicationType', label: '신청유형', field: 'applicationType', align: 'center' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        {
          name: 'appliedAt',
          label: '신청일',
          field: 'appliedAt',
          align: 'left',
          format: qFormatDateTimeKst,
        },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'userId']),
  },
  mounted() {
    this.loadEnrollments();
  },
  methods: {
    canCancel(status) {
      return status === 'APPLIED' || status === 'WAITLISTED';
    },
    canWithdraw(status) {
      return status === 'APPROVED';
    },
    goLogin() {
      this.$router.push({ path: '/login', query: { redirect: '/enrollment' } });
    },
    goDetail(id) {
      this.$router.push(`/enrollment/${id}`);
    },
    async loadEnrollments() {
      if (!this.isLoggedIn) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const rows = await listUserEnrollments(this.userId);
        this.rows = Array.isArray(rows) ? rows : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강신청 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async onCancel(row) {
      this.actionId = row.id;
      try {
        await cancelEnrollment(row.id);
        this.$q.notify({ type: 'info', message: '신청이 취소되었습니다.', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '취소에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.actionId = '';
      }
    },
    async onWithdraw(row) {
      this.actionId = row.id;
      try {
        await withdrawEnrollment(row.id);
        this.$q.notify({ type: 'info', message: '수강이 철회되었습니다.', position: 'top' });
        await this.loadEnrollments();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '철회에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.actionId = '';
      }
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 1100px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
</style>
