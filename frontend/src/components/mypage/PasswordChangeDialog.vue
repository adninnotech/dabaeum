<template>
  <!-- 마이페이지 > 비밀번호 변경. 바꾸면 로그아웃하고 다시 로그인하게 한다 -->
  <q-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" @before-show="reset">
    <q-card style="width: 420px; max-width: 90vw">
      <q-card-section class="row items-center justify-between">
        <div class="text-h6">비밀번호 변경</div>
        <q-btn v-close-popup flat round dense icon="close" />
      </q-card-section>
      <q-separator />
      <q-card-section class="column q-gutter-md">
        <q-input
          v-model="passwordForm.currentPassword"
          outlined
          dense
          type="password"
          label="현재 비밀번호"
          :error="!!errors.currentPassword"
          :error-message="errors.currentPassword"
          @update:model-value="errors.currentPassword = ''"
        />
        <q-input
          v-model="passwordForm.newPassword"
          outlined
          dense
          type="password"
          label="새 비밀번호 (10자 이상)"
          :error="!!errors.newPassword"
          :error-message="errors.newPassword"
          @update:model-value="errors.newPassword = ''"
        />
        <q-input
          v-model="passwordForm.confirmPassword"
          outlined
          dense
          type="password"
          label="새 비밀번호 확인"
          :error="!!errors.confirmPassword"
          :error-message="errors.confirmPassword"
          @update:model-value="errors.confirmPassword = ''"
        />
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="취소" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          label="비밀번호 변경"
          :loading="passwordSaving"
          @click="submitPasswordChange"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'pinia';
import { changeMyPassword } from '@/services/user-api';
import { useAuthStore } from '@/stores/auth-store';

const EMPTY_FORM = { currentPassword: '', newPassword: '', confirmPassword: '' };
const NEW_PASSWORD_MIN = 10;

/* 칸 아래 안내 — 잠깐 뜨는 토스트로만 알려 놓치기 쉬웠다(QA AUTH-07) */
function validate({ currentPassword, newPassword, confirmPassword }) {
  const errors = {};
  if (!currentPassword) errors.currentPassword = '현재 비밀번호를 입력해 주세요.';
  if (!newPassword) errors.newPassword = '새 비밀번호를 입력해 주세요.';
  else if (newPassword.length < NEW_PASSWORD_MIN) errors.newPassword = `새 비밀번호는 ${NEW_PASSWORD_MIN}자 이상이어야 합니다.`;
  if (newPassword && newPassword !== confirmPassword) errors.confirmPassword = '새 비밀번호가 일치하지 않습니다.';
  return errors;
}

export default {
  name: 'PasswordChangeDialog',
  props: {
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      passwordSaving: false,
      passwordForm: { ...EMPTY_FORM },
      errors: {},
    };
  },
  methods: {
    ...mapActions(useAuthStore, ['logout']),
    // 지난번에 친 비밀번호가 다시 열 때 남지 않게
    reset() {
      this.passwordForm = { ...EMPTY_FORM };
      this.errors = {};
    },
    async submitPasswordChange() {
      const { currentPassword, newPassword } = this.passwordForm;
      this.errors = validate(this.passwordForm);
      if (Object.keys(this.errors).length) return;
      this.passwordSaving = true;
      try {
        await changeMyPassword({ currentPassword, newPassword });
        this.$emit('update:modelValue', false);
        this.$q.notify({
          type: 'positive',
          message: '비밀번호가 변경되었습니다. 다시 로그인해 주세요.',
        });
        this.logout();
        // 다시 로그인하면 바꾼 곳(마이페이지)으로 돌아오게 한다
        this.$router.push({ path: '/login', query: { redirect: '/mypage' } });
      } catch (error) {
        // 401 은 현재 비밀번호가 틀린 것 — 로그인 실패 문구("이메일 또는 비밀번호…")를 띄우지 않는다
        if (error?.status === 401) {
          this.errors = { currentPassword: '현재 비밀번호가 올바르지 않습니다.' };
        } else if (error?.status === 404) {
          this.$q.notify({ type: 'negative', message: '이 계정은 비밀번호 로그인을 쓰지 않습니다.' });
        } else {
          this.$q.notify({
            type: 'negative',
            message: error?.message || '비밀번호 변경에 실패했습니다.',
          });
        }
      } finally {
        this.passwordSaving = false;
      }
    },
  },
};
</script>
