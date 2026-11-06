<template>
  <q-table
    class="attendance-roster"
    flat
    bordered
    row-key="key"
    :rows="rows"
    :columns="columns"
    :loading="loading"
    :pagination="{ rowsPerPage: 0 }"
    :rows-per-page-options="[0]"
    :pagination-label="(first, last, total) => `총 ${total}명 중 ${first}-${last}`"
    :table-row-class-fn="rowClass"
    :grid="$q.screen.xs"
    :no-data-label="emptyLabel"
  >
    <template #header-cell-select="props">
      <q-th :props="props" auto-width>
        <!-- 기록이 이미 있는 행은 일괄 등록 대상이 아니다 — 머리 체크는 미기록 행만 다 고른다 -->
        <!-- 고를 수강생이 없으면(전원 기록됨) 꺼진 체크가 눌리는 것처럼 보였다 — 아예 두지 않는다 -->
        <q-checkbox
          v-if="selectableKeys.length"
          dense
          :model-value="headerState"
          aria-label="미기록 수강생 전체 선택"
          @update:model-value="toggleAll"
        />
      </q-th>
    </template>
    <template #body-cell-select="props">
      <q-td :props="props" auto-width>
        <q-checkbox
          v-if="!props.row.attendanceId"
          dense
          :model-value="selectedIds.includes(props.row.enrollmentId)"
          :aria-label="`${props.row.displayName} 선택`"
          @update:model-value="(checked) => toggleOne(props.row.enrollmentId, checked)"
        />
      </q-td>
    </template>
    <!-- 상태 + 방법·시각 한 칸 — 따로 있던 '방법'·'출석 시각' 칸은 대부분 '-'라 표만 넓혔다 -->
    <template #body-cell-status="props">
      <q-td :props="props">
        <div class="roster-status">
          <StatusBadge v-if="props.row.status" :status="props.row.status" />
          <!-- 상태값이 없으면 StatusBadge 가 회색(--db-status-gray)으로 그린다 — 색을 직접 적지 않는다 -->
          <StatusBadge v-else status="" label="미기록" soft />
          <span v-if="props.row.attendanceId" class="roster-status__meta">{{ recordMeta(props.row) }}</span>
        </div>
      </q-td>
    </template>
    <template #body-cell-actions="props">
      <q-td :props="props">
        <!-- 기록된 행 — 미기록 행처럼 네 칸을 그대로 두고 지금 상태만 켠다. 다른 칸을 누르면 정정 창(사유).
             예전엔 [정정 ▾] → 항목 → 창 세 단계라, 고칠 일이 더 잦은데 기록보다 멀었다 -->
        <div v-if="props.row.attendanceId" class="recorded-actions">
          <AttendanceStatusSegment
            compact
            :model-value="props.row.status"
            :label="`${props.row.displayName} 출결 정정`"
            :disabled="Boolean(busyKey) || disable"
            @update:model-value="(status) => $emit('adjust', props.row, status)"
          />
          <q-btn flat no-caps color="primary" label="상세" class="recorded-actions__detail" @click="$emit('detail', props.row)" />
        </div>
        <!-- 미기록 — 창을 열지 않고 한 번에 기록(지금 시각). 시간을 따로 정할 때만 ⋯ 메뉴의 창 -->
        <div v-else class="quick-record" role="group" :aria-label="`${props.row.displayName} 출결 기록`">
          <q-btn
            v-for="option in QUICK_OPTIONS"
            :key="option.value"
            outline
            no-caps
            :color="option.color || undefined"
            :label="option.label"
            class="quick-record__btn"
            :class="option.tone && `quick-record__btn--${option.tone}`"
            :loading="busyKey === props.row.key && busyStatus === option.value"
            :disable="Boolean(busyKey) || disable"
            @click="$emit('quick-record', props.row, option.value)"
          />
          <q-btn flat round dense icon="more_horiz" color="grey-7" :aria-label="`${props.row.displayName} 시간 지정해 기록`">
            <q-menu>
              <q-list dense>
                <q-item v-close-popup clickable @click="$emit('record', props.row)">
                  <q-item-section>시간 지정해 기록</q-item-section>
                </q-item>
              </q-list>
            </q-menu>
          </q-btn>
        </div>
      </q-td>
    </template>
    <!-- 폰(xs): 6칸 표가 옆으로 밀려 [등록]·[정정]이 화면 밖에 있었다 — 한 사람을 카드 한 장으로 -->
    <template #item="props">
      <div class="col-12">
        <div class="roster-card" :class="rowClass(props.row)">
          <q-checkbox
            v-if="!props.row.attendanceId"
            dense
            :model-value="selectedIds.includes(props.row.enrollmentId)"
            :aria-label="`${props.row.displayName} 선택`"
            @update:model-value="(checked) => toggleOne(props.row.enrollmentId, checked)"
          />
          <div class="roster-card__body">
            <div class="roster-card__name">{{ props.row.displayName }}</div>
            <div class="roster-card__meta">
              <StatusBadge v-if="props.row.status" :status="props.row.status" />
              <StatusBadge v-else status="" label="미기록" soft />
              <span v-if="props.row.attendanceId" class="num">{{ recordMeta(props.row) }}</span>
            </div>
          </div>
          <div class="roster-card__actions">
            <q-btn v-if="props.row.attendanceId" flat no-caps color="primary" label="상세" class="recorded-actions__detail" @click="$emit('detail', props.row)" />
          </div>
          <!-- 폰: 기록된 행도 네 칸을 한 줄 전체 폭으로 — 지금 상태가 켜져 있고 다른 칸이 정정 -->
          <AttendanceStatusSegment
            v-if="props.row.attendanceId"
            :model-value="props.row.status"
            :label="`${props.row.displayName} 출결 정정`"
            :disabled="Boolean(busyKey) || disable"
            class="roster-card__seg"
            @update:model-value="(status) => $emit('adjust', props.row, status)"
          />
          <!-- 폰: 미기록이면 이름 아래 네 단추를 한 줄 전체 폭으로 -->
          <div v-if="!props.row.attendanceId" class="quick-record quick-record--card">
            <q-btn
              v-for="option in QUICK_OPTIONS"
              :key="option.value"
              outline
              no-caps
              :color="option.color || undefined"
              :label="option.label"
              class="quick-record__btn"
              :class="option.tone && `quick-record__btn--${option.tone}`"
              :loading="busyKey === props.row.key && busyStatus === option.value"
              :disable="Boolean(busyKey) || disable"
              @click="$emit('quick-record', props.row, option.value)"
            />
          </div>
        </div>
      </div>
    </template>
  </q-table>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import AttendanceStatusSegment from '@/components/attendance/AttendanceStatusSegment.vue';
