<template>
  <!-- 행별 처리 — 지금 상태에서 할 수 있는 것만 보인다(일괄 처리와 같은 기준) -->
  <div class="completion-row-actions">
    <!-- 끝나지 않은 회차가 있으면 꺼 두고 이유를 툴팁으로(꺼진 q-btn 은 툴팁이 안 떠 span 에 단다) -->
    <!-- 판정(계산, 언제든 다시)·확정(최종, 강사는 못 되돌림)이 똑같이 생겨 헷갈렸다 —
         판정은 테두리(다시 판정은 글자 단추), 확정만 초록 바탕 + ✓ -->
    <span v-if="canProcess && canEvaluate" class="completion-row-actions__host">
      <q-btn
        :outline="!row.completion"
        :flat="!!row.completion"
        no-caps
        class="completion-row-actions__btn"
        color="primary"
        :icon="row.completion ? 'refresh' : 'fact_check'"
        :label="row.completion ? '다시 판정' : '출석으로 판정'"
        :loading="row.busy === 'evaluate'"
        :disable="locked || !!evaluateBlockedReason"
        @click="$emit('evaluate', row)"
      />
      <q-tooltip v-if="evaluateBlockedReason">{{ evaluateBlockedReason }}</q-tooltip>
    </span>
    <!-- 사유를 적어 미이수로 판정 — 사유 입력 칸이 없어 [출석으로 판정]만 할 수 있었다(QA CMP-03).
         출석률 미달은 서버가 사유 없이도 미이수로 판정한다 -->
    <q-btn
      v-if="canProcess && canEvaluate && !isNotCompleted"
      flat
      no-caps
      class="completion-row-actions__btn"
      color="negative"
      label="미이수 처리"
      :disable="locked || !!evaluateBlockedReason"
      @click="$emit('fail', row)"
    />
    <!-- 확정할 사람(이수 가능)에게만 나오므로 초록 바탕이 많이 쌓이지 않는다 -->
    <q-btn
      v-if="canProcess && canConfirm"
      unelevated
      no-caps
      class="completion-row-actions__btn"
      color="positive"
      icon="check"
      label="이수 확정"
      :loading="row.busy === 'confirm'"
      :disable="locked"
      @click="$emit('confirm', row)"
    />
    <!-- 꺼진 q-btn 은 마우스 이벤트를 받지 않아 안의 툴팁이 뜨지 않는다 — 감싼 span 에 단다 -->
    <span v-if="showIssue" class="completion-row-actions__host">
      <q-btn
        outline
        no-caps
        class="completion-row-actions__btn"
        color="primary"
        :label="isRevoked ? '재발급' : '수료증 발급'"
        :loading="row.busy === 'issue'"
        :disable="locked || !canIssueNow"
        @click="$emit('issue', row)"
      />
      <q-tooltip v-if="!canIssueNow">{{ issueBlockedReason }}</q-tooltip>
    </span>
    <q-btn
      v-if="canRevert && isCompleted"
      outline
      no-caps
      class="completion-row-actions__btn"
      color="negative"
      label="확정 취소"
      :disable="locked"
      @click="$emit('revert', row)"
    />
  </div>
</template>

<script>
import {
  canConfirmRow,
  canEvaluateRow,
  canIssueRow,
} from '@/components/completion/completion-roster-rows';

export default {
  name: 'CompletionRosterActions',
  props: {
    row: { type: Object, required: true },
    canIssue: { type: Boolean, default: false },
    canRevert: { type: Boolean, default: false },
    /** 출석 평가·이수 확정 버튼 — 기관 발급 전용 화면에선 끈다(강사 몫) */
    canProcess: { type: Boolean, default: true },
    /** 일괄 처리 중에는 행 버튼을 잠근다 — 같은 수강생에 요청이 겹치지 않게 */
    disable: { type: Boolean, default: false },
    /** 끝나지 않은 회차 때문에 평가할 수 없는 이유 — 있으면 [출석 평가]를 끈다 */
    evaluateBlockedReason: { type: String, default: '' },
  },
  emits: ['evaluate', 'fail', 'confirm', 'issue', 'revert'],
  computed: {
    locked() {
      return this.disable || !!this.row.busy;
    },
    isCompleted() {
      return this.row.completion?.status === 'COMPLETED';
    },
    /** 수료증이 폐기됐으면 같은 발급 버튼이 '재발급'(사용자 결정 10-21) */
    isRevoked() {
      return this.row.credential?.status === 'REVOKED';
    },
    /** 이미 미이수면 [다시 판정]만 — [미이수 처리]·꺼진 [수료증 발급]까지 세 모양이 한 줄에 섞였다(QA) */
    isNotCompleted() {
      return this.row.completion?.status === 'NOT_COMPLETED';
    },
    canEvaluate() {
      return canEvaluateRow(this.row);
    },
    canConfirm() {
      return canConfirmRow(this.row);
    },
    canIssueNow() {
      return canIssueRow(this.row);
    },
    /** 이수 가능·완료 행에만 발급 버튼 — 확정 전이면 꺼진 채로 이유를 알린다. 미이수·취소는 발급할 일이 없다 */
    showIssue() {
      return this.canIssue && ['ELIGIBLE', 'COMPLETED'].includes(this.row.completion?.status);
    },
    issueBlockedReason() {
      if (!this.isCompleted) return '이수를 확정한 뒤 발급할 수 있습니다.';
      return '이미 발급했거나 발급 처리 중입니다.';
    },
  },
};
</script>

<style scoped>
.completion-row-actions__host {
  display: inline-flex;
}

.completion-row-actions {
  display: inline-flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

/* dense 버튼은 글자가 테두리에 붙어 답답했다 — 좌우 여백과 높이를 둔다 */
.completion-row-actions__btn {
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
  white-space: nowrap;
}
</style>
