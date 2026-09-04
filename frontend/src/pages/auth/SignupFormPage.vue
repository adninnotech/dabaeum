<template>
  <q-page class="bg-grey-1 q-pa-md">
    <div class="form-wrap">
      <div class="text-h4 text-weight-bold text-center q-mt-lg">회원정보 입력</div>
      <div class="row justify-center q-my-lg q-gutter-md">
        <q-chip color="grey-3">1 역할선택</q-chip><q-chip color="primary" text-color="white">2 정보입력</q-chip><q-chip color="grey-3">3 가입완료</q-chip>
      </div>
      <q-card flat bordered class="q-pa-md">
        <q-card-section>
          <div class="text-h6 text-weight-bold">기본 정보</div>
          <div class="text-caption text-grey-7 q-mb-lg">선택한 유형: {{ roleLabel }}</div>
          <q-form ref="form" class="q-gutter-md" @submit="submit">
            <q-input v-model="name" outlined label="이름 *" :rules="[required]" />
            <q-input v-model="email" outlined type="email" label="이메일 *" :rules="[required, emailRule]" />
            <q-input v-model="password" outlined type="password" label="비밀번호 * (10자 이상)" :rules="[required, passwordRule]" />
            <q-input v-model="passwordConfirm" outlined type="password" label="비밀번호 확인 *" :rules="[required, confirmRule]" />
            <q-input v-model="phone" outlined mask="###-####-####" label="휴대전화" />
            <q-input v-model="birthDate" outlined type="date" label="생년월일" />
            <div class="row items-center justify-between">
              <q-checkbox v-model="agreeTerms" label="이용약관 및 개인정보 처리방침에 동의합니다. (필수)" />
              <q-btn flat dense color="primary" label="내용보기" @click="termsOpen = true" />
            </div>
            <q-btn type="submit" unelevated color="primary" label="가입하기" class="full-width q-py-sm" :loading="submitting" />
          </q-form>
        </q-card-section>
      </q-card>
    </div>
    <q-dialog v-model="termsOpen">
      <q-card style="width: 620px; max-width: 90vw">
        <q-card-section class="row items-center justify-between"><div class="text-h6">이용약관</div><q-btn v-close-popup flat round icon="close" /></q-card-section>
        <q-separator /><q-card-section class="terms">{{ terms }}</q-card-section>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import { mapActions } from 'pinia';
import { MOCK_TERMS } from '@/data/ui-mock.js';
import { signupLocalAccount } from '@/services/auth-api';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'SignupFormPage',
  data() {
    return {
      name: '',
      password: '',
      passwordConfirm: '',
      email: '',
      phone: '',
      birthDate: '',
      agreeTerms: false,
      termsOpen: false,
      terms: MOCK_TERMS,
      submitting: false,
    };
  },
  computed: {
    role() { return String(this.$route.query.role || 'LEARNER'); },
    roleLabel() { return { LEARNER: '일반회원', INSTRUCTOR: '개인강사', INSTITUTION_ADMIN: '기관관리자' }[this.role] || '일반회원'; },
  },
  methods: {
    ...mapActions(useAuthStore, ['applyAuthToken']),
    required(value) { return !!value || '필수 입력 항목입니다.'; },
    passwordRule(value) { return (value && value.length >= 10) || '10자 이상 입력해 주세요.'; },
    confirmRule(value) { return value === this.password || '비밀번호가 일치하지 않습니다.'; },
    emailRule(value) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) || '올바른 이메일을 입력해 주세요.'; },
    async submit() {
      if (!this.agreeTerms) {
        this.$q.notify({ type: 'warning', message: '필수 약관에 동의해 주세요.' });
        return;
      }
      const valid = await this.$refs.form.validate();
      if (!valid) return;
      this.submitting = true;
      try {
        const tokenPayload = await signupLocalAccount({
          email: this.email.trim(),
          password: this.password,
          name: this.name.trim(),
          phone: this.phone.trim() || null,
          birthDate: this.birthDate || null,
        });
        await this.applyAuthToken(tokenPayload);
        this.$q.notify({
          type: 'positive',
          message: this.role === 'LEARNER'
            ? '가입되었습니다.'
            : '일반회원으로 가입되었습니다. 강사/기관 역할은 신청·승인 후 부여됩니다.',
          position: 'top',
        });
        const next = this.role === 'INSTRUCTOR' ? '/mypage/instructor-apply' : '/signup/complete';
        this.$router.push({ path: next, query: { role: this.role } });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '회원가입에 실패했습니다.', position: 'top' });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.form-wrap { max-width: 640px; margin: 0 auto; padding-bottom: 64px; }
.terms { white-space: pre-line; line-height: 1.8; max-height: 60vh; overflow: auto; }
</style>
