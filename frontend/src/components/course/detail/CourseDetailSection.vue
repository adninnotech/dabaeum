<template>
  <!-- 강좌 상세 한 섹션의 틀 — 앵커 탭이 data-tab 으로 찾아 내려온다(CourseDetailPage observeSections·goSection) -->
  <section class="detail-section" :data-tab="tab">
    <!-- 폰에선 바로 위 고정 탭에 같은 이름이 있다 — 건수·버튼이 없는 제목은 숨긴다(화면낭독기엔 남긴다) -->
    <h2 class="detail-section__title" :class="{ 'detail-section__title--plain': !$slots.count && !$slots.action }">
      {{ title }}
      <span v-if="$slots.count" class="detail-section__count"><slot name="count" /></span>
      <span v-if="$slots.action" class="detail-section__action"><slot name="action" /></span>
    </h2>
    <slot name="before" />
    <div class="detail-section__body" :class="{ 'detail-section__body--wide': wide }">
      <slot />
    </div>
  </section>
</template>

<script>
export default {
  name: 'CourseDetailSection',
  props: {
    /** 앵커 탭 이름 — TABS 와 같아야 한다 */
    tab: { type: String, required: true },
    title: { type: String, required: true },
    /** 본문 여백을 넓게(소개·유의사항 같은 긴 글) */
    wide: { type: Boolean, default: false },
  },
};
</script>

<style scoped>
@media (max-width: 1023px) {
  .detail-section__title.detail-section__title--plain {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
}

/* 섹션 */
.detail-section {
  border: 1px solid var(--db-border);
  border-radius: 8px;
  /* 머리 68px(화면) + 탭 48px — 머리 몫만 zoom 으로 나눈다 */
  scroll-margin-top: calc(68px / var(--db-content-zoom, 1) + 48px);
}

.detail-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 12px 16px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
  color: var(--db-text-title);
}

/* 리뷰 섹션만 안쪽 10px 래퍼가 있어 제목이 다른 칸보다 안쪽에서 시작했다 — 다른 섹션과 같게 */
.detail-section__count {
  font-size: 14px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.detail-section__action {
  margin-left: auto;
}

.detail-section__body {
  padding: 10px 16px 16px;
  font-size: 14px;
  color: var(--db-text-body);
}

.detail-section__body--wide {
  padding: 24px 10px;
}

@media (max-width: 599px) {
  .detail-section {
    background: var(--db-bg-white);
  }
}
</style>
