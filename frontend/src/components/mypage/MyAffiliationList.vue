<template>
  <!-- 신분증처럼 이름 아래 소속 — 소속은 기관이 정해 준 것이라 확인 표시(✔)로 '등록됨'을 보인다
       (GET /users/me/institutions, 로그인 때 받아 둔다. 여기서는 바꿀 수 없다).
       콘솔 마이페이지(MyAccountPanel)와 학습자 마이페이지(MyPage)가 같이 쓴다 -->
  <ul v-if="affiliations.length" class="my-affiliation__list" aria-label="소속 기관">
    <!-- 이름 아래 부가 정보 한 줄 — 학번 줄과 같은 크기. 초록은 '기관이 등록한 소속' 체크 하나에만 쓴다
         (테두리·초록 바탕·두 줄 칩은 이름보다 무거워 프로필 카드에서 튀었다) -->
    <li
      v-for="item in affiliations"
      :key="`${item.institutionId}-${item.role}`"
      class="my-affiliation"
    >
      <span class="my-affiliation__line" :title="`${item.name} 소속 ${item.roleLabel}`">
        <!-- 기관 로고(없으면 건물 그림) -->
        <span class="my-affiliation__logo" aria-hidden="true">
          <img v-if="institutionLogos[item.institutionId]" :src="institutionLogos[item.institutionId]" alt="" />
          <q-icon v-else name="apartment" size="14px" />
        </span>
        <span class="my-affiliation__text">{{ item.name }} · {{ item.roleLabel }}</span>
        <q-icon name="verified" size="14px" class="my-affiliation__check" aria-label="기관 등록 소속" />
      </span>
    </li>
  </ul>
  <div v-else-if="hasInstructorRole" class="my-affiliation__empty">
    소속 기관이 없습니다. 기관 관리자에게 소속 등록을 요청해 주세요.
  </div>
</template>

<script>
import { mapState } from 'pinia';
import { storedFileContentUrl } from '@/services/file-api';
import { getInstitution } from '@/services/institution-api';
import { useAuthStore } from '@/stores/auth-store';

// 소속 기관 역할 이름(GET /users/me/institutions role)
const AFFILIATION_ROLE_LABEL = { INSTRUCTOR: '강사', INSTITUTION_ADMIN: '기관 관리자' };
const ROLE_ORDER = { INSTRUCTOR: 0, INSTITUTION_ADMIN: 1 };

export default {
  name: 'MyAffiliationList',
  data() {
    return {
      // 소속 기관 로고 { institutionId: url } — 소속 목록엔 로고가 없어 기관 상세에서 받는다
      institutionLogos: {},
    };
  },
  computed: {
    ...mapState(useAuthStore, ['user', 'myInstitutions']),
    /** 소속 기관 — 강사 먼저, 같은 기관에 두 역할이면 줄을 나눠 보인다 */
    affiliations() {
      return [...(this.myInstitutions || [])]
        .filter((item) => item?.name)
        .sort((a, b) => (ROLE_ORDER[a.role] ?? 9) - (ROLE_ORDER[b.role] ?? 9))
        .map((item) => ({ ...item, roleLabel: AFFILIATION_ROLE_LABEL[item.role] || '소속' }));
    },
    // 강사 역할을 직접 가진 계정만 '소속 없음'을 알린다(관리자는 강사 화면을 열 수 있어도 소속이 없는 게 정상)
    hasInstructorRole() {
      return (this.user?.roles || []).some((role) => (role?.role || role) === 'INSTRUCTOR');
    },
  },
  watch: {
    myInstitutions: {
      immediate: true,
      handler() {
        this.loadInstitutionLogos();
      },
    },
  },
  methods: {
    /** 소속 기관 로고 — 기관마다 GET /institutions/{id}. 못 받거나 로고가 없으면 건물 그림으로 둔다(화면은 그대로) */
    async loadInstitutionLogos() {
      const ids = [...new Set((this.myInstitutions || []).map((item) => item?.institutionId).filter(Boolean))];
      const missing = ids.filter((id) => !(id in this.institutionLogos));
      if (!missing.length) return;
      const results = await Promise.allSettled(missing.map((id) => getInstitution(id)));
      const next = { ...this.institutionLogos };
      results.forEach((result, index) => {
        next[missing[index]] = result.status === 'fulfilled' ? storedFileContentUrl(result.value?.logoFileId) : '';
      });
      this.institutionLogos = next;
    },
  },
};
</script>

<style scoped>
.my-affiliation__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

/* 줄 맞춤은 감싼 쪽 text-align 을 따른다 — 콘솔·학습자 PC 는 가운데, 학습자 모바일은 왼쪽 */
.my-affiliation + .my-affiliation {
  margin-top: 4px;
}

.my-affiliation__line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
}

.my-affiliation__logo {
  display: inline-grid;
  flex-shrink: 0;
  place-items: center;
  width: 20px;
  height: 20px;
  overflow: hidden;
  border-radius: 4px;
  color: var(--db-text-sub);
}

.my-affiliation__logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.my-affiliation__text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.my-affiliation__check {
  flex-shrink: 0;
  color: var(--db-status-green-text);
}

.my-affiliation__empty {
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}
</style>
