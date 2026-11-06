<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="전체 강사 관리"
    list-title="강사 목록"
    unit="명"
    empty-text="등록된 강사가 없습니다."
    search-placeholder="회원명, 기관명, 이메일, 연락처로 검색"
    :stats="stats"
    :columns="columns"
    :rows="rows"
    :loading="loading"
    :error="errorMessage"
    :total="serverTotal ?? rows.length"
    :filters="filters"
    @search="onSearch"
  >
    <template #cell-user="{ row }">
      <div class="cell-user">
        <div class="cell-user__avatar">
          <img v-if="row.avatarUrl" :src="row.avatarUrl" alt="" />
          <template v-else>{{ initial(row.name) }}</template>
        </div>
        <div class="cell-lines">
          <RouterLink :to="`/admin/instructors/${row.id}`" class="cell-title cell-link">
            {{ row.name || '-' }}
          </RouterLink>
          <!-- 목록엔 생년월일 전체 대신 6자리, 휴대폰은 가운데를 가린다 — 전체 값은 상세에서 -->
          <div class="cell-note cell-note--sub">{{ formatBirthDateCompact(row.birthDate) || '-' }} · 강사</div>
        </div>
      </div>
    </template>

    <template #cell-institutionName="{ row }">{{ institutionNameOf(row) }}</template>

    <template #cell-contact="{ row }">
      <div class="cell-lines">
        <div>{{ row.email || '-' }}</div>
        <div class="cell-note cell-note--sub">{{ maskPhone(row.phone) }}</div>
      </div>
    </template>

    <template #cell-createdAt="{ row }">{{ formatDateKst(row.createdAt, '-') }}</template>

    <template #cell-status="{ row }">
      <StatusBadge
        :status="row.status"
        :label="statusBadge(row.status).label"
        :color="statusBadge(row.status).color"
      />
    </template>

    <template #cell-lastLoginAt="{ row }">{{ formatDateKst(row.lastLoginAt, '-') }}</template>
    <template #cell-courseCount="{ row }">{{ row.courseCount ?? '-' }}</template>

    <template #row-actions="{ row }">
      <q-item
        v-for="option in statusOptions"
        :key="option.value"
        v-close-popup
        clickable
        @click="changeStatus(row, option.value)"
      >
        <q-item-section>상태: {{ option.label }}</q-item-section>
      </q-item>
      <q-separator />
      <q-item v-close-popup clickable @click="resetPassword(row)">
        <q-item-section>비밀번호 초기화</q-item-section>
      </q-item>
    </template>

    <template #default>
      <PasswordResetDialog
        v-model="resetDialogOpen"
        :name="resetTargetName"
        :password="temporaryPassword"
      />
    </template>
  </ConsoleListPage>
</template>

<script>
import { formatBirthDateCompact, maskPhone } from '@/utils/user-display';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import PasswordResetDialog from '@/components/console/PasswordResetDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import userAdmin, { USER_STATUS_FILTER } from '@/mixins/user-admin';
import { listUsers } from '@/services/user-api';
import { formatDateKst } from '@/utils/datetime';
import { memberListQuery } from '@/utils/member-filters';
import { userRow } from '@/utils/user-fields';

const COLUMNS = [
  { key: 'user', label: '회원 정보' },
  { key: 'institutionName', label: '기관명', width: 160 },
  { key: 'contact', label: '이메일·연락처', width: 220 },
  { key: 'createdAt', label: '가입일', width: 100, align: 'center' },
  { key: 'status', label: '상태', width: 80, align: 'center' },
  { key: 'lastLoginAt', label: '최근 로그인', width: 100, align: 'center' },
  { key: 'courseCount', label: '강의 수', width: 80, align: 'center' },
];

const ROLE = 'INSTRUCTOR';

/** 조건에 맞는 강사 수 — 한 명만 받고 page.totalElements 를 쓴다. 못 받으면 null('-') */
async function countInstructors(query) {
  const result = await listUsers({ page: 0, size: 1, role: ROLE, ...query });
  const total = result.page?.totalElements;
  return Number.isFinite(total) ? total : null;
}

