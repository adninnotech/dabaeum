<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="학생 회원 관리"
    list-title="회원 목록"
    unit="명"
    empty-text="등록된 회원이 없습니다."
    search-placeholder="회원명, 이메일, 연락처로 검색"
    :stats="stats"
    :columns="columns"
    :rows="rows"
    :loading="loading"
    :error="errorMessage"
    :total="serverTotal ?? rows.length"
    :filters="filters"
    :sort="sort"
    @sort="onSort"
    @search="onSearch"
  >
    <!-- 가입일 기간 — 고르는 즉시 걸러진다. 빠른 선택은 오늘까지 N일 -->
    <template #filter-extra>
      <div class="signup-period" role="group" aria-label="가입일 기간">
        <span class="signup-period__label">가입일</span>
        <AppDateInput
          v-model="signupFrom"
          outlined
          dense
          clearable
          no-future
          hide-bottom-space
          aria-label="가입일 시작"
          class="signup-date"
          :max="signupTo || ''"
        />
        <span class="signup-period__sep" aria-hidden="true">~</span>
        <AppDateInput
          v-model="signupTo"
          outlined
          dense
          clearable
          no-future
          hide-bottom-space
          aria-label="가입일 종료"
          class="signup-date"
          :min="signupFrom || ''"
        />
        <div class="signup-presets">
          <button
            v-for="preset in SIGNUP_PRESETS"
            :key="preset.days"
            type="button"
            class="signup-preset"
            :class="{ active: isPreset(preset.days) }"
            :aria-pressed="isPreset(preset.days) ? 'true' : 'false'"
            @click="applyPreset(preset.days)"
          >
            {{ preset.label }}
          </button>
        </div>
      </div>
    </template>

    <template #cell-user="{ row }">
      <div class="cell-user">
        <div class="cell-user__avatar">
          <img v-if="row.avatarUrl" :src="row.avatarUrl" alt="" />
          <template v-else>{{ initial(row.name) }}</template>
        </div>
        <div class="cell-lines">
          <RouterLink :to="`/admin/students/${row.id}`" class="cell-title cell-link">
            {{ row.name || '-' }}
          </RouterLink>
          <!-- 목록엔 생년월일 전체 대신 6자리, 휴대폰은 가운데를 가린다 — 전체 값은 상세에서 -->
          <div class="cell-note cell-note--sub">{{ formatBirthDateCompact(row.birthDate) || '-' }} · 학생</div>
        </div>
      </div>
    </template>

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
    <template #cell-enrollmentCount="{ row }">{{ row.enrollmentCount ?? '-' }}</template>

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
import AppDateInput from '@/components/AppDateInput.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import PasswordResetDialog from '@/components/console/PasswordResetDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import userAdmin, { USER_STATUS_FILTER } from '@/mixins/user-admin';
import { listUsers } from '@/services/user-api';
import { formatDateKst, todayString } from '@/utils/datetime';
import {
  ENROLLMENT_FILTER,
  GENDER_FILTER,
  LOGIN_FILTER,
  SIGNUP_PRESETS,
  kstDateKey,
  memberListQuery,
} from '@/utils/member-filters';
import { userRow } from '@/utils/user-fields';

const COLUMNS = [
  { key: 'user', label: '회원 정보' },
  { key: 'contact', label: '이메일·연락처', width: 238 },
  { key: 'createdAt', label: '가입일', width: 190, align: 'center', sortable: 'date' },
  { key: 'status', label: '상태', width: 82, align: 'center' },
  { key: 'lastLoginAt', label: '최근 로그인', width: 210, align: 'center', sortable: 'date' },
  { key: 'enrollmentCount', label: '수강 수', width: 150, align: 'center', sortable: 'number' },
];

const DAY_MS = 24 * 60 * 60 * 1000;
const ROLE = 'LEARNER';

/** 오늘 포함 N일 전 'YYYY-MM-DD' (N=1 이면 오늘) */
function startOfLastDays(days) {
  return kstDateKey(new Date(Date.now() - (days - 1) * DAY_MS));
}

/** 조건에 맞는 학생 수 — 한 명만 받고 page.totalElements 를 쓴다. 못 받으면 null('-') */
async function countLearners(query) {
  const result = await listUsers({ page: 0, size: 1, role: ROLE, ...query });
  const total = result.page?.totalElements;
  return Number.isFinite(total) ? total : null;
}

