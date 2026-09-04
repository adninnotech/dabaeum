<template>
  <q-page padding class="bg-grey-1">
    <div class="page-wrap">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">마이페이지</div>
      </div>
      <q-card flat bordered class="q-mb-lg">
        <q-card-section>
          <div class="text-h6 text-weight-bold">내 정보</div>
          <div class="text-caption text-grey-7">PUT /users/me</div>
        </q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="profileName" outlined dense label="이름" />
          <q-input v-model="profileEmail" outlined dense label="이메일" />
          <q-input v-model="profilePhone" outlined dense label="연락처" />
          <q-input v-model="profileBirthDate" outlined dense type="date" label="생년월일" />
          <div class="row q-gutter-sm">
            <q-btn color="primary" unelevated :loading="savingProfile" label="프로필 저장" @click="saveProfile" />
            <q-btn outline color="primary" label="강사 신청" @click="$router.push('/mypage/instructor-apply')" />
          </div>
        </q-card-section>
      </q-card>
      <q-card flat bordered class="q-mb-lg">
        <q-card-section><div class="text-h6 text-weight-bold">학습현황</div></q-card-section>
        <q-card-section class="row text-center">
          <div v-for="item in summaryCards" :key="item.label" class="col">
            <div class="text-h4 text-primary text-weight-bold">{{ item.value }}</div>
            <div class="app-page-header__subtitle text-grey-7">{{ item.label }}</div>
          </div>
        </q-card-section>
      </q-card>
      <q-card flat bordered class="q-mb-lg">
        <q-card-section class="row justify-between items-center">
          <div class="text-h6 text-weight-bold">최근 수강 현황</div>
          <q-btn flat color="primary" label="전체보기" @click="$router.push('/learning')" />
        </q-card-section>
        <q-separator />
        <q-list separator>
          <q-item v-for="course in recentCourses" :key="course.id">
            <q-item-section avatar>
              <q-img :src="course.image" width="96px" height="64px" class="rounded-borders" />
            </q-item-section>
            <q-item-section>
              <q-item-label class="text-weight-bold">{{ course.title }}</q-item-label>
              <q-item-label caption>{{ course.period }}</q-item-label>
            </q-item-section>
            <q-item-section side>
              <q-badge color="primary" outline>{{ course.status }}</q-badge>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card>
      <q-card flat bordered>
        <q-card-section class="row justify-between items-center">
          <div class="text-h6 text-weight-bold">나의 문의</div>
          <q-btn flat color="primary" label="전체보기" @click="$router.push('/learning?tab=inquiries')" />
        </q-card-section>
        <q-separator />
        <q-list v-if="inquiries.length" separator>
          <q-item v-for="item in inquiries" :key="item.id">
            <q-item-section>{{ item.title }}</q-item-section>
            <q-item-section side>
              <q-badge color="orange">{{ item.status }}</q-badge>
            </q-item-section>
          </q-item>
        </q-list>
        <div v-else class="text-center text-grey-6 q-pa-xl">등록된 문의가 없습니다.</div>
      </q-card>
    </div>
  </q-page>
</template>

<script>
import { mapActions, mapState } from 'pinia';
import { MOCK_INQUIRIES, MOCK_LEARNING_SUMMARY, MOCK_MY_COURSES } from '@/data/ui-mock.js';
import { useAuthStore } from '@/stores/auth-store';

export default {
  name: 'MyPage',
  data() {
    return {
      inquiries: MOCK_INQUIRIES,
      recentCourses: MOCK_MY_COURSES.slice(0, 2),
      profileName: '',
      profileEmail: '',
      profilePhone: '',
      profileBirthDate: '',
      savingProfile: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['user']),
    summaryCards() {
      return [
        { label: '신청중', value: MOCK_LEARNING_SUMMARY.applying },
        { label: '수강중', value: MOCK_LEARNING_SUMMARY.inProgress },
        { label: '수강완료', value: MOCK_LEARNING_SUMMARY.finished },
      ];
    },
  },
  watch: {
    user: {
      immediate: true,
      handler(value) {
        if (!value) return;
        this.profileName = value.name || '';
        this.profileEmail = value.email || '';
        this.profilePhone = value.phone || '';
        this.profileBirthDate = value.birthDate ? String(value.birthDate).slice(0, 10) : '';
      },
    },
  },
  methods: {
    ...mapActions(useAuthStore, ['updateMyProfile']),
    async saveProfile() {
      this.savingProfile = true;
      try {
        await this.updateMyProfile({
          name: this.profileName.trim(),
          email: this.profileEmail.trim() || null,
          phone: this.profilePhone.trim() || null,
          birthDate: this.profileBirthDate || null,
        });
        this.$q.notify({ type: 'positive', message: '프로필을 저장했습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '저장에 실패했습니다.', position: 'top' });
      } finally {
        this.savingProfile = false;
      }
    },
  },
};
</script>

<style scoped>
.page-wrap {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
