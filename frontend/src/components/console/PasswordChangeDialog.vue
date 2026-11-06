<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="saving"
    @update:model-value="$emit('update:modelValue', $event)"
    @hide="reset"
  >
    <q-card class="password-change">
      <q-card-section class="password-change__head">
        <div class="text-h6">비밀번호 변경</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />
      <q-card-section class="password-change__body">
        <q-input
          v-model="currentPassword"
          outlined
          dense
          type="password"
          label="현재 비밀번호 (임시 비밀번호)"
          autocomplete="current-password"
        />
        <q-input
          v-model="newPassword"
          outlined
          dense
          type="password"
          :label="`새 비밀번호 (${MIN_LENGTH}자 이상)`"
          autocomplete="new-password"
        />
        <q-input
          v-model="confirmPassword"
          outlined
          dense
          type="password"
          label="새 비밀번호 확인"
          autocomplete="new-password"
        />
        <p v-if="error" class="password-change__error" role="alert">{{ error }}</p>
      </q-card-section>
      <q-card-actions align="right" class="password-change__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          label="변경하기"
          :loading="saving"
          @click="submit"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { changeMyPassword } from '@/services/user-api';

const MIN_LENGTH = 10;

function validate({ currentPassword, newPassword, confirmPassword }) {
  if (!currentPassword || !newPassword) return '비밀번호를 입력해 주세요.';
  if (newPassword.length < MIN_LENGTH) return `새 비밀번호는 ${MIN_LENGTH}자 이상이어야 합니다.`;
  if (newPassword !== confirmPassword) return '새 비밀번호가 일치하지 않습니다.';
  if (newPassword === currentPassword) return '현재 비밀번호와 다른 비밀번호를 입력해 주세요.';
  return '';
}

/* 본인 비밀번호 변경 — PUT /users/me/password. 성공하면 부모가 로그아웃·재로그인을 이끈다 */
export default {
  name: 'PasswordChangeDialog',
  props: {
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue', 'changed'],
  data() {
    return {
      MIN_LENGTH,
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
      error: '',
      saving: false,
    };
  },
  methods: {
    reset() {
      this.currentPassword = '';
      this.newPassword = '';
      this.confirmPassword = '';
      this.error = '';
    },
    async submit() {
      this.error = validate(this);
      if (this.error) return;
      this.saving = true;
      try {
        await changeMyPassword({
          currentPassword: this.currentPassword,
          newPassword: this.newPassword,
        });
        this.$emit('update:modelValue', false);
        this.$emit('changed');
      } catch (error) {
        this.error =
          error?.status === 404
            ? '이 계정은 비밀번호 로그인을 쓰지 않습니다.'
            : error?.message || '비밀번호를 바꾸지 못했습니다.';
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.password-change {
  width: 420px;
  max-width: 92vw;
}

.password-change__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.password-change__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.password-change__error {
  margin: 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--db-status-pink);
}

.password-change__actions {
  padding: 8px 16px 16px;
}
</style>
