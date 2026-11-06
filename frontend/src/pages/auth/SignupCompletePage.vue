<template>
  <AuthShell :current-step="3">
    <div class="complete">
      <div class="complete__wrap">
        <header class="complete__header">
          <h1 class="complete__title">{{ copy.title }}</h1>
          <p class="complete__body">{{ copy.body[0] }}<br />{{ copy.body[1] }}</p>
        </header>
      </div>

      <!-- 학습자는 가입과 동시에 로그인돼 바로 쓸 수 있다 — 다음 할 일로 곧장 보낸다 -->
      <div class="complete__actions">
        <router-link
          v-for="action in copy.actions"
          :key="action.label"
          :to="action.to"
          class="complete__home"
          :class="{ 'complete__home--secondary': action.secondary }"
        >
          {{ action.label }}
        </router-link>
      </div>
    </div>
  </AuthShell>
</template>

<script>
import AuthShell from '@/components/auth/AuthShell.vue';
import { safeRedirect } from '@/utils/auth-redirect';

const HOME_ACTIONS = [{ label: '홈으로', to: '/' }];

/* 학습자는 가입 즉시 토큰이 나와 승인 절차가 없다. 승인 대기 문구는 기관 관리자·강사에게만 쓴다 */
const COPIES = {
  LEARNER: {
    title: '가입이 완료되었습니다!',
    body: ['다배움 회원이 되신 것을 환영합니다.', '지금 바로 강좌를 찾아보고 수강을 시작해 보세요.'],
    actions: [
      { label: '강좌 찾아보기', to: '/courses' },
      { label: '학습관리', to: '/learning', secondary: true },
    ],
  },
  INSTITUTION_ADMIN: {
    title: '가입 신청이 접수되었습니다!',
    body: [
      '기관 관리자 가입 신청이 정상적으로 접수되었습니다.',
      '입력하신 정보를 검토 후 승인되면 안내드리겠습니다.',
    ],
    actions: HOME_ACTIONS,
  },
  INSTRUCTOR: {
    title: '가입 신청이 접수되었습니다!',
    body: [
      '가입 신청이 정상적으로 접수되었습니다.',
      '입력하신 정보를 검토 후 가입이 승인되면 안내드리겠습니다.',
    ],
    actions: HOME_ACTIONS,
  },
};

export default {
  name: 'SignupCompletePage',
  components: { AuthShell },
  computed: {
    // role 이 없으면 학습자 가입이다 (다대구 가입이 LEARNER 로 넘긴다. 이메일 가입은 #112 에서 없앴다).
    // 모르는 값은 "바로 이용 가능"을 약속하지 않도록 승인 대기 문구로 둔다
    copy() {
      const role = String(this.$route.query.role || 'LEARNER');
      const base = COPIES[role] || COPIES.INSTRUCTOR;
      const redirect = safeRedirect(this.$route.query.redirect);
      // 수강신청 등 로그인이 필요한 곳에서 가입하러 왔으면 그 화면으로 돌아가는 게 첫 행동
      if (role !== 'LEARNER' || !redirect) return base;
      return {
        ...base,
        actions: [
          { label: '하던 곳으로 돌아가기', to: redirect },
          ...base.actions.map((action) => ({ ...action, secondary: true })),
        ],
      };
    },
  },
};
</script>

<style scoped>
/* Figma가 가로 padding 10을 선언해놓고 렌더엔 반영이 없다 — 4-B와 같은 처리 */
.complete {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
  width: 1200px;
  margin: 0 auto;
  padding: 10px 0;
}

.complete__wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.complete__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
}

.complete__title {
  margin: 0;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

/* 2줄이라 줄 맞춤이 실제로 먹는다. 제목은 1줄 hug라 text-align이 의미 없다 */
.complete__body {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.complete__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

/* 아이콘·일러스트는 Figma에 없다 */
.complete__home {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 496px;
  height: 53px;
  align-self: center;
  border-radius: 8px;
  background: var(--db-primary); /* 사이트 주 버튼과 같은 남색 — 밝은 파랑이라 다른 화면과 어긋났다 */
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-on-color);
  text-decoration: none;
}

.complete__home--secondary {
  border: 1px solid var(--db-accent);
  background: var(--db-bg-white);
  color: var(--db-accent);
}

/* 폰: 1200 컨테이너·496 버튼이 375 폭을 넘었다 */
@media (max-width: 1023px) {
  .complete {
    width: 100%;
    max-width: 1200px;
    padding: 16px;
  }

  .complete__title {
    font-size: 24px;
    line-height: 32px;
    text-align: center;
  }

  .complete__home {
    width: 100%;
    max-width: 496px;
  }
}
</style>
