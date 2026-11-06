<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="학습배지 관리"
    list-title="배지 정의"
    unit="개"
    tall-rows
    empty-text="등록된 배지가 없습니다."
    search-placeholder="배지 이름 또는 코드로 검색"
    row-key="id"
    :selectable="false"
    :stats="stats"
    :stat-columns="3"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    row-clickable
    @search="onSearch"
    @row-click="openForm"
  >
    <template #header-actions>
      <q-btn unelevated no-caps color="primary" icon="add" label="배지 추가" @click="openForm(null)" />
    </template>

    <template #cell-badge="{ row }">
      <div class="bd-badge">
        <!-- 대표 그림이 없으면 1단계 그림 — 학습자가 받는 배지와 다른 기본 아이콘이 보였다 -->
        <BadgeMedal
          :image-url="coverImage(row).url"
          :image-file-id="coverImage(row).fileId"
          :size="44"
          :muted="!row.active"
          :alt="row.name"
        />
        <div class="cell-lines">
          <span class="cell-title">{{ row.name }}</span>
          <code class="bd-code">{{ row.code }}</code>
        </div>
      </div>
    </template>

    <template #cell-rule="{ row }">
      <BadgeCriteria
        class="bd-rule"
        compact
        :rule="row.rule"
        :classification-tree="classificationTree"
        :course-titles="courseTitles"
      />
    </template>

    <!-- 몇 명이 받았나 — 조건 잠김·삭제 가능 여부를 가르는 숫자. 누르면 그 배지로 거른 발급 현황 -->
    <template #cell-issued="{ row }">
      <span v-if="issuedCounts[row.id] == null" class="bd-muted">—</span>
      <RouterLink
        v-else-if="issuedCounts[row.id] > 0"
        :to="issuedPath(row)"
        class="bd-issued"
        :aria-label="`${row.name} 발급 현황 ${issuedCounts[row.id]}개 보기`"
        @click.stop
      >
        {{ formatCount(issuedCounts[row.id]) }}개
      </RouterLink>
      <span v-else class="bd-muted">0개</span>
    </template>

    <template #cell-active="{ row }">
      <StatusBadge
        :label="row.active ? '사용' : '사용 중지'"
        :color="row.active ? 'var(--db-status-green)' : 'var(--db-status-gray)'"
      />
    </template>

    <template #cell-updatedAt="{ row }">{{ formatDateKst(row.updatedAt, '-') }}</template>

    <!-- 수정은 행을 누르면 열린다 — 메뉴엔 그 밖의 일만 -->
    <template #row-actions="{ row }">
      <q-item v-close-popup clickable :to="issuedPath(row)">
        <q-item-section>발급 현황 보기</q-item-section>
      </q-item>
      <q-item v-close-popup clickable @click="toggleActive(row)">
        <q-item-section>{{ row.active ? '사용 중지' : '다시 사용' }}</q-item-section>
      </q-item>
      <q-separator />
      <q-item v-close-popup clickable @click="confirmRemove(row)">
        <q-item-section class="bd-danger">삭제</q-item-section>
      </q-item>
    </template>

    <template #default>
      <BadgeDefinitionDialog v-model="formOpen" :definition="editing" @saved="onSaved" />
    </template>
  </ConsoleListPage>
</template>

<script>
import { confirmDialog } from '@/utils/confirm-dialog';
import BadgeCriteria from '@/components/badge/BadgeCriteria.vue';
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import BadgeDefinitionDialog from '@/components/console/BadgeDefinitionDialog.vue';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import {
  deleteBadgeDefinition,
  listAdminBadgeDefinitions,
  setBadgeDefinitionActive,
} from '@/services/badge-api';
import { searchAdminBadges } from '@/services/credential-api';
import { listCourseClassifications } from '@/services/classification-api';
import { getCourse } from '@/services/course-api';
import { formatDateKst } from '@/utils/datetime';

