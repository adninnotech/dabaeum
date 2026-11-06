<template>
  <!-- 수료증 한 건의 처리 — 표 칸과 폰 카드가 같이 쓴다.
       서버는 폐기·재발급·배지 발급을 발급완료(ISSUED)일 때만 받는다(그 밖엔 409) — 다른 상태엔 버튼을 그리지 않는다.
       되돌릴 수 없는 [폐기]가 맨 앞에 같은 무게로 있었다 — 자주 쓰는 [재발급]만 꺼내고 나머지는 ⋮ -->
  <div v-if="row.status === 'ISSUED'" class="credential-actions">
    <q-btn flat dense no-caps color="primary" label="재발급" @click="$emit('reissue', row)" />
    <q-btn
      flat
      round
      dense
      size="sm"
      icon="more_vert"
      :loading="busy"
      :aria-label="`${row.learnerLabel || '수료증'} 메뉴`"
    >
      <q-menu>
        <!-- 좁은 메뉴에서 '수료증 폐기'가 두 줄로 꺾였다 -->
        <q-list dense class="credential-menu">
          <q-item v-close-popup clickable @click="$emit('badge', row)">
            <q-item-section>배지 발급</q-item-section>
          </q-item>
          <q-separator />
          <q-item v-close-popup clickable class="text-negative" @click="$emit('revoke', row)">
            <q-item-section>수료증 폐기</q-item-section>
          </q-item>
        </q-list>
      </q-menu>
    </q-btn>
  </div>
  <div v-else-if="row.status === 'FAILED'" class="credential-failure">
    <span v-if="failureText" class="credential-failure__reason">{{ failureText }}</span>
    <!-- 같은 이수로 다시 발급한다 — 이수 처리 화면까지 가지 않아도 되게 행에서 바로 -->
    <q-btn
      v-if="row.retryable"
      flat
      dense
      no-caps
      color="primary"
      label="다시 발급"
      :loading="issuing"
      :disable="issueLocked"
      @click="$emit('issue', row, 'retry')"
    />
    <span v-else class="muted-note">이후 다시 발급된 건이 있습니다.</span>
  </div>
  <q-btn
    v-else-if="row.status === notIssued"
    unelevated
    dense
    no-caps
    color="primary"
    label="발급"
    class="credential-issue"
    :loading="issuing"
    :disable="issueLocked"
    @click="$emit('issue', row, 'issue')"
  />
  <span v-else class="muted-note">-</span>
</template>

<script>
export default {
  name: 'CredentialRowActions',
  props: {
    row: { type: Object, required: true },
    /** 미발급 상태값(NOT_ISSUED) — 서비스 상수를 페이지가 넘긴다 */
    notIssued: { type: String, required: true },
    busy: { type: Boolean, default: false },
    issuing: { type: Boolean, default: false },
    /** 다른 행이 발급 중이면 이 행 발급은 잠근다 */
    issueLocked: { type: Boolean, default: false },
    failureText: { type: String, default: '' },
  },
  emits: ['reissue', 'revoke', 'badge', 'issue'],
};
</script>

<style scoped>
.credential-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.credential-menu {
  min-width: 140px;
  white-space: nowrap;
}

.credential-failure {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-end;
}

.credential-failure__reason {
  font-size: 12px;
  color: var(--db-error);
}

.credential-issue {
  padding: 0 12px;
}

.muted-note {
  font-size: 12px;
  color: var(--db-text-sub);
}
</style>
