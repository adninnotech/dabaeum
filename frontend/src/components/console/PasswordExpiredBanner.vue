<template>
  <div v-if="visible" class="pw-banner" role="status">
    <q-icon name="o_lock_clock" size="20px" class="pw-banner__icon" />
    <p class="pw-banner__text">
      <strong>비밀번호를 바꿔 주세요.</strong>
      임시 비밀번호이거나 {{ ADMIN_PASSWORD_MAX_AGE_DAYS }}일 넘게 바꾸지 않은 관리자 비밀번호입니다.
    </p>
    <div class="pw-banner__actions">
      <q-btn
        unelevated
        no-caps
        dense
        color="primary"
        label="지금 변경"
        class="pw-banner__btn"
        @click="dialogOpen = true"
      />
      <q-btn flat no-caps dense label="나중에" class="pw-banner__later" @click="dismiss" />
    </div>
    <PasswordChangeDialog v-model="dialogOpen" @changed="onChanged" />
  </div>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import PasswordChangeDialog from '@/components/console/PasswordChangeDialog.vue';
import { useAuthStore } from '@/stores/auth-store';
import { ADMIN_PASSWORD_MAX_AGE_DAYS } from '@/utils/admin-labels';

const DISMISS_KEY_PREFIX = 'dabaeum.passwordExpired.dismissed.';

function readDismissed(userId) {
  try {
    return sessionStorage.getItem(DISMISS_KEY_PREFIX + userId) === '1';
  } catch {
    return false;
  }
}

/* 관리자 비밀번호 변경 안내(설계서 §10.3) — session.passwordExpired 가 true 면 콘솔 본문 위에 띄운다.
 * 접근은 막지 않는다. "나중에"는 이 브라우저 탭 세션 동안만 숨긴다 */
export default {
  name: 'PasswordExpiredBanner',
  components: { PasswordChangeDialog },
  data() {
    return {
      ADMIN_PASSWORD_MAX_AGE_DAYS,
      dialogOpen: false,
      dismissed: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['session', 'userId', 'impersonating']),
    visible() {
      // 대리 로그인은 남의 비밀번호다 — 안내하지 않는다
      return this.session?.passwordExpired === true && !this.impersonating && !this.dismissed;
    },
  },
  watch: {
    userId: {
      immediate: true,
      handler(value) {
        this.dismissed = value ? readDismissed(value) : false;
      },
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['logout']),
    dismiss() {
      this.dismissed = true;
      try {
        sessionStorage.setItem(DISMISS_KEY_PREFIX + this.userId, '1');
      } catch {
        /* 저장이 막혀도 이번 화면에서는 숨긴다 */
      }
    },
    onChanged() {
      this.$q.notify({
        type: 'positive',
        message: '비밀번호를 바꿨습니다. 새 비밀번호로 다시 로그인해 주세요.',
        position: 'top',
      });
      this.logout();
      this.$router.push('/login');
    },
  },
};
</script>

<style scoped>
.pw-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 24px 0;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--db-status-orange) 10%, var(--db-bg-white));
  border: 1px solid color-mix(in srgb, var(--db-status-orange) 45%, transparent);
  border-radius: 8px;
}

.pw-banner__icon {
  flex-shrink: 0;
  color: var(--db-status-orange-text);
}

.pw-banner__text {
  flex: 1;
  min-width: 0;
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.pw-banner__text strong {
  margin-right: 4px;
  color: var(--db-text-title);
}

.pw-banner__actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
}

.pw-banner__btn {
  padding: 0 12px;
  border-radius: 6px;
}

.pw-banner__later {
  color: var(--db-text-sub);
}

@media (max-width: 599px) {
  .pw-banner {
    flex-wrap: wrap;
    margin: 12px 16px 0;
  }

  .pw-banner__actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
