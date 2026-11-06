<template>
  <!-- 학습자 사이트 하단 바 — 저작권만 있던 줄에 약관·개인정보처리방침·고객센터를 둔다 -->
  <div class="site-footer">
    <nav class="site-footer__links" aria-label="사이트 정보">
      <router-link to="/policies/usage" class="site-footer__link">이용약관</router-link>
      <router-link to="/policies/privacy" class="site-footer__link site-footer__link--strong">개인정보처리방침</router-link>
      <router-link to="/support" class="site-footer__link">고객센터</router-link>
    </nav>
    <p class="site-footer__copy">Copyright © {{ year }} 다배움. All rights reserved.</p>
  </div>
</template>

<script>
export default {
  name: 'AppSiteFooter',
  data() {
    return { year: new Date().getFullYear() };
  },
};
</script>

<style scoped>
.site-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px 24px;
  flex-wrap: wrap;
  max-width: var(--db-learner-width);
  min-height: 60px;
  margin: 0 auto;
  padding: 10px 16px;
  color: var(--db-on-color);
}

.site-footer__links {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 20px;
}

.site-footer__link {
  font-size: 14px;
  color: var(--db-on-color);
  text-decoration: none;
  opacity: 0.85;
}

/* 개인정보처리방침은 법적으로 눈에 띄게 둔다 */
.site-footer__link--strong {
  font-weight: 700;
  opacity: 1;
}

.site-footer__link:hover {
  opacity: 1;
  text-decoration: underline;
}

.site-footer__link:focus-visible {
  outline: 2px solid var(--db-bg-white);
  outline-offset: 2px;
  border-radius: 2px;
}

.site-footer__copy {
  margin: 0;
  font-size: 14px;
  opacity: 0.85;
}

@media (max-width: 599px) {
  .site-footer {
    justify-content: center;
    text-align: center;
  }

  .site-footer__links {
    justify-content: center;
  }

  .site-footer__copy {
    font-size: 13px;
  }
}
</style>
