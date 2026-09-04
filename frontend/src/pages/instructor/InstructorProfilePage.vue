<template>
  <q-page padding>
    <div class="form-wrap">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">강사 프로필</div>
        <div class="app-page-header__subtitle text-grey-7">PUT /users/me · 이름·이메일·연락처·생년월일</div>
      </div>
      <q-card flat bordered>
        <q-card-section class="q-gutter-md">
          <div class="row items-center q-gutter-md">
            <q-avatar size="80px" color="primary" text-color="white">{{ avatarLetter }}</q-avatar>
          </div>
          <q-input v-model="name" outlined label="이름" />
          <q-input v-model="email" outlined label="이메일" />
          <q-input v-model="phone" outlined label="연락처" />
          <q-input v-model="birthDate" outlined type="date" label="생년월일" />
          <q-input v-model="career" outlined type="textarea" label="주요 경력 (화면만, API 없음)" />
          <q-input v-model="introduction" outlined type="textarea" label="강사 소개 (화면만, API 없음)" />
          <q-btn color="primary" unelevated :loading="saving" label="저장하기" @click="save" />
        </q-card-section>
      </q-card>
    </div>
  </q-page>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'InstructorProfilePage',
  data() {
    return {
      name: '',
      email: '',
      phone: '',
      birthDate: '',
      career: '',
      introduction: '',
      saving: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['avatarLetter', 'user']),
  },
  watch: {
    user: {
      immediate: true,
      handler(value) {
        if (!value) return;
        this.name = value.name || '';
        this.email = value.email || '';
        this.phone = value.phone || '';
        this.birthDate = value.birthDate ? String(value.birthDate).slice(0, 10) : '';
      },
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['updateMyProfile']),
    async save() {
      this.saving = true;
      try {
        await this.updateMyProfile({
          name: this.name.trim(),
          email: this.email.trim() || null,
          phone: this.phone.trim() || null,
          birthDate: this.birthDate || null,
        });
        this.$q.notify({ type: 'positive', message: '프로필을 저장했습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '저장에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.form-wrap {
  max-width: 760px;
  margin: 0 auto;
}
</style>
