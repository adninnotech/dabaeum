<template>
  <!-- 홈 바로가기 — 홈 섹션이 이미 여는 곳(인기 강좌·다른 기관 강좌)은 뺐다. 강좌 찾기는 남긴다 — 비로그인 첫 화면에서
       가장 많이 누를 곳이고, 중장년 이용자는 머리 메뉴보다 큰 단추를 찾는다.
       동그라미 아이콘 6칸 격자는 흔한 틀이라 가로 줄 단추 넷으로. 뜻을 모를 수 있어 이름 옆에 한 줄 설명은 남긴다.
       로그인이 필요한 곳(학습관리·학습지갑)은 누르면 라우터 가드가 로그인(가입 안내)으로 보낸다 -->
  <nav class="quick-links" aria-label="바로가기">
    <router-link v-for="item in QUICK_LINKS" :key="item.label" :to="item.to" class="quick-links__item">
      <span class="quick-links__icon" aria-hidden="true">
        <q-icon :name="item.icon" size="24px" />
      </span>
      <span class="quick-links__text">
        <span class="quick-links__label">{{ item.label }}</span>
        <span class="quick-links__desc">{{ item.desc }}</span>
      </span>
      <q-icon name="chevron_right" size="22px" class="quick-links__chevron" aria-hidden="true" />
    </router-link>
  </nav>
</template>

<script>
// 대구시민대학은 표준 강좌 분류의 중분류(시민참여교육 > 대구시민대학)
const QUICK_LINKS = [
  { label: '강좌 찾기', desc: '분야별로 찾기', icon: 'search', to: '/courses' },
  {
    label: '대구시민대학',
    desc: '시민참여 강좌',
    icon: 'groups',
    to: { path: '/courses', query: { tab: '시민참여교육', sub: 'TSIX_060400' } },
  },
  { label: '학습관리', desc: '신청·수강 현황', icon: 'school', to: '/learning' },
  { label: '학습지갑', desc: '수료증·배지', icon: 'workspace_premium', to: '/wallet' },
];

export default {
  name: 'HomeQuickLinks',
  data() {
    return { QUICK_LINKS };
  },
};
</script>

<style scoped>
.quick-links {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

/* 태블릿: 2칸 두 줄 */
@media (max-width: 1023px) {
  .quick-links {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.quick-links__item {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  min-height: 72px;
  padding: 12px 14px 12px 16px;
  border: 1px solid var(--db-border);
  border-radius: 10px;
  background: var(--db-bg-white);
  text-decoration: none;
  color: var(--db-text-title);
}

@media (hover: hover) {
  .quick-links__item {
    transition: border-color 0.15s ease, background-color 0.15s ease;
  }

  .quick-links__item:hover {
    border-color: var(--db-accent);
    background: var(--db-bg-subtle);
  }

  .quick-links__item:hover .quick-links__chevron {
    color: var(--db-accent);
    transform: translateX(2px);
  }
}

.quick-links__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.quick-links__icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--db-accent-soft);
  color: var(--db-accent);
}

.quick-links__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.quick-links__label {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 17px;
  font-weight: 700;
  line-height: 1.3;
}

.quick-links__desc {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  line-height: 1.4;
  color: var(--db-text-sub);
}

.quick-links__chevron {
  flex-shrink: 0;
  color: var(--db-text-sub);
  transition: color 0.15s ease, transform 0.15s ease;
}

@media (prefers-reduced-motion: reduce) {
  .quick-links__item,
  .quick-links__chevron {
    transition: none;
  }
}

/* 폰: 한 줄에 하나 — 세 줄이 64px 씩 */
@media (max-width: 599px) {
  .quick-links {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .quick-links__item {
    min-height: 64px;
  }
}
</style>
