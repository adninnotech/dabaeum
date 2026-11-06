<template>
  <!-- 이수 상태 한 칸 — 기록이 없으면(404) '평가 전', 조회가 실패하면 그 사유를 보인다 -->
  <q-spinner v-if="!row.completionLoaded" size="16px" color="grey-6" />
  <!-- 사유를 말풍선에만 두면 폰(카드 보기)에서는 볼 길이 없었다 — 배지 아래 한 줄로 늘 보인다 -->
  <div v-else-if="row.completionError" class="roster-status">
    <span class="text-negative text-caption">조회 실패</span>
    <div class="roster-status__reason roster-status__reason--error">{{ row.completionError }}</div>
  </div>
  <!-- 상태값이 없으면 StatusBadge 가 회색(--db-status-gray 와 같은 값)으로 그린다 — 주황 평가대기와 섞이지 않게 -->
  <StatusBadge v-else-if="!row.completion" label="판정 전" />
  <div v-else class="roster-status">
    <StatusBadge
      :status="row.completion.status"
      :label="COMPLETION_STATUS_LABEL[row.completion.status] || ''"
    />
    <div v-if="row.completion.failureReason" class="roster-status__reason">
      {{ row.completion.failureReason }}
    </div>
  </div>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import { COMPLETION_STATUS_LABEL } from '@/utils/status';

export default {
  name: 'CompletionRosterStatus',
  components: { StatusBadge },
  props: {
    row: { type: Object, required: true },
  },
  data() {
    return { COMPLETION_STATUS_LABEL };
  },
};
</script>

<style scoped>
.roster-status {
  display: inline-flex;
  flex-direction: column;
  /* 왼쪽 맞춤 — 가운데면 사유 줄이 긴 '미이수' 배지만 안쪽으로 밀려 다른 행과 어긋났다(QA) */
  align-items: flex-start;
  gap: 2px;
}

/* 사유가 길어도 표 칸이 끝없이 넓어지지 않게 — 줄바꿈해 보인다 */
.roster-status__reason {
  max-width: 220px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--db-text-sub);
  white-space: normal;
  text-align: left;
  word-break: keep-all;
}

.roster-status__reason--error {
  color: var(--db-status-pink);
}
</style>
