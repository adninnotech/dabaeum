<template>
  <!-- 수강 신청 처리 표 — 강사 '수강 신청 관리'와 기관 '수강 관리'가 같이 쓴다.
       고른 신청 일괄 승인·거절, 폰(xs)에선 한 신청을 카드 한 장으로(6칸 표가 옆으로 밀려 버튼이 화면 밖에 있었다) -->
  <div>
    <div class="bulk-bar">
      <span class="bulk-bar__summary" role="status">
        {{ rows.length }}건<template v-if="selected.length"> · {{ selected.length }}건 선택</template>
      </span>
      <!-- 폰 카드 보기엔 표 머리 체크박스가 없다 — 같은 전체 선택을 여기서 -->
      <q-checkbox
        v-if="$q.screen.xs && decidableIds.length"
        dense
        label="전체 선택"
        :model-value="headerState"
        @update:model-value="toggleAll"
      />
      <template v-if="selected.length">
        <q-btn
          unelevated
          no-caps
          color="primary"
          icon="check"
          label="선택 승인"
          :loading="bulkSaving"
          @click="$emit('bulk-approve')"
        />
        <q-btn
          outline
          no-caps
          color="negative"
          icon="close"
          label="선택 거절"
          :disable="bulkSaving"
          @click="$emit('bulk-reject')"
        />
      </template>
    </div>
    <slot name="note" />

    <q-table
      flat
      bordered
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      :no-data-label="emptyText"
      :pagination="{ rowsPerPage: 10 }"
      :rows-per-page-options="[10, 20, 50]"
      rows-per-page-label="한 번에 보기"
      :pagination-label="(first, last, total) => `총 ${total}건 중 ${first}-${last}`"
    >
      <!-- 표 기본 '데이터 없음'은 경고(⚠) 아이콘이라 처리할 일이 없는 것도 오류처럼 보였다 -->
      <template #no-data="{ message }">
        <div class="table-empty">{{ message }}</div>
      </template>
      <template #header-cell-select="props">
        <q-th :props="props" auto-width>
          <q-checkbox
            dense
            :model-value="headerState"
            :disable="!decidableIds.length"
            aria-label="처리할 수 있는 신청 전체 선택"
            @update:model-value="toggleAll"
          />
        </q-th>
      </template>
      <template #body-cell-select="props">
        <q-td :props="props" auto-width>
          <q-checkbox
            v-if="isDecidable(props.row)"
            dense
            :model-value="selected.includes(props.row.id)"
            :aria-label="`${props.row.applicantLabel} 선택`"
            @update:model-value="(value) => toggleOne(props.row.id, value)"
          />
        </q-td>
      </template>
      <!-- 대기는 연한 뱃지 — 신청(바로 처리)과 같은 주황 꽉 찬 뱃지라 글자를 읽기 전엔 구분이 안 됐다 -->
      <template #body-cell-status="props">
        <q-td :props="props">
          <StatusBadge :status="props.row.status" :label="props.row.statusLabel" :soft="isWaitlisted(props.row)" />
          <div v-if="cancellationReason(props.row)" class="text-caption text-grey-7">
            사유: {{ cancellationReason(props.row) }}
          </div>
        </q-td>
      </template>
      <!-- 처리 끝난 신청엔 꺼진 버튼을 늘어놓지 않는다. 되돌릴 수 없는 '신청 취소'·'수강 철회'는 승인·거절 옆이 아니라 ⋮ 메뉴로 -->
      <template #body-cell-action="props">
        <q-td :props="props">
          <div class="row-actions">
            <EnrollmentDecisionButtons :row="props.row" :busy-id="busyId" @approve="onApprove" @reject="onReject" />
            <q-btn
              v-if="rowMenu(props.row).length"
              flat
              round
              dense
              size="sm"
              icon="more_vert"
              :aria-label="`${props.row.applicantLabel} 신청 메뉴`"
            >
              <q-menu>
                <q-list dense>
                  <q-item
                    v-for="item in rowMenu(props.row)"
                    :key="item.event"
                    v-close-popup
                    clickable
                    class="text-negative"
                    @click="$emit(item.event, props.row)"
                  >
                    <q-item-section>{{ item.label }}</q-item-section>
                  </q-item>
                </q-list>
              </q-menu>
            </q-btn>
          </div>
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <div class="app-card">
            <q-checkbox
              v-if="isDecidable(props.row)"
              dense
              :model-value="selected.includes(props.row.id)"
              :aria-label="`${props.row.applicantLabel} 선택`"
              @update:model-value="(value) => toggleOne(props.row.id, value)"
            />
            <div class="app-card__body">
              <!-- 되돌릴 수 없는 '신청 취소'·'수강 철회'는 웹처럼 ⋮ 메뉴로 — 폰에서만 빨간 버튼이 카드마다 한 줄을 차지했다 -->
              <div class="app-card__head">
                <span class="app-card__name">{{ props.row.applicantLabel }}</span>
                <q-btn
                  v-if="rowMenu(props.row).length"
                  flat
                  round
                  dense
                  size="sm"
                  icon="more_vert"
                  :aria-label="`${props.row.applicantLabel} 신청 메뉴`"
                >
                  <q-menu>
                    <q-list dense>
                      <q-item
                        v-for="item in rowMenu(props.row)"
                        :key="item.event"
                        v-close-popup
                        clickable
                        class="text-negative"
                        @click="$emit(item.event, props.row)"
                      >
                        <q-item-section>{{ item.label }}</q-item-section>
                      </q-item>
                    </q-list>
                  </q-menu>
                </q-btn>
              </div>
              <div class="app-card__course">{{ props.row.courseTitle }}</div>
              <div class="app-card__meta">
                <StatusBadge :status="props.row.status" :label="props.row.statusLabel" :soft="isWaitlisted(props.row)" />
                <span class="num">신청 {{ props.row.appliedAtText }}</span>
              </div>
              <div v-if="cancellationReason(props.row)" class="text-caption text-grey-7">
                사유: {{ cancellationReason(props.row) }}
              </div>
              <div v-if="isDecidable(props.row)" class="app-card__actions">
                <EnrollmentDecisionButtons :row="props.row" :busy-id="busyId" @approve="onApprove" @reject="onReject" />
              </div>
            </div>
          </div>
        </div>
      </template>
    </q-table>
  </div>
