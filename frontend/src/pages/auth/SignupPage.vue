<template>
  <!-- 역할 고르기는 약관·정보 입력 앞 단계라 단계 표시를 끈다 — 켜 두면 "① 약관 동의"에 불이 들어왔다 -->
  <AuthShell :current-step="1" :show-stepper="false" login-path="/login/staff">
    <div class="signup-role">
      <div class="signup-role__main">
        <header class="signup-role__header">
          <h1 class="signup-role__title">강사·기관 관리자 회원가입</h1>
          <p class="signup-role__sub">가입하실 역할을 선택해주세요</p>
        </header>

        <div class="signup-role__cards">
          <button
            v-for="role in roles"
            :key="role.value"
            type="button"
            class="role-card"
            :class="{ 'role-card--on': selected === role.value }"
            :aria-pressed="selected === role.value"
            @click="selected = role.value"
          >
            <!-- 고른 카드는 테두리 색만 바뀌어 색으로만 구분됐다 — 체크 표시를 함께 -->
            <q-icon
              v-if="selected === role.value"
              name="check_circle"
              size="28px"
              class="role-card__check"
              aria-hidden="true"
            />
            <span class="role-card__inner">
              <q-icon :name="role.icon" size="80px" :style="{ color: role.color }" />
              <span class="role-card__text">
                <span class="role-card__name">{{ role.label }}</span>
                <span class="role-card__desc">
                  {{ role.description[0] }}<br v-if="role.description[1]" />{{
                    role.description[1]
                  }}
                </span>
              </span>
            </span>
          </button>
        </div>
      </div>

      <button type="button" class="signup-role__next" :disabled="!selected" @click="goNext">
        다음
      </button>
      <p class="signup-role__learner">
        강좌를 들으려는 학습자이신가요?
        <router-link to="/login" class="signup-role__learner-link">다대구로 로그인</router-link>
      </p>
    </div>
  </AuthShell>
</template>

<script>
import AuthShell from '@/components/auth/AuthShell.vue';
import { safeRedirect } from '@/utils/auth-redirect';

const GREEN = 'var(--db-status-green)';
const PURPLE = 'var(--db-status-purple)';

/* 학습자(일반 회원)는 다대구로만 가입한다(#112) — 이메일 가입은 강사·기관 관리자만(#134).
 * 예전 1단계(일반 회원 / 교육 운영자)는 운영자 하나만 남아 없애고, 2단계 두 역할을 바로 보인다 */
const STAFF_ROLES = [
  {
    value: 'INSTRUCTOR',
    label: '개인 강사',
    icon: 'person_outline',
    color: GREEN,
    description: ['소속 기관의 강사로 활동'],
  },
  {
    value: 'INSTITUTION_ADMIN',
    label: '기관 관리자',
    icon: 'apartment',
    color: PURPLE,
    description: ['기관을 등록하고 강사와 강의를 관리'],
  },
];

const NEXT_ROUTE = {
  INSTRUCTOR: { path: '/signup/form', query: { role: 'INSTRUCTOR' } },
  INSTITUTION_ADMIN: { path: '/signup/institution' },
};

export default {
  name: 'SignupPage',
  components: { AuthShell },
  data() {
    return { selected: '', roles: STAFF_ROLES };
  },
  methods: {
    // 로그인이 필요한 곳에서 왔으면 돌아갈 주소를 가입 단계 끝까지 넘긴다
    goNext() {
      const next = NEXT_ROUTE[this.selected];
      const redirect = safeRedirect(this.$route.query.redirect);
      this.$router.push(redirect ? { ...next, query: { ...next.query, redirect } } : next);
    },
  },
};
</script>

<style scoped>
/* container 616 (padding 10) → 안쪽 596 = 카드 282 + gap 32 + 282 */
/* 786 = inner wrap 693 + 40 + 버튼 53. gap 160은 숨김 register 앞이라 렌더에 안 나온다 */
.signup-role {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 40px;
  width: 616px;
  margin: 0 auto;
  padding: 10px;
}

.signup-role__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 80px;
}

.signup-role__header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
}

.signup-role__title {
  margin: 0;
  font-size: 40px;
  line-height: 48px;
  font-weight: 700;
  color: var(--db-text-body);
}

.signup-role__sub {
  margin: 0;
  text-align: center;
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.signup-role__cards {
  display: flex;
  gap: 32px;
}

.role-card {
  position: relative;
  width: 282px;
  height: 320px;
  padding: 10px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  cursor: pointer;
}

.role-card:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.role-card__check {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--db-accent);
}

/* 보더가 1→2px이라 padding을 9로 줄여 282×320을 유지한다 */
.role-card--on {
  padding: 9px;
  border: 2px solid var(--db-accent);
  background: var(--db-accent-soft);
}

.role-card__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  height: 100%;
  padding: 25px 3px;
}

.role-card__text {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.role-card__name {
  font-size: 32px;
  line-height: 38px;
  font-weight: 700;
  color: var(--db-text-title);
}

.role-card__desc {
  font-size: 16px;
  line-height: 19px;
  font-weight: 400;
  color: var(--db-text-body);
}

.signup-role__next {
  width: 496px;
  height: 53px;
  align-self: center;
  border: 0;
  border-radius: 8px;
  background: var(--db-primary); /* 사이트 주 버튼과 같은 남색 — 밝은 파랑이라 다른 화면과 어긋났다 */
  font-family: inherit;
  font-size: 18px;
  line-height: 21px;
  font-weight: 600;
  color: var(--db-on-color);
  cursor: pointer;
}

/* opacity는 Quasar 전역 `[disabled]{opacity:.6!important}`가 가져간다 */
.signup-role__next:disabled {
  cursor: default;
}

/* 학습자가 잘못 들어왔을 때 돌아갈 길 — 다대구 로그인 화면으로 */
.signup-role__learner {
  margin: 0;
  font-size: 15px;
  color: var(--db-text-sub);
  text-align: center;
  word-break: keep-all;
}

.signup-role__learner-link {
  margin-left: 6px;
  font-weight: 600;
  color: var(--db-primary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 폰: 616 컨테이너·282 카드 2장·496 버튼이 375 폭을 넘었다 — 카드를 세로로 쌓고 가로형으로 */
@media (max-width: 1023px) {
  .signup-role {
    gap: 32px;
    width: 100%;
    max-width: 616px;
    padding: 16px;
  }

  .signup-role__main {
    gap: 32px;
    width: 100%;
  }

  .signup-role__title {
    font-size: 28px;
    line-height: 36px;
  }

  .signup-role__cards {
    flex-direction: column;
    gap: 12px;
    width: 100%;
  }

  .role-card {
    width: 100%;
    height: auto;
  }

  .role-card__inner {
    flex-direction: row;
    justify-content: flex-start;
    gap: 16px;
    padding: 12px;
    text-align: left;
  }

  /* q-icon size prop 이 inline font-size 라 !important 로만 줄어든다 */
  .role-card__inner .q-icon {
    font-size: 56px !important;
  }

  .role-card__text {
    gap: 6px;
  }

  .role-card__name {
    font-size: 22px;
    line-height: 28px;
  }

  .signup-role__next {
    width: 100%;
  }
}
</style>
