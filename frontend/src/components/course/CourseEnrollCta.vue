<template>
  <!-- 강좌 상세 신청 줄 — 머리(데스크톱)와 폰 아래 고정 줄이 같이 쓴다.
       [목록]은 신청과 같은 크기의 버튼이었다 — 돌아가는 길이라 머리 위 글자 링크로 뺐다 -->
  <div class="enroll-cta">
    <q-btn
      outline
      no-caps
      color="primary"
      class="enroll-cta__interest"
      :icon="interested ? 'favorite' : 'favorite_border'"
      :aria-label="interested ? '관심 강좌에서 빼기' : '관심 강좌에 담기'"
      :aria-pressed="interested ? 'true' : 'false'"
      :loading="interestLoading"
      @click="$emit('toggle-interest')"
    >
      <q-tooltip>{{ interested ? '관심 강좌에서 빼기' : '관심 강좌에 담기' }}</q-tooltip>
    </q-btn>
    <!-- 이미 신청한 강좌에서 [수강신청]을 다시 누르면 서버 오류만 떠서, 진행 중인 신청 상태를 대신 보인다 -->
    <div v-if="activeEnrollment" class="enroll-cta__main enroll-cta__status">
      <span class="enroll-cta__status-label">
        <q-icon name="check_circle" size="20px" />
        {{ activeEnrollmentLabel }}
      </span>
      <q-btn
        flat
        dense
        no-caps
        color="primary"
        class="enroll-cta__status-link"
        label="신청 내역 보기"
        :to="`/enrollment/${activeEnrollment.id}`"
      />
    </div>
    <q-btn
      v-else
      unelevated
      no-caps
      color="primary"
      class="enroll-cta__main"
      :label="enrollLabel"
      :disable="enrollDisabled"
      :loading="applying"
      @click="$emit('apply')"
    />
  </div>
</template>

<script>
export default {
  name: 'CourseEnrollCta',
  props: {
    interested: { type: Boolean, default: false },
    interestLoading: { type: Boolean, default: false },
    /** 진행 중인 내 신청(신청·대기·승인) 또는 null */
    activeEnrollment: { type: Object, default: null },
    activeEnrollmentLabel: { type: String, default: '' },
    enrollLabel: { type: String, default: '수강신청' },
    enrollDisabled: { type: Boolean, default: false },
    applying: { type: Boolean, default: false },
  },
  emits: ['toggle-interest', 'apply'],
};
</script>

<style scoped>
.enroll-cta {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* Quasar 기본 min-height(2.572em)가 이겨서 높이가 안 먹는다 */
.enroll-cta :deep(.q-btn) {
  height: 48px;
  min-height: 48px;
  border-radius: 6px;
}

/* 하트는 정사각 아이콘 버튼 — 폰에서 '관심 강좌 담기' 글자가 아이콘 아래로 꺾였다 */
.enroll-cta__interest {
  flex: 0 0 48px;
  width: 48px;
  padding: 0;
}

.enroll-cta__main {
  flex: 1;
  min-width: 0;
}

/* 신청 상태 — 버튼 자리(48px)를 그대로 차지한다 */
.enroll-cta__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 48px;
  padding: 0 8px 0 16px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-page);
}

.enroll-cta__status-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  font-size: 15px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
}

.enroll-cta .enroll-cta__status :deep(.q-btn) {
  flex-shrink: 0;
  height: 36px;
  min-height: 36px;
}
</style>