</template>

<script>
import EnrollmentDecisionButtons from '@/components/console/EnrollmentDecisionButtons.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { DECIDABLE_STATUSES, enrollmentCancellationReason, isWaitlistBadgeSoft } from '@/utils/enrollment-decision';
import { WITHDRAWABLE_STATUSES } from '@/utils/enrollment-withdraw';

// 신청 취소(POST /cancel)는 승인 전 신청만 — 승인 건은 서버가 거부한다. 승인 건은 '수강 철회'(POST /withdraw)
const CANCELLABLE_STATUSES = ['APPLIED', 'WAITLISTED'];

// 접수 상태 정렬 순서 — 처리할 것(신청·대기)이 먼저, 끝난 것은 뒤로
const STATUS_ORDER = ['APPLIED', 'WAITLISTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN'];
const statusRank = (status) => {
  const rank = STATUS_ORDER.indexOf(status);
  return rank === -1 ? STATUS_ORDER.length : rank;
};
const byKorean = (a, b) => String(a || '').localeCompare(String(b || ''), 'ko');

// 표 머리를 눌러 정렬한다 — 정렬이 없어 접수 상태·신청자 순으로 볼 수 없었다(QA ENR-17)
const COLUMNS = [
  { name: 'select', label: '', field: 'id', align: 'center' },
  { name: 'applicantLabel', label: '신청자', field: 'applicantLabel', align: 'left', sortable: true, sort: byKorean },
  { name: 'courseTitle', label: '강의명', field: 'courseTitle', align: 'left', sortable: true, sort: byKorean },
  {
    name: 'status',
    label: '상태',
    field: 'status',
    align: 'center',
    sortable: true,
    sort: (a, b) => statusRank(a) - statusRank(b),
  },
  {
    name: 'appliedAt',
    label: '신청일',
    // 정렬은 원래 시각으로, 보이는 건 KST 문구
    field: (row) => row.appliedAt || row.createdAt || '',
    format: (value, row) => row.appliedAtText,
    align: 'left',
    sortable: true,
  },
  // 처리된 신청은 버튼 대신 언제 처리됐는지를 본다
  { name: 'processedAt', label: '처리일', field: 'processedAtText', align: 'left' },
  { name: 'action', label: '처리', field: 'id', align: 'right' },
];

