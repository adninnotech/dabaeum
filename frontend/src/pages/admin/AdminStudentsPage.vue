<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">수강생 관리</div>
        <div class="app-page-header__subtitle text-grey-7">GET /users · 역할(LEARNER) 필터 · 상태 변경</div>
      </div>
      <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadUsers" />
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-sm q-mb-md items-center">
      <div class="col-12 col-sm-4 col-md-3">
        <q-toggle v-model="learnerOnly" label="LEARNER만 표시" @update:model-value="onLearnerToggle" />
      </div>
      <div class="col-12 col-sm-5 col-md-4">
        <q-input v-model="keyword" outlined dense clearable label="이름/이메일 검색" />
      </div>
    </div>

    <q-table
      flat
      bordered
      row-key="id"
      :rows="filteredRows"
      :columns="columns"
      :loading="loading"
      :pagination="{ rowsPerPage: 10 }"
    >
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" />
        </q-td>
      </template>
      <template #body-cell-roles="props">
        <q-td :props="props">
          <template v-if="props.row.roles && props.row.roles.length">
            <q-badge
              v-for="role in props.row.roles"
              :key="typeof role === 'string' ? role : role.id || role.role"
              class="q-mr-xs"
              outline
              color="primary"
            >
              {{ typeof role === 'string' ? role : role.role || role.name }}
            </q-badge>
          </template>
          <span v-else class="text-grey-6">-</span>
        </q-td>
      </template>
      <template #body-cell-actions="props">
        <q-td :props="props">
          <q-btn flat dense color="warning" label="휴면" @click="setStatus(props.row, 'DORMANT')" />
          <q-btn flat dense color="negative" label="정지" @click="setStatus(props.row, 'SUSPENDED')" />
          <q-btn flat dense color="positive" label="활성" @click="setStatus(props.row, 'ACTIVE')" />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { changeUserStatus, listUsers } from '@/services/user-api';

export default {
  components: { AppErrorBanner,  StatusBadge },
  name: 'AdminStudentsPage',
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      keyword: '',
      learnerOnly: true,
      columns: [
        { name: 'name', label: '이름', field: 'name', align: 'left' },
        { name: 'email', label: '이메일', field: 'email', align: 'left' },
        { name: 'phone', label: '휴대폰', field: 'phone', align: 'left' },
        { name: 'roles', label: '역할', field: 'roles', align: 'left' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'actions', label: '상태변경', field: 'actions', align: 'right' },
      ],
    };
  },
  computed: {
    filteredRows() {
      const kw = String(this.keyword || '')
        .trim()
        .toLowerCase();
      return this.rows.filter((row) => {
        if (!kw) return true;
        return (
          String(row.name || '')
            .toLowerCase()
            .includes(kw) ||
          String(row.email || '')
            .toLowerCase()
            .includes(kw) ||
          String(row.id || '')
            .toLowerCase()
            .includes(kw)
        );
      });
    },
  },
  mounted() {
    this.loadUsers();
  },
  methods: {
    onLearnerToggle() {
      this.loadUsers();
    },
    roleNames(roles) {
      if (!Array.isArray(roles)) return [];
      return roles.map((r) => (typeof r === 'string' ? r : r.role || r.name)).filter(Boolean);
    },
    async loadUsers() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listUsers({ page: 0, size: 50, sort: 'createdAt,desc' });
        const users = Array.isArray(result.data) ? result.data : [];
        const hasAnyRoles = users.some((u) => Array.isArray(u.roles) && u.roles.length);

        if (this.learnerOnly && hasAnyRoles) {
          const learners = users.filter((u) => this.roleNames(u.roles).includes('LEARNER'));
          this.rows = learners.length ? learners : users;
          if (!learners.length && users.length) {
            this.$q.notify({
              type: 'info',
              message: 'LEARNER 역할 사용자가 없어 전체 사용자를 표시합니다.',
              position: 'top',
            });
          }
        } else {
          this.rows = users;
        }
      } catch (error) {
        this.errorMessage = error.message || '사용자 목록 조회 실패';
        this.rows = [];
      } finally {
        this.loading = false;
      }
    },
    async setStatus(row, status) {
      try {
        await changeUserStatus(row.id, status);
        this.$q.notify({ type: 'info', message: `상태 변경: ${status}`, position: 'top' });
        await this.loadUsers();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      }
    },
  },
};
</script>
