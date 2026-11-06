<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="관리자 계정 관리"
    list-title="관리자 목록"
    unit="명"
    empty-text="조건에 맞는 관리자가 없습니다."
    search-placeholder="이름, 이메일로 검색"
    row-key="id"
    :selectable="false"
    row-clickable
    :columns="columns"
    :rows="rows"
    :loading="loading"
    :error="errorMessage"
    :total="total"
    :filters="filters"
    @search="onSearch"
    @row-click="openDetail"
  >
    <!-- 확인이 필요한 관리자 — 잠김·임시 비밀번호·변경 주기 지남. 누르면 그 사람들만 -->
    <template #filter-extra>
      <div v-if="attentionChips.length" class="admins-attention" role="group" aria-label="확인이 필요한 관리자">
        <button
          v-for="chip in attentionChips"
          :key="chip.key"
          type="button"
          class="admins-chip"
          :class="[`admins-chip--${chip.tone}`, { 'admins-chip--on': attention === chip.key }]"
          :aria-pressed="attention === chip.key"
          @click="toggleAttention(chip.key)"
        >
          {{ chip.label }} <strong>{{ chip.count }}</strong>
        </button>
      </div>
    </template>

    <template #header-actions>
      <q-btn
        unelevated
        no-caps
        color="primary"
        icon="add"
        label="관리자 추가"
        class="admins-add"
        @click="createOpen = true"
      />
    </template>

    <template #cell-admin="{ row }">
      <span class="cell-title">
        {{ row.name || '-' }}
        <span v-if="row.id === userId" class="admins-me">나</span>
      </span>
    </template>

    <template #cell-email="{ row }">
      <span class="admins-email" :title="row.email || ''">{{ row.email || '-' }}</span>
    </template>

    <template #cell-roles="{ row }">
      <div class="admins-roles">
        <span v-for="item in adminRoleLabels(row.roles)" :key="item.label" class="admins-role">
          {{ item.label }}
        </span>
      </div>
    </template>

    <template #cell-status="{ row }">
      <StatusBadge
        :status="row.status"
        :label="statusBadge(row.status).label"
        :color="statusBadge(row.status).color"
      />
    </template>

    <template #cell-lock="{ row }">
      <span v-if="row.lockedUntil" class="admins-flag admins-flag--danger">
        {{ formatTimeKst(row.lockedUntil) }}까지 잠김
      </span>
      <span v-else class="cell-note cell-note--sub">-</span>
    </template>

    <template #cell-passwordChangedAt="{ row }">
      <span v-if="!row.passwordChangedAt" class="admins-flag admins-flag--warn">임시 비밀번호</span>
      <div v-else class="cell-lines">
        <span>{{ formatDateKst(row.passwordChangedAt) }}</span>
        <span v-if="passwordStale(row)" class="cell-note admins-stale">
          {{ ADMIN_PASSWORD_MAX_AGE_DAYS }}일 지남
        </span>
      </div>
    </template>

    <template #cell-lastLoginAt="{ row }">{{ formatDateMinuteKst(row.lastLoginAt, '-') }}</template>

    <!-- ⋮ 는 자주 쓰는 셋만 — 상태·역할은 행을 눌러 여는 상세 창에서(상태 넷이 초기화와 한 메뉴에 섞여 있었다) -->
    <template #row-actions="{ row }">
      <q-item v-close-popup clickable @click="openDetail(row)">
        <q-item-section>상세·역할 관리</q-item-section>
      </q-item>
      <q-item v-if="row.id !== userId" v-close-popup clickable @click="resetPassword(row)">
        <q-item-section>
          <q-item-label>{{ row.lockedUntil ? '잠금 풀기 (비밀번호 초기화)' : '비밀번호 초기화' }}</q-item-label>
        </q-item-section>
      </q-item>
      <q-item v-close-popup clickable :to="{ path: '/admin/access-logs', query: { actorId: row.id } }">
        <q-item-section>접속기록 보기</q-item-section>
      </q-item>
    </template>

    <template #default>
      <AdministratorCreateDialog v-model="createOpen" @created="onCreated" />
      <AdministratorDetailDialog
        v-model="detailOpen"
        @hide="clearUserQuery"
        :admin="detailAdmin"
        :current-user-id="userId"
        @changed="reloadAll"
        @change-status="onChangeStatus"
        @reset-password="resetPassword"
      />
      <PasswordResetDialog
        v-model="resetDialogOpen"
        :name="resetTargetName"
        :password="temporaryPassword"
      />
    </template>
  </ConsoleListPage>
</template>

