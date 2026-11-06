<template>
  <!-- 마이페이지 > 회원 탈퇴. 되돌릴 수 없어 확인 문구를 직접 입력받는다 -->
  <q-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" @before-show="reset">
    <q-card style="width: 420px; max-width: 90vw">
      <q-card-section class="row items-center justify-between">
        <div class="text-h6">회원 탈퇴</div>
        <q-btn v-close-popup flat round dense icon="close" />
      </q-card-section>
      <q-separator />
      <!-- q-gutter 는 칸에 음수 왼쪽 여백을 줘 글자가 창 왼쪽 끝에 붙었다 — 일반 간격으로 둔다 -->
      <q-card-section class="mypage-withdraw__body">
        <!-- 세 가지 결과가 한 문단에 이어져 읽기 어려웠다 — 훑어볼 수 있게 목록으로.
             계약(POST /users/me/withdraw)이 보장하는 것만 적는다: 토큰 무효화·이력 보존.
             공유 링크·수료증 처리는 명세에 없어 조심스러운 문구로 둔다 -->
        <div class="mypage-withdraw__notice">
          <p class="mypage-withdraw__lead">탈퇴하기 전에 확인해 주세요.</p>
          <ul class="mypage-withdraw__list">
            <li>바로 로그아웃되고, 이 계정으로 다시 로그인할 수 없습니다.</li>
            <li>학습지갑(수료증·배지)과 포트폴리오 공유 링크를 더 이상 이용할 수 없습니다.</li>
            <li>수강·수강평 이력은 삭제되지 않고 남습니다.</li>
          </ul>
        </div>
        <q-input
          v-model="withdrawReason"
          outlined
          dense
          type="textarea"
          autogrow
          maxlength="500"
          label="탈퇴 사유 (선택)"
        />
        <!-- 버튼 한 번으로 되돌릴 수 없는 탈퇴가 일어나지 않게 직접 입력을 받는다.
             방법을 입력칸 라벨에만 두면 입력을 시작하는 순간 사라진다 — 위에 문장으로 둔다 -->
        <div class="mypage-withdraw__confirm">
          <label class="mypage-withdraw__confirm-label" for="withdraw-confirm">
            계속하려면 아래 칸에 <strong>{{ WITHDRAW_CONFIRM_WORD }}</strong>를 입력하세요.
          </label>
          <q-input
            v-model="withdrawConfirmText"
            for="withdraw-confirm"
            outlined
            dense
            :placeholder="WITHDRAW_CONFIRM_WORD"
            autocomplete="off"
          />
        </div>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="취소" />
        <q-btn
          unelevated
          no-caps
          color="negative"
          label="탈퇴하기"
          :disable="!withdrawConfirmed"
          :loading="withdrawSaving"
          @click="submitWithdraw"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { mapActions } from 'pinia';
import { withdrawMe } from '@/services/user-api';
import { useAuthStore } from '@/stores/auth-store';

// 탈퇴 확인 입력 문구
const WITHDRAW_CONFIRM_WORD = '탈퇴';

export default {
  name: 'WithdrawDialog',
  props: {
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      WITHDRAW_CONFIRM_WORD,
      withdrawSaving: false,
      withdrawReason: '',
      withdrawConfirmText: '',
    };
  },
  computed: {
    withdrawConfirmed() {
      return this.withdrawConfirmText.trim() === WITHDRAW_CONFIRM_WORD;
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['logout']),
    // 지난번에 입력한 확인 문구가 남아 있으면 확인 절차가 무력해진다
    reset() {
      this.withdrawReason = '';
      this.withdrawConfirmText = '';
    },
    async submitWithdraw() {
      if (!this.withdrawConfirmed) return;
      this.withdrawSaving = true;
      try {
        await withdrawMe({ reason: this.withdrawReason.trim() || undefined });
        this.$emit('update:modelValue', false);
        this.$q.notify({ type: 'positive', message: '탈퇴 처리되었습니다.' });
        this.logout();
        this.$router.push('/');
      } catch (error) {
        if (error?.status === 409) {
          this.$q.notify({ type: 'negative', message: '이미 탈퇴 처리된 계정입니다.' });
        } else {
          this.$q.notify({ type: 'negative', message: error?.message || '탈퇴에 실패했습니다.' });
        }
      } finally {
        this.withdrawSaving = false;
      }
    },
  },
};
</script>

<style scoped>
.mypage-withdraw__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
}

.mypage-withdraw__notice {
  padding: 14px 16px;
  background: var(--db-bg-subtle);
  border-radius: 8px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.mypage-withdraw__lead {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.mypage-withdraw__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding-left: 18px;
  font-size: 14px;
  line-height: 21px;
}

.mypage-withdraw__confirm {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mypage-withdraw__confirm-label {
  font-size: 14px;
  color: var(--db-text-body);
}
</style>