const COLUMNS = [
  { key: 'badge', label: '배지' },
  { key: 'rule', label: '획득 조건', width: 300 },
  { key: 'issued', label: '발급', width: 96, align: 'center' },
  { key: 'active', label: '상태', width: 100, align: 'center' },
  { key: 'updatedAt', label: '수정일', width: 116, align: 'center' },
];

const ACTIVE_FILTER = [
  { label: '사용', value: true },
  { label: '사용 중지', value: false },
];

/* 학습배지 정의 관리(설계서 §11, 플랫폼 관리자) — 피그마에 없다. 콘솔 목록(ConsoleListPage)을 쓴다.
 * 정의 수가 많지 않아(서버가 목록을 페이지 없이 준다) 검색·필터는 클라이언트에서 한다 */
export default {
  name: 'AdminBadgeDefinitionsPage',
  components: { BadgeCriteria, BadgeDefinitionDialog, BadgeMedal, ConsoleListPage, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      searchFields: ['name', 'code'],
      filters: [{ key: 'active', label: '상태', options: ACTIVE_FILTER }],
      classificationTree: [],
      courseTitles: {},
      formOpen: false,
      editing: null,
      // 정의 id → 발급 건수(취소 포함). 아직 못 셌으면 키가 없다
      issuedCounts: {},
    };
  },
  computed: {
    // 정의 수만 세던 카드(사용 중지 수)는 쓸모가 적었다 — 관리자가 보는 건 '몇 개가 나갔나'
    stats() {
      const counts = Object.values(this.issuedCounts);
      const issuedReady = this.rows.length > 0 && counts.length === this.rows.length;
      return [
        { label: '전체 배지', value: this.rows.length, unit: '개' },
        { label: '사용 중', value: this.countBy('active', true), unit: '개' },
        {
          label: '총 발급',
          value: issuedReady ? this.formatCount(counts.reduce((sum, n) => sum + n, 0)) : '—',
          unit: issuedReady ? '개' : '',
        },
      ];
    },
  },
  created() {
    this.load();
    listCourseClassifications()
      .then((tree) => {
        this.classificationTree = tree;
      })
      .catch(() => {});
  },
  methods: {
    formatDateKst,
    coverImage(row) {
      if (row.imageUrl || row.imageFileId) return { url: row.imageUrl || '', fileId: row.imageFileId || '' };
      const first = (row.rule?.levels || [])[0] || {};
      return { url: first.imageUrl || '', fileId: first.imageFileId || '' };
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.rows = await listAdminBadgeDefinitions();
        this.loadCourseTitles();
        this.loadIssuedCounts();
      } catch (error) {
        this.handleLoadError(error, '배지 정의를 불러오지 못했습니다.');
      } finally {
        this.loading = false;
      }
    },
    async loadCourseTitles() {
      const ids = [...new Set(this.rows.map((row) => row.rule?.courseId).filter(Boolean))];
      const results = await Promise.allSettled(ids.map((id) => getCourse(id)));
      this.courseTitles = results.reduce((acc, result, index) => {
        const title = result.status === 'fulfilled' ? result.value?.title : '';
        return title ? { ...acc, [ids[index]]: title } : acc;
      }, {});
    },
    formatCount(value) {
      return Number(value || 0).toLocaleString('ko-KR');
    },
    issuedPath(row) {
      return { path: '/admin/badges', query: { definitionId: row.id } };
    },
    /** 정의마다 발급 건수 — 정의가 많지 않아(페이지 없는 목록) 한 번씩 센다. 못 센 정의는 '—' */
    async loadIssuedCounts() {
      const ids = this.rows.map((row) => row.id);
      const results = await Promise.allSettled(ids.map((id) => this.countIssued(id, null)));
      this.issuedCounts = results.reduce(
        (acc, result, index) => (result.status === 'fulfilled' && result.value != null ? { ...acc, [ids[index]]: result.value } : acc),
        {},
      );
    },
    openForm(row) {
      this.editing = row;
      this.formOpen = true;
    },
    onSaved() {
      this.$q.notify({ type: 'positive', message: '배지 정의를 저장했습니다.', position: 'top' });
      this.load();
    },
    /** 사용 중지하면 새로 발급되지 않는다 — 누르는 즉시 바뀌고 알림도 없던 것을 묻고 알린다 */
    async toggleActive(row) {
      const turningOff = row.active;
      if (turningOff) {
        const ok = await confirmDialog(this.$q, {
          title: '배지 사용 중지',
          message: `"${row.name}" 배지를 사용 중지합니다. 사용 중지한 배지는 새로 발급되지 않습니다.`,
          ok: '사용 중지',
          color: 'negative',
          cancel: '닫기',
        });
        if (!ok) return;
      }
      try {
        const updated = await setBadgeDefinitionActive(row.id, !row.active);
        this.rows = this.rows.map((item) => (item.id === row.id ? { ...item, ...updated } : item));
        this.$q.notify({
          type: 'positive',
          message: `"${row.name}" 배지를 ${turningOff ? '사용 중지했습니다' : '다시 사용합니다'}.`,
          position: 'top',
        });
      } catch (error) {
        this.notifyError(error, '상태를 바꾸지 못했습니다.');
      }
    },
    // 발급된 배지가 있으면 서버가 409 로 막는다 — 누르기 전에 세어 보고, 못 지우면 이유와 지금 상태를 먼저 알린다
    async confirmRemove(row) {
      const issued = await this.countIssued(row.id);
      if (issued > 0) {
        this.explainInUse(row, issued);
        return;
      }
      this.$q
        .dialog({
          title: '배지 정의 삭제',
          message: `"${row.name}" 정의를 지웁니다. 되돌릴 수 없습니다.`,
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'negative', label: '삭제' },
          persistent: true,
        })
        .onOk(() => this.remove(row));
    },
    /** fallback: 못 셀 때 돌려줄 값 — 삭제 확인은 0(시도하고 서버 409 로 안내), 목록 칸은 null('—') */
    async countIssued(definitionId, fallback = 0) {
      try {
        const result = await searchAdminBadges({ definitionId, page: 0, size: 1 });
        return result.page?.totalElements ?? result.data?.length ?? 0;
      } catch {
        return fallback;
      }
    },
    // 발급 기록(폐기 포함)이 하나라도 있으면 지울 수 없다 — 운영에서 발급 기록을 지우지 않는다(백엔드 c3f7982)
    explainInUse(row, issued) {
      const count = issued ? ` (${issued}개)` : '';
      const state = row.active
        ? '사용 중지하면 도감·포트폴리오 목록에서 숨겨집니다.'
        : '이미 사용 중지되어 도감·포트폴리오 목록에서는 숨겨져 있습니다.';
      this.$q
        .dialog({
          title: '삭제할 수 없는 배지입니다',
          message: `"${row.name}"은(는) 발급 기록이 있어${count} 삭제할 수 없습니다. ${state}`,
          cancel: row.active ? { flat: true, noCaps: true, label: '닫기' } : false,
          ok: row.active
            ? { unelevated: true, noCaps: true, color: 'primary', label: '사용 중지' }
            : { unelevated: true, noCaps: true, color: 'primary', label: '확인' },
        })
        .onOk(() => {
          if (row.active) this.toggleActive(row);
        });
    },
    async remove(row) {
      try {
        await deleteBadgeDefinition(row.id);
        this.rows = this.rows.filter((item) => item.id !== row.id);
        this.$q.notify({ type: 'positive', message: '배지 정의를 지웠습니다.', position: 'top' });
      } catch (error) {
        if (error?.payload?.code === 'BADGE_DEFINITION_IN_USE') {
          this.explainInUse(row, 0);
          return;
        }
        this.notifyError(error, '삭제하지 못했습니다.');
      }
    },
  },
};
</script>

<style scoped>
.bd-badge {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.bd-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--db-text-sub);
}

.bd-rule {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
  word-break: keep-all;
}

.bd-issued {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  min-height: 36px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--db-accent);
  text-decoration: none;
}

.bd-issued:hover,
.bd-issued:focus-visible {
  text-decoration: underline;
}

.bd-muted {
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.bd-danger {
  color: var(--db-status-pink);
}
</style>
