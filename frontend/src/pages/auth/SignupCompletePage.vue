<template>
  <q-page class="column items-center justify-center bg-grey-1 q-pa-md">
    <q-card flat bordered class="complete-card text-center">
      <q-card-section>
        <q-avatar size="88px" color="green-1" text-color="positive" icon="check" />
        <div class="text-h4 text-weight-bold q-mt-xl">회원가입이 완료되었습니다!</div>
        <div class="text-grey-7 q-mt-md">{{ roleLabel }}으로 다배움과 함께 새로운 배움을 시작해 보세요.</div>
        <div class="row justify-center q-gutter-sm q-mt-xl">
          <q-btn outline color="primary" label="홈으로" to="/" />
          <q-btn unelevated color="primary" label="로그인하기" to="/login" />
        </div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script>
export default {
  name: 'SignupCompletePage',
  computed: {
    roleLabel() {
      return { LEARNER: '일반회원', INSTRUCTOR: '개인강사', INSTITUTION_ADMIN: '기관관리자' }[this.$route.query.role] || '회원';
    },
  },
};
</script>

<style scoped>
.complete-card { width: 680px; max-width: 100%; padding: 64px 24px; border-radius: 20px; }
</style>
