<template>
  <aside class="info-aside">
    <section class="info-aside__block">
      <h3 class="info-aside__title">강사 정보</h3>
      <template v-if="instructor">
        <div class="info-aside__avatar">{{ avatarLetter }}</div>
        <div class="info-aside__name">{{ instructor.name || '이름 미등록' }}</div>
        <!-- 배정 행만 받은 경우(limited) 생년월일·성별·연락처는 "미등록"이 아니라 볼 권한이 없을 뿐 — 줄째 숨긴다 -->
        <div v-if="!instructorLimited" class="info-aside__line">
          <span :class="{ 'info-aside__missing': !instructor.birthDate }">
            {{ instructor.birthDate ? formatDateKst(instructor.birthDate) : '생년월일 미등록' }}
          </span>
          <i class="info-aside__sep" />
          <span>강사</span>
        </div>
        <!-- 빈 값은 "-" 대신 무엇이 비었는지 적는다 — "-"만으로는 어떤 칸인지 알 수 없다 -->
        <div v-for="field in instructorFields" :key="field.key" class="info-aside__line">
          <span :class="{ 'info-aside__missing': !field.value }">{{ field.value || `${field.label} 미등록` }}</span>
        </div>
      </template>
      <div v-else class="info-aside__empty">배정된 강사가 없습니다.</div>
    </section>

    <hr class="info-aside__divider" />

    <section class="info-aside__block">
      <h3 class="info-aside__title">소속 기관</h3>
      <template v-if="institution">
        <div class="info-aside__avatar info-aside__avatar--logo">
          <q-icon name="apartment" size="36px" color="grey-7" />
        </div>
        <div class="info-aside__name">{{ institution.name || '기관명 미등록' }}</div>
        <div v-for="field in institutionFields" :key="field.key" class="info-aside__line">
          <span :class="{ 'info-aside__missing': !field.value }">{{ field.value || `${field.label} 미등록` }}</span>
        </div>
      </template>
      <div v-else class="info-aside__empty">소속 기관 정보가 없습니다.</div>
    </section>
  </aside>
</template>

<script>
import { formatDateKst } from '@/utils/datetime';

const GENDER_LABEL = { MALE: '남성', FEMALE: '여성', UNDISCLOSED: '밝히지 않음' };

export default {
  name: 'CourseInfoAside',
  props: {
    instructor: { type: Object, default: null },
    institution: { type: Object, default: null },
    /** 강사 정보가 배정 행(이름·이메일)뿐이면 true */
    instructorLimited: { type: Boolean, default: false },
  },
  computed: {
    instructorFields() {
      const i = this.instructor || {};
      const email = { key: 'email', label: '이메일', value: i.email };
      if (this.instructorLimited) return [email];
      return [
        email,
        { key: 'gender', label: '성별', value: this.genderLabel },
        { key: 'phone', label: '연락처', value: i.phone },
      ];
    },
    institutionFields() {
      const i = this.institution || {};
      return [
        { key: 'address', label: '주소', value: i.address },
        { key: 'email', label: '이메일', value: i.contactEmail },
        { key: 'phone', label: '연락처', value: i.contactPhone },
      ];
    },
    avatarLetter() {
      return (this.instructor?.name || '?').trim().charAt(0).toUpperCase();
    },
    genderLabel() {
      return GENDER_LABEL[this.instructor?.gender] || '';
    },
  },
  methods: { formatDateKst },
};
</script>

<style scoped>
.info-aside {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 320px;
  flex-shrink: 0;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.info-aside__block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.info-aside__title {
  align-self: flex-start;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-body);
}

.info-aside__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 2.5px solid var(--db-border-avatar);
  background: var(--db-border-avatar);
  font-size: 28px;
  font-weight: 700;
  color: var(--db-text-body);
}

.info-aside__name {
  font-size: 16px;
  font-weight: 700;
  color: var(--db-text-dark);
}

.info-aside__missing {
  /* placeholder 색은 흰 바탕 대비가 모자라다 — 보조 글자색 + "미등록" 문구로 구분 */
  color: var(--db-text-sub);
}

.info-aside__line {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.info-aside__sep {
  width: 1px;
  height: 10px;
  background: var(--db-border);
}

.info-aside__empty {
  align-self: flex-start;
  font-size: 14px;
  color: var(--db-text-sub);
}

.info-aside__divider {
  width: 100%;
  height: 1px;
  margin: 10px 0;
  border: none;
  background: var(--db-border);
}

@media (max-width: 1023px) {
  .info-aside {
    width: 100%;
  }
}
</style>