export default {
  name: 'AdminInstructorsPage',
  components: { ConsoleListPage, PasswordResetDialog, StatusBadge },
  mixins: [consoleList, userAdmin],
  data() {
    return {
      columns: COLUMNS,
      statusOptions: USER_STATUS_FILTER,
      // 요약 카드 — 필터와 상관없는 전체 강사 기준 서버 총계
      summary: { all: null, active: null, dormant: null, withdrawn: null },
      // 늦게 온 옛 응답이 새 목록을 덮지 않게 요청마다 번호를 붙인다
      requestSeq: 0,
    };
  },
  computed: {
    filters() {
      return [
        { key: 'status', label: '상태', options: USER_STATUS_FILTER },
      ];
    },
    /**
     * 목록 요청 조건 전부 — 학생(QA 중앙 #52)처럼 최신 100명만 받아 그 안에서 거르면 강사가 100명을 넘을 때 모자란다.
     * 필터·페이지를 모두 서버에 싣고, 바뀌면 다시 받는다
     */
    listQuery() {
      return {
        page: this.page - 1,
        size: this.pageSize,
        role: ROLE,
        ...memberListQuery({ q: this.search.q, filters: this.search.filters }),
      };
    },
    stats() {
      return [
        { label: '전체 강사 수', value: this.summary.all ?? '-', unit: '명' },
        { label: '활성 강사', value: this.summary.active ?? '-', unit: '명' },
        { label: '비활성 강사', value: this.summary.dormant ?? '-', unit: '명' },
        { label: '탈퇴 강사', value: this.summary.withdrawn ?? '-', unit: '명' },
      ];
    },
  },
  watch: {
    listQuery() {
      this.loadRows();
    },
    pageSize() {
      this.page = 1;
    },
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    formatBirthDateCompact,
    maskPhone,
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase();
    },
    institutionNameOf(row) {
      const roles = Array.isArray(row.roles) ? row.roles : [];
      const withInstitution = roles.find((r) => r.role === 'INSTRUCTOR' && r.institutionName);
      return withInstitution?.institutionName || '-';
    },
    // 조건이 바뀌면 listQuery 감시가 다시 받는다 — 여기선 첫 페이지로만
    onSearch(payload) {
      this.search = payload;
      this.page = 1;
    },
    /** 목록과 요약 카드 — 처음과 상태 변경 뒤(user-admin mixin) */
    async load() {
      await Promise.all([this.loadRows(), this.loadSummary()]);
    },
    async loadRows() {
      const seq = ++this.requestSeq;
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listUsers(this.listQuery);
        if (seq !== this.requestSeq) return;
        const users = Array.isArray(result.data) ? result.data : [];
        this.rows = users.map((user) => ({ ...user, ...userRow(user) }));
        this.rememberTotal(result);
      } catch (error) {
        if (seq !== this.requestSeq) return;
        this.serverTotal = null;
        this.handleLoadError(error, '강사 목록 조회 실패');
      } finally {
        if (seq === this.requestSeq) this.loading = false;
      }
    },
    async loadSummary() {
      const counts = await Promise.allSettled([
        countInstructors({}),
        countInstructors({ status: 'ACTIVE' }),
        countInstructors({ status: 'DORMANT' }),
        countInstructors({ status: 'WITHDRAWN' }),
      ]);
      const [all, active, dormant, withdrawn] = counts.map((item) =>
        item.status === 'fulfilled' ? item.value : null,
      );
      if (counts.some((item) => item.status === 'rejected')) {
        // 숫자 카드만 '-' 로 남는다 — 목록은 따로 받는다
        console.warn('[AdminInstructorsPage] 요약 숫자를 일부 불러오지 못했습니다', counts);
      }
      this.summary = { all, active, dormant, withdrawn };
    },
  },
};
</script>

<style scoped>
.cell-link {
  text-decoration: none;
}

.cell-link:hover {
  text-decoration: underline;
}
</style>
