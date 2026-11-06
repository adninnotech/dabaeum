<template>
  <!-- 없는 주소 — 영문 Quasar 기본 화면 대신 서비스 톤(토큰·한글)으로 -->
  <main class="not-found">
    <section class="not-found__card" aria-labelledby="not-found-title">
      <p class="not-found__code" aria-hidden="true">404</p>
      <h1 id="not-found-title" class="not-found__title">페이지를 찾을 수 없습니다</h1>
      <p class="not-found__text">주소가 바뀌었거나 없어진 페이지입니다. 주소를 다시 확인해 주세요.</p>
      <div class="not-found__actions">
        <q-btn unelevated no-caps color="primary" label="홈으로" to="/" class="not-found__btn" />
        <q-btn outline no-caps color="primary" label="이전 화면" class="not-found__btn" @click="goBack" />
      </div>
    </section>
  </main>
</template>

<script>
export default {
  name: 'ErrorNotFound',
  methods: {
    goBack() {
      if (window.history.length > 1) this.$router.back();
      else this.$router.push('/');
    },
  },
};
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 16px;
  background: var(--db-bg-page);
}

.not-found__card {
  width: 100%;
  max-width: 440px;
  padding: 40px 28px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 12px;
  text-align: center;
}

.not-found__code {
  margin: 0;
  font-size: 56px;
  line-height: 1;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--db-primary);
  font-variant-numeric: tabular-nums;
}

.not-found__title {
  margin: 16px 0 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.not-found__text {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-sub);
}

.not-found__actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
}

.not-found__btn {
  min-width: 120px;
  height: 40px;
  border-radius: 6px;
}
</style>
