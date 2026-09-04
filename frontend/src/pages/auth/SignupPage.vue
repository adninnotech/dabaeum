<template>
  <q-page class="signup-page">
    <header class="signup-header row items-center justify-between">
      <div class="row items-center text-h6 text-weight-bold"><BrandLogo :size="32" logo-class="q-mr-sm" />다배움</div>
      <div class="text-grey-7">이미 회원이신가요? <router-link to="/login">로그인</router-link></div>
    </header>
    <main class="signup-inner">
      <div class="text-h4 text-weight-bold text-center">회원가입</div>
      <div class="steps row no-wrap q-my-xl">
        <div v-for="item in steps" :key="item.no" class="step col row items-center">
          <q-avatar :color="item.no === 1 ? 'primary' : 'grey-4'" :text-color="item.no === 1 ? 'white' : 'grey-7'" size="34px">{{ item.no }}</q-avatar>
          <span :class="item.no === 1 ? 'text-primary text-weight-bold' : 'text-grey-6'" class="q-ml-sm">{{ item.label }}</span>
          <q-separator v-if="item.no < 3" class="col q-mx-md" />
        </div>
      </div>
      <div class="text-h5 text-weight-bold text-center">{{ operatorSelected ? '교육운영자 유형을 선택해 주세요' : '가입 유형을 선택해 주세요' }}</div>
      <div class="text-grey-7 text-center q-mt-sm q-mb-xl">서비스 이용 목적에 맞는 회원 유형을 선택해 주세요.</div>
      <div class="row q-col-gutter-lg justify-center">
        <div v-for="role in visibleRoles" :key="role.value" class="col-12 col-sm-6">
          <q-card v-ripple flat bordered class="role-card cursor-pointer text-center" @click="selectRole(role.value)">
            <q-card-section>
              <q-avatar color="blue-1" text-color="primary" :icon="role.icon" size="72px" />
              <div class="text-h6 text-weight-bold q-mt-lg">{{ role.label }}</div>
              <div class="text-grey-7 q-mt-sm">{{ role.description }}</div>
            </q-card-section>
          </q-card>
        </div>
      </div>
      <q-btn v-if="operatorSelected" flat icon="arrow_back" label="이전 선택으로" class="q-mt-lg" @click="operatorSelected = false" />
    </main>
  </q-page>
</template>

<script>
import BrandLogo from '@/components/BrandLogo.vue';

export default {
  name: 'SignupPage',
  components: { BrandLogo },
  data() {
    return {
      operatorSelected: false,
      steps: [{ no: 1, label: '역할선택' }, { no: 2, label: '정보입력' }, { no: 3, label: '가입완료' }],
      baseRoles: [
        { value: 'LEARNER', label: '일반회원', icon: 'school', description: '다양한 강의를 찾고 수강합니다.' },
        { value: 'OPERATOR', label: '교육운영자', icon: 'business_center', description: '강의 또는 교육기관을 운영합니다.' },
      ],
      operatorRoles: [
        { value: 'INSTRUCTOR', label: '개인강사', icon: 'person_outline', description: '직접 강의를 개설하고 운영합니다.' },
        { value: 'INSTITUTION_ADMIN', label: '기관관리자', icon: 'apartment', description: '소속 기관과 강사를 관리합니다.' },
      ],
    };
  },
  computed: {
    visibleRoles() {
      return this.operatorSelected ? this.operatorRoles : this.baseRoles;
    },
  },
  methods: {
    selectRole(role) {
      if (role === 'OPERATOR') {
        this.operatorSelected = true;
        return;
      }
      this.$router.push({ path: '/signup/form', query: { role } });
    },
  },
};
</script>

<style scoped>
.signup-page { min-height: 100vh; background: #fff; }
.signup-header { height: 72px; padding: 0 5vw; border-bottom: 1px solid #eee; }
.signup-inner { max-width: 800px; margin: 0 auto; padding: 64px 24px; }
.role-card { min-height: 230px; padding: 28px 12px; border-radius: 16px; transition: .2s; }
.role-card:hover { border-color: var(--q-primary); transform: translateY(-3px); box-shadow: 0 8px 24px #1d4ed818; }
@media (max-width: 599px) { .step span { font-size: 12px; } .steps .q-separator { margin: 0 6px; } }
</style>