export default {
  name: 'AdminStudentsPage',
  components: { AppDateInput, ConsoleListPage, PasswordResetDialog, StatusBadge },
  mixins: [consoleList, userAdmin],
  data() {
    return {
      columns: COLUMNS,
      statusOptions: USER_STATUS_FILTER,
      SIGNUP_PRESETS,
      signupFrom: '',
      signupTo: '',
      // 처음부터 '가입일 최신순'이 켜져 보여 정렬할 수 있는 줄 안다
      sort: { key: 'createdAt', dir: 'desc' },
      // 요약 카드 — 필터와 상관없는 전체 학생 기준 서버 총계
      summary: { all: null, active: null, recent: null, dormant: null },
      // 늦게 온 옛 응답이 새 목록을 덮지 않게 요청마다 번호를 붙인다
      requestSeq: 0,
    };
  },
  computed: {
    filters() {
      return [
        { key: 'status', label: '상태', options: USER_STATUS_FILTER },
        { key: 'gender', label: '성별', options: GENDER_FILTER },
        { key: 'enrollment', label: '수강', options: ENROLLMENT_FILTER },
        { key: 'login', label: '최근 접속', options: LOGIN_FILTER },
      ];
    },
    /**
     * 목록 요청 조건 전부 — 최신 100명만 받아 그 안에서 거르니 달서구 496명이 14명으로 나왔다(QA 중앙 #52).
     * 필터·정렬·페이지를 모두 서버에 싣고, 바뀌면 다시 받는다
     */
    listQuery() {
      return {
        page: this.page - 1,
        size: this.pageSize,
        role: ROLE,
        ...memberListQuery({
          q: this.search.q,
          filters: this.search.filters,
          signupFrom: this.signupFrom,
          signupTo: this.signupTo,
          sort: this.sort,
        }),
      };
    },
    stats() {
      return [
        { label: '전체 회원', value: this.summary.all ?? '-', unit: '명' },
        { label: '활성 회원', value: this.summary.active ?? '-', unit: '명' },
        { label: '최근 가입(30일)', value: this.summary.recent ?? '-', unit: '명' },
        { label: '비활성 회원', value: this.summary.dormant ?? '-', unit: '명' },
      ];
    },
  },
  watch: {
    listQuery() {
      this.loadRows();
    },
    signupFrom() {
      this.page = 1;
    },
    signupTo() {
      this.page = 1;
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
    isPreset(days) {
      return this.signupTo === todayString() && this.signupFrom === startOfLastDays(days);
    },
    applyPreset(days) {
      if (this.isPreset(days)) {
        this.signupFrom = '';
        this.signupTo = '';
        return;
      }
      this.signupFrom = startOfLastDays(days);
      this.signupTo = todayString();
    },
    // 조건이 바뀌면 listQuery 감시가 다시 받는다 — 여기선 첫 페이지로만
    onSearch(payload) {
      this.search = payload;
      this.page = 1;
    },
    onSort(sort) {
      this.sort = sort;
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
        this.handleLoadError(error, '회원 목록 조회 실패');
      } finally {
        if (seq === this.requestSeq) this.loading = false;
      }
    },
    async loadSummary() {
      const counts = await Promise.allSettled([
        countLearners({}),
        countLearners({ status: 'ACTIVE' }),
        countLearners({ createdFrom: startOfLastDays(30), createdTo: todayString() }),
        countLearners({ status: 'DORMANT' }),
      ]);
      const [all, active, recent, dormant] = counts.map((item) =>
        item.status === 'fulfilled' ? item.value : null,
      );
      if (counts.some((item) => item.status === 'rejected')) {
        // 숫자 카드만 '-' 로 남는다 — 목록은 따로 받는다
        console.warn('[AdminStudentsPage] 요약 숫자를 일부 불러오지 못했습니다', counts);
      }
      this.summary = { all, active, recent, dormant };
    },
  },
};
</script>

<style scoped>
/* 필터가 다섯 개라 고른 값('90일 넘게 없음')이 120px 에서 잘렸다 */
.console-list :deep(.filter-select) {
  width: 140px;
}

.signup-period {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.signup-period__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.signup-period__sep {
  color: var(--db-text-sub);
}

.signup-date {
  width: 172px;
}

.signup-presets {
  display: inline-flex;
  gap: 4px;
}

.signup-preset {
  min-width: 44px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-white);
  font: inherit;
  font-size: 13px;
  color: var(--db-text-body);
  cursor: pointer;
}

.signup-preset:hover {
  border-color: var(--db-accent);
}

.signup-preset:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.signup-preset.active {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 700;
}

@media (max-width: 599px) {
  .signup-period {
    width: 100%;
  }

  .signup-date {
    flex: 1 1 120px;
    width: auto;
    min-width: 0;
  }

  .signup-presets {
    width: 100%;
  }

  .signup-preset {
    flex: 1;
  }
}
</style>