export default {
  name: 'EnrollmentDecisionTable',
  components: { EnrollmentDecisionButtons, StatusBadge },
  props: {
    /** { id, applicantLabel, courseTitle, status, statusLabel, appliedAtText, processedAtText, canDecide } */
    rows: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    emptyText: { type: String, default: '' },
    busyId: { type: String, default: null },
    bulkSaving: { type: Boolean, default: false },
    /** 고른 신청 id — v-model:selected */
    selected: { type: Array, default: () => [] },
    /** 신청 취소(기관·관리자) 메뉴를 보일지 */
    cancellable: { type: Boolean, default: false },
    /** 수강 철회 메뉴를 보일지 — 행의 canDecide(기관·관리자, 이 강의 주강사)일 때만 */
    withdrawable: { type: Boolean, default: false },
  },
  emits: ['update:selected', 'approve', 'reject', 'cancel', 'withdraw', 'bulk-approve', 'bulk-reject'],
  data() {
    return { columns: COLUMNS };
  },
  computed: {
    decidableIds() {
      return this.rows.filter(this.isDecidable).map((row) => row.id);
    },
    headerState() {
      const picked = this.decidableIds.filter((id) => this.selected.includes(id)).length;
      if (!picked) return false;
      return picked === this.decidableIds.length ? true : null;
    },
  },
  watch: {
    // 필터를 바꿔 안 보이게 된 신청은 선택에서 뺀다
    rows() {
      const visible = new Set(this.decidableIds);
      const kept = this.selected.filter((id) => visible.has(id));
      if (kept.length !== this.selected.length) this.$emit('update:selected', kept);
    },
  },
  methods: {
    cancellationReason: enrollmentCancellationReason,
    isWaitlisted: isWaitlistBadgeSoft,
    isDecidable(row) {
      return row.canDecide && DECIDABLE_STATUSES.includes(row.status);
    },
    /** ⋮ 메뉴 항목 — 되돌릴 수 없는 일만 모은다. 비면 ⋮ 를 그리지 않는다 */
    rowMenu(row) {
      const items = [];
      if (this.cancellable && CANCELLABLE_STATUSES.includes(row.status)) items.push({ event: 'cancel', label: '신청 취소' });
      if (this.withdrawable && row.canDecide && WITHDRAWABLE_STATUSES.includes(row.status)) {
        items.push({ event: 'withdraw', label: '수강 철회' });
      }
      return items;
    },
    toggleOne(id, value) {
      const rest = this.selected.filter((item) => item !== id);
      this.$emit('update:selected', value ? [...rest, id] : rest);
    },
    toggleAll() {
      this.$emit('update:selected', this.headerState === true ? [] : [...this.decidableIds]);
    },
    onApprove(row) {
      this.$emit('approve', row);
    },
    onReject(row) {
      this.$emit('reject', row);
    },
  },
};
</script>

<style scoped>
.bulk-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  margin-bottom: 8px;
}

.bulk-bar__summary {
  flex: 1 1 auto;
  font-size: 14px;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.app-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--db-border);
}

.app-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.table-empty {
  width: 100%;
  padding: 24px 0;
  text-align: center;
  font-size: 14px;
  color: var(--db-text-sub);
}

.app-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.app-card__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.app-card__course {
  font-size: 13px;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

.app-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--db-text-sub);
}

.app-card__actions {
  display: flex;
  flex-wrap: wrap;
  align-self: flex-end;
  gap: 4px;
}
</style>
