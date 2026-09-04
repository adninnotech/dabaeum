<template>
  <q-page padding>
    <div class="page-inner">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">고객센터</div>
        <div class="app-page-header__subtitle text-grey-7">공지 · FAQ · 문의 (비API 화면)</div>
      </div>

      <q-list bordered class="rounded-borders">
        <q-expansion-item v-for="item in faqs" :key="item.q" :label="item.q" header-class="text-weight-medium">
          <q-card flat>
            <q-card-section class="text-grey-8">{{ item.a }}</q-card-section>
          </q-card>
        </q-expansion-item>
      </q-list>

      <q-card flat bordered class="q-mt-md">
        <q-card-section>
          <div class="text-subtitle1 text-weight-bold">문의하기</div>
          <q-input v-model="subject" outlined dense class="q-mt-md" label="제목" />
          <q-input v-model="message" outlined dense type="textarea" autogrow class="q-mt-md" label="내용" />
          <q-btn class="q-mt-md" color="primary" unelevated label="문의 등록" @click="submitInquiry" />
        </q-card-section>
      </q-card>
    </div>
  </q-page>
</template>

<script>
export default {
  name: 'SupportPage',
  data() {
    return {
      subject: '',
      message: '',
      faqs: [
        { q: '수강신청은 어떻게 하나요?', a: '강좌 상세에서 수강신청하기를 누르면 APPLIED 상태로 접수됩니다.' },
        { q: '출석은 어떻게 하나요?', a: '회차 시작 전후 QR 토큰으로 출석을 제출합니다.' },
        { q: '수료증은 어디서 보나요?', a: '학습지갑 메뉴의 Credential 탭에서 확인할 수 있습니다.' },
      ],
    };
  },
  methods: {
    submitInquiry() {
      if (!this.subject || !this.message) {
        this.$q.notify({ type: 'warning', message: '제목과 내용을 입력해 주세요.', position: 'top' });
        return;
      }
      this.subject = '';
      this.message = '';
      this.$q.notify({ type: 'positive', message: '문의가 등록되었습니다. (mock)', position: 'top' });
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 800px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
</style>