import { ATTENDANCE_METHOD_LABEL, STATUS_LABEL } from '@/utils/status';
import { formatTimeKst } from '@/utils/datetime';

// 미기록 행 한 번에 기록 — 자주 쓰는 순서(출석·지각·결석·공결). 색은 상태 배지와 같은 계열
const QUICK_OPTIONS = [
  { value: 'PRESENT', color: 'positive' },
  // 지각은 토큰 색(진한 주황)을 클래스로 — Quasar 주황 계열은 대비가 낮거나 결석 빨강과 구분이 안 됐다
  { value: 'LATE', color: '', tone: 'late' },
  { value: 'ABSENT', color: 'negative' },
  { value: 'EXCUSED', color: 'primary' },
].map((option) => ({ ...option, label: STATUS_LABEL[option.value] }));

/**
 * 회차 출결 명단 — 승인된 수강생 전원을 보이고, 아직 기록이 없는 행은 '미기록' 으로 둔다.
 * 기록이 있는 행만 보이던 표로는 누가 빠졌는지 알 수 없었다.
 * rows: { key, enrollmentId, userId, displayName, attendanceId, status, attendanceMethod, checkedAt }
 */
export default {
  name: 'AttendanceRosterTable',
  components: { StatusBadge, AttendanceStatusSegment },
  props: {
    rows: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 고른 미기록 행의 enrollmentId 목록 */
    selectedIds: { type: Array, default: () => [] },
    /** 강조할 행 key — 수강생 목록에서 ?userId 로 넘어온 학생 */
    highlightKey: { type: String, default: '' },
    /** 방금 정정한 행 attendanceId — 잠깐 깜빡여 어느 줄이 바뀌었는지 보인다 */
    flashId: { type: String, default: '' },
    /** 빈 명단 문구 — 조회 실패일 때 '수강생이 없습니다'로 잘못 알리지 않게 화면이 넘긴다 */
    emptyLabel: { type: String, default: '이 강의에 승인된 수강생이 없습니다.' },
    /** 한 번에 기록 중인 행 key·상태 — 그 단추에 도는 표시, 나머지는 잠근다 */
    busyKey: { type: String, default: '' },
    busyStatus: { type: String, default: '' },
    /** 일괄 기록 중 — 행 단추를 잠근다 */
    disable: { type: Boolean, default: false },
  },
  /** quick-record(row, status): 미기록 행을 지금 시각으로 바로 기록 */
  emits: ['update:selectedIds', 'detail', 'adjust', 'record', 'quick-record'],
  data() {
    return { QUICK_OPTIONS };
  },
  computed: {
    columns() {
      return [
        { name: 'select', label: '', field: 'key', align: 'center' },
        // 이름·상태 칸 폭을 정해 기록 단추가 이름 가까이 오게 — 넓은 화면에서 1,000px 떨어져 있었다
        { name: 'displayName', label: '수강생', field: 'displayName', align: 'left', sortable: true, style: 'width: 240px', headerStyle: 'width: 240px' },
        { name: 'status', label: '상태', field: 'status', align: 'left', style: 'width: 240px', headerStyle: 'width: 240px' },
        { name: 'actions', label: '기록', field: 'key', align: 'left' },
      ];
    },
    selectableKeys() {
      return this.rows.filter((row) => !row.attendanceId).map((row) => row.enrollmentId);
    },
    headerState() {
      const picked = this.selectableKeys.filter((id) => this.selectedIds.includes(id)).length;
      if (!picked) return false;
      return picked === this.selectableKeys.length ? true : null;
    },
  },
  watch: {
    // 명단이 늦게 오므로 행이 그려진 뒤에 강조 행으로 스크롤한다
    highlightKey() {
      this.scrollToHighlight();
    },
    rows() {
      this.scrollToHighlight();
    },
  },
  methods: {
    /** 'QR · 10:03' — 기록 방법과 시각을 상태 옆 작은 글자로 */
    recordMeta(row) {
      const method = ATTENDANCE_METHOD_LABEL[row.attendanceMethod] || row.attendanceMethod || '';
      const time = row.checkedAt ? formatTimeKst(row.checkedAt) : '';
      return [method, time].filter(Boolean).join(' · ');
    },
    rowClass(row) {
      return [
        row.key === this.highlightKey ? 'attendance-roster__row--highlight' : '',
        this.flashId && row.attendanceId === this.flashId ? 'attendance-roster__row--flash' : '',
      ]
        .filter(Boolean)
        .join(' ');
    },
    toggleOne(enrollmentId, checked) {
      const rest = this.selectedIds.filter((id) => id !== enrollmentId);
      this.$emit('update:selectedIds', checked ? [...rest, enrollmentId] : rest);
    },
    toggleAll() {
      // 다 골라져 있으면 비우고, 아니면 미기록 전원을 고른다
      this.$emit('update:selectedIds', this.headerState === true ? [] : [...this.selectableKeys]);
    },
    scrollToHighlight() {
      if (!this.highlightKey) return;
      this.$nextTick(() => {
        const el = this.$el?.querySelector?.('.attendance-roster__row--highlight');
        if (el) el.scrollIntoView({ block: 'center', behavior: 'smooth' });
      });
    },
  },
};
</script>