<script>
import { mapState } from 'pinia';
import AdministratorCreateDialog from '@/components/console/AdministratorCreateDialog.vue';
import AdministratorDetailDialog from '@/components/console/AdministratorDetailDialog.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import PasswordResetDialog from '@/components/console/PasswordResetDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import userAdmin, { USER_STATUS_FILTER } from '@/mixins/user-admin';
import { listAdministrators } from '@/services/admin-api';
import { useAuthStore } from '@/stores/auth-store';
import { ADMIN_PASSWORD_MAX_AGE_DAYS, ADMIN_ROLE_OPTIONS } from '@/utils/admin-labels';
import { daysFromToday, formatDateKst, formatDateMinuteKst, formatTimeKst } from '@/utils/datetime';
import { roleLabels } from '@/utils/user-fields';

const ADMIN_ROLES = new Set(ADMIN_ROLE_OPTIONS.map((item) => item.value));
// 확인 필요 칩은 한 번에 받은 관리자로 센다(서버 size 상한 100) — 관리자 수는 이보다 적다
const ATTENTION_SCAN_SIZE = 100;

function isPasswordStale(row) {
  if (!row.passwordChangedAt) return false;
  const days = daysFromToday(row.passwordChangedAt);
  return days !== null && -days >= ADMIN_PASSWORD_MAX_AGE_DAYS;
}

const ATTENTION = {
  locked: { label: '로그인 잠김', tone: 'danger', test: (row) => Boolean(row.lockedUntil) },
  temp: { label: '임시 비밀번호', tone: 'warn', test: (row) => !row.passwordChangedAt },
  stale: { label: `비밀번호 ${ADMIN_PASSWORD_MAX_AGE_DAYS}일 지남`, tone: 'warn', test: isPasswordStale },
};

const COLUMNS = [
  { key: 'admin', label: '관리자', width: 170 },
  { key: 'email', label: '이메일', minWidth: 200 },
  { key: 'roles', label: '역할', width: 150 },
  { key: 'status', label: '상태', width: 76, align: 'center' },
  { key: 'lock', label: '로그인 잠금', width: 96, align: 'center' },
  { key: 'passwordChangedAt', label: '비밀번호 변경일', width: 124, align: 'center' },
  { key: 'lastLoginAt', label: '최근 로그인', width: 140, align: 'center' },
];

