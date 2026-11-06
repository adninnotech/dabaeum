<template>
  <!-- 임시 비밀번호는 이 창에서 한 번만 보인다 — 바깥 누름으로 닫혀 잃지 않게 닫기 버튼으로만 닫고, 복사 버튼을 둔다 -->
  <q-dialog :model-value="modelValue" persistent @update:model-value="$emit('update:modelValue', $event)" @hide="copied = false">
    <q-card class="reset-card">
      <q-card-section class="reset-card__title">임시 비밀번호 발급</q-card-section>
      <q-card-section class="column q-gutter-sm">
        <div class="reset-card__name">{{ name }}</div>
        <div class="reset-card__row">
          <span class="reset-password">{{ password || '-' }}</span>
          <q-btn
            v-if="password"
            flat
            dense
            no-caps
            color="primary"
            :icon="copied ? 'check' : 'content_copy'"
            :label="copied ? '복사됨' : '복사'"
            @click="copy"
          />
        </div>
        <p class="reset-card__help">이 창을 닫으면 다시 볼 수 없습니다. 본인에게 전달하고, 처음 로그인할 때 바꾸도록 안내해 주세요.</p>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup unelevated no-caps color="primary" label="전달했습니다" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { copyToClipboard } from 'quasar';

export default {
  name: 'PasswordResetDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    name: { type: String, default: '' },
    password: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  data() {
    return { copied: false };
  },
  methods: {
    async copy() {
      try {
        await copyToClipboard(this.password);
        this.copied = true;
      } catch {
        this.$q.notify({ type: 'warning', message: '복사하지 못했습니다. 직접 선택해 복사해 주세요.', position: 'top' });
      }
    },
  },
};
</script>

<style scoped>
.reset-card {
  width: 420px;
  max-width: 94vw;
}

.reset-card__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.reset-card__name {
  font-size: 14px;
  color: var(--db-text-body);
}

.reset-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-subtle);
}

.reset-password {
  min-width: 0;
  font-family: monospace;
  font-size: 18px;
  font-weight: 700;
  word-break: break-all;
  user-select: all;
}

.reset-card__help {
  margin: 0;
  font-size: 12px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