<style scoped>
/* 기록 시각은 행마다 자릿수가 달라 세로줄이 흔들렸다 — 숫자 폭을 고정한다 */
.attendance-roster :deep(td.num) {
  font-variant-numeric: tabular-nums;
}

/* Quasar q-table 기본 글자(머리 12px·칸 13px)가 옆 버튼·필터(14px)와 다른 콘솔 표보다 작아 섞여 보였다 —
   운영 콘솔 목록 크기로 맞춘다 */
.attendance-roster :deep(thead th) {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
}

.attendance-roster :deep(tbody td) {
  font-size: 15px;
  color: var(--db-text-body);
}

.attendance-roster :deep(.q-table__bottom) {
  font-size: 13px;
  color: var(--db-text-sub);
}

.attendance-roster :deep(.attendance-roster__row--highlight) {
  background: var(--db-accent-soft);
}

/* 방금 정정한 행 — 잠깐 옅은 파랑이 빠진다 */
.attendance-roster :deep(.attendance-roster__row--flash) {
  animation: roster-row-flash 1.6s ease-out;
}

@keyframes roster-row-flash {
  0%,
  35% {
    background: var(--db-accent-soft);
  }

  100% {
    background: transparent;
  }
}

@media (prefers-reduced-motion: reduce) {
  .attendance-roster :deep(.attendance-roster__row--flash) {
    animation: none;
    background: var(--db-accent-soft);
  }
}

.recorded-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.recorded-actions__detail {
  min-height: 44px;
  font-size: 14px;
  font-weight: 600;
}

.roster-card__seg {
  display: grid;
  flex-basis: 100%;
}

.roster-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.roster-status__meta {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.quick-record {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.quick-record__btn {
  min-width: 56px;
  min-height: 36px;
  padding: 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.quick-record__btn--late {
  color: var(--db-status-orange-text);
}

/* 폰 카드 — 네 단추가 한 줄 전체 폭 */
.quick-record--card {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  flex-basis: 100%;
}

.quick-record--card .quick-record__btn {
  min-height: 44px;
}

/* 폰 카드 — 이름 한 줄, 상태·방법·시각 한 줄, 동작은 오른쪽 */
.roster-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid var(--db-border);
  background: var(--db-bg-white);
}

.roster-card__body {
  flex: 1;
  min-width: 0;
}

.roster-card__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.roster-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.roster-card__meta .num {
  font-variant-numeric: tabular-nums;
}

/* [상세]가 위, [정정 ▾]가 아래 줄로 따로 놓였다 — 이름 줄 높이에 나란히 */
.roster-card__actions {
  display: flex;
  align-items: center;
  align-self: flex-start;
  flex-shrink: 0;
  gap: 2px;
}
</style>