/* 관리자 관리(설계서 §10.1) — 피그마에 없다. 기존 콘솔 목록(ConsoleListPage)을 그대로 쓰고 서버 페이지로 조회한다 */
export default {
  name: 'AdminAdministratorsPage',
  components: { AdministratorCreateDialog, AdministratorDetailDialog, ConsoleListPage, PasswordResetDialog, StatusBadge },
  mixins: [consoleList, userAdmin],
  data() {
    return {
      ADMIN_PASSWORD_MAX_AGE_DAYS,
      columns: COLUMNS,
      statusOptions: USER_STATUS_FILTER,
      serverSearch: true,
      total: 0,
      createOpen: false,
      detailOpen: false,
      detailId: '',
      // 확인 필요 칩 — 고르면 서버 목록 대신 한 번에 받은 관리자에서 거른다
      attention: '',
      attentionRows: [],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['userId']),
    detailAdmin() {
      return this.rows.find((row) => row.id === this.detailId) || this.attentionRows.find((row) => row.id === this.detailId) || null;
    },
    attentionChips() {
      return Object.entries(ATTENTION)
        .map(([key, item]) => ({ key, ...item, count: this.attentionRows.filter(item.test).length }))
        .filter((chip) => chip.count > 0 || chip.key === this.attention);
    },
    filters() {
      return [
        { key: 'role', label: '역할', options: ADMIN_ROLE_OPTIONS },
        { key: 'status', label: '상태', options: USER_STATUS_FILTER },
      ];
    },
  },
  watch: {
    page() {
      this.load();
    },
    pageSize() {
      if (this.page === 1) this.load();
      else this.page = 1;
    },
  },
  async created() {
    await this.reloadAll();
    this.openFromQuery();
  },
  methods: {
    formatDateKst,
    formatDateMinuteKst,
    formatTimeKst,
    // 목록은 관리자 역할만 보여 준다 — 같은 계정의 학습자 역할은 여기서 의미가 없다
    adminRoleLabels(roles) {
      return roleLabels((roles || []).filter((item) => ADMIN_ROLES.has(item.role)));
    },
    passwordStale(row) {
      return isPasswordStale(row);
    },
    /** 접속기록에서 관리자 이름을 누르면 ?userId= 로 온다 — 그 사람 상세를 바로 연다 */
    openFromQuery() {
      const userId = typeof this.$route.query.userId === 'string' ? this.$route.query.userId : '';
      if (!userId) return;
      const found = this.rows.find((row) => row.id === userId) || this.attentionRows.find((row) => row.id === userId);
      if (found) {
        this.openDetail(found);
        return;
      }
      // 관리자 역할이 회수됐거나 탈퇴했으면 이 목록에 없다
      this.$q.notify({ type: 'warning', message: '이 사용자는 지금 관리자 목록에 없습니다. 역할이 회수됐을 수 있습니다.', position: 'top' });
      this.clearUserQuery();
    },
    clearUserQuery() {
      if (!this.$route.query.userId) return;
      const rest = Object.fromEntries(Object.entries(this.$route.query).filter(([key]) => key !== 'userId'));
      this.$router.replace({ query: rest });
    },
    openDetail(row) {
      this.detailId = row.id;
      this.detailOpen = true;
    },
    async onChangeStatus(row, status) {
      // 믹스인이 확인 창·알림·목록 다시 읽기를 맡는다 — 칩 수도 함께
      await this.changeStatus(row, status);
      await this.loadAttention();
    },
    toggleAttention(key) {
      this.attention = this.attention === key ? '' : key;
      if (this.page === 1) this.load();
      else this.page = 1;
    },
    async reloadAll() {
      await Promise.all([this.load(), this.loadAttention()]);
    },
    // 칩 수는 보조 정보 — 실패해도 목록은 보이게 조용히 비운다
    async loadAttention() {
      try {
        const result = await listAdministrators({ page: 0, size: ATTENTION_SCAN_SIZE });
        this.attentionRows = (result.data || []).map((item) => ({ ...item, id: item.userId }));
      } catch {
        this.attentionRows = [];
      }
    },
    onSearch(payload) {
      // 검색은 서버 목록 — 확인 필요 칩 거르기는 푼다
      this.attention = '';
      this.search = payload;
      if (this.page === 1) this.load();
      else this.page = 1;
    },
    onCreated() {
      this.loadAttention();
      if (this.page === 1) this.load();
      else this.page = 1;
    },
    /** 칩을 골랐을 때 — 받은 관리자에서 걸러 화면에서 쪽을 나눈다 */
    loadAttentionPage() {
      const test = ATTENTION[this.attention].test;
      const matched = this.attentionRows.filter(test);
      const start = (this.page - 1) * this.pageSize;
      this.rows = matched.slice(start, start + this.pageSize);
      this.total = matched.length;
    },
    async load() {
      if (this.attention) {
        this.errorMessage = '';
        if (!this.attentionRows.length) await this.loadAttention();
        this.loadAttentionPage();
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      const filters = this.search.filters || {};
      try {
        const result = await listAdministrators({
          page: this.page - 1,
          size: this.pageSize,
          role: filters.role || undefined,
          status: filters.status || undefined,
          q: this.search.q || undefined,
        });
        // user-admin 믹스인(상태 변경·비밀번호 초기화)은 row.id 를 쓴다
        this.rows = (result.data || []).map((item) => ({ ...item, id: item.userId }));
        this.total = result.page?.totalElements ?? this.rows.length;
      } catch (error) {
        this.total = 0;
        this.handleLoadError(error, '관리자 목록을 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 주소는 한 줄로, 넘치면 … — 전체는 마우스를 올리면 보인다(옆 역할 열로 넘치거나 글자 중간에서 꺾이지 않게) */
.admins-email {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
}

.admins-add {
  border-radius: 6px;
}

.admins-me {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--db-accent-soft);
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-accent);
  vertical-align: middle;
}

.admins-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.admins-role {
  padding: 2px 8px;
  border: 1px solid var(--db-border);
  border-radius: 4px;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-body);
  /* "기관 관리자 (기관명)" 은 길다 — 칸 안에서 꺾어 옆 상태 열을 침범하지 않게 */
  max-width: 100%;
  overflow-wrap: anywhere;
}

.admins-flag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  white-space: nowrap;
}

.admins-flag--danger {
  background: color-mix(in srgb, var(--db-status-pink) 12%, transparent);
  color: var(--db-status-pink);
}

.admins-flag--warn {
  background: color-mix(in srgb, var(--db-status-orange) 14%, transparent);
  /* 주황 토큰 그대로는 흰 바탕 대비가 부족해 어둡게 섞는다 */
  color: var(--db-status-orange-text);
}

/* 확인 필요 칩 */
.admins-attention {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.admins-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-body);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.admins-chip--danger strong {
  color: var(--db-status-pink);
}

.admins-chip--warn strong {
  color: var(--db-status-orange-text);
}

.admins-chip--on.admins-chip--danger {
  border-color: var(--db-status-pink);
  background: var(--db-status-pink-soft);
}

.admins-chip--on.admins-chip--warn {
  border-color: var(--db-status-orange-text);
  background: var(--db-status-orange-soft);
}

.admins-chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

@media (hover: hover) {
  .admins-chip:not(.admins-chip--on):hover {
    border-color: var(--db-text-sub);
  }
}

@media (prefers-reduced-motion: reduce) {
  .admins-chip {
    transition: none;
  }
}

.admins-stale {
  color: var(--db-status-orange-text);
}
</style>
