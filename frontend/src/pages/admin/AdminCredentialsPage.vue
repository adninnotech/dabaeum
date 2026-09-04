<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">VC 수료증 발급</div>
        <div class="app-page-header__subtitle text-grey-7">GET /users/{userId}/credentials · revoke / reissue</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadCredentials" />
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-sm q-mb-md items-end">
      <div class="col-12 col-sm-7 col-md-6">
        <q-input v-model="userId" outlined dense label="userId" />
      </div>
      <div class="col-auto">
        <q-btn color="primary" unelevated label="조회" :loading="loading" @click="loadCredentials" />
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
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="negative" label="폐기" :loading="actionId === props.row.id" @click="revoke(props.row)" />
          <q-btn flat dense color="primary" label="재발급" :loading="actionId === props.row.id" @click="reissue(props.row)" />
          <q-btn flat dense color="secondary" label="배지발급" :loading="actionId === props.row.id" @click="issueBadge(props.row)" />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { DEFAULT_USER_ID } from '@/config/api';
import {
  issueLearningBadge,
  listUserCredentials,
  reissueCredential,
  revokeCredential,
} from '@/services/credential-api';

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'AdminCredentialsPage',
  data() {
    return {
      userId: DEFAULT_USER_ID,
      rows: [],
      loading: false,
      actionId: null,
      errorMessage: '',
      columns: [
        {
          name: 'credentialNo',
          label: '번호',
          field: (r) => r.credentialNo || r.id,
          align: 'left',
        },
        {
          name: 'courseTitle',
          label: '강좌',
          field: (r) => r.courseTitle || r.course?.title || r.courseId || '-',
          align: 'left',
        },
        {
          name: 'subjectIdentifier',
          label: '대상',
          field: (r) => r.subjectIdentifier || r.userId || r.subject?.id || '-',
          align: 'left',
        },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'issuedAt', label: '발급일', field: 'issuedAt', align: 'left', format: qFormatDateTimeKst },
        { name: 'actions', label: '액션', field: 'actions', align: 'right' },
      ],
    };
  },
  mounted() {
    this.loadCredentials();
  },
  methods: {
    async loadCredentials() {
      if (!this.userId || !String(this.userId).trim()) {
        this.$q.notify({ type: 'warning', message: 'userId를 입력해 주세요.', position: 'top' });
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listUserCredentials(this.userId.trim(), {
          page: 0,
          size: 50,
          sort: 'issuedAt,desc',
        });
        this.rows = Array.isArray(result.data) ? result.data : [];
      } catch (error) {
        this.errorMessage = error.message || '수료증 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async revoke(row) {
      this.actionId = row.id;
      try {
        await revokeCredential(row.id, { reason: '관리자 폐기' });
        this.$q.notify({ type: 'info', message: '수료증이 폐기되었습니다.', position: 'top' });
        await this.loadCredentials();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionId = null;
      }
    },
    async reissue(row) {
      this.actionId = row.id;
      try {
        await reissueCredential(row.id, {
          reason: '관리자 재발급',
          validUntil: '2029-12-31T23:59:59+09:00',
        });
        this.$q.notify({ type: 'positive', message: '재발급이 완료되었습니다.', position: 'top' });
        await this.loadCredentials();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionId = null;
      }
    },
    async issueBadge(row) {
      this.actionId = row.id;
      try {
        await issueLearningBadge(row.id, {
          badgeType: 'COMPLETION',
          badgeName: '수료 배지',
          courseId: row.courseId || null,
        });
        this.$q.notify({ type: 'positive', message: '배지 발급 요청이 완료되었습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.actionId = null;
      }
    },
  },
};
</script>
