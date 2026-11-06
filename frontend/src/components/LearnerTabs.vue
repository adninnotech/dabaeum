<template>
  <nav class="learner-tabs">
    <!-- 탭 묶음만 tablist — 오른쪽 슬롯(정렬 select)은 탭이 아니라 밖에 둔다.
         화살표 키로 옮기고, 선택된 탭만 Tab 순서에 넣는다(로빙 tabindex) -->
    <div class="learner-tabs__list" role="tablist" @keydown="onKeydown">
      <button
        v-for="tab in tabs"
        :key="tab"
        type="button"
        role="tab"
        class="learner-tabs__item"
        :class="{ active: tab === modelValue }"
        :aria-selected="tab === modelValue ? 'true' : 'false'"
        :tabindex="tab === modelValue || (!tabs.includes(modelValue) && tab === tabs[0]) ? 0 : -1"
        @click="$emit('update:modelValue', tab)"
      >
        {{ tab }}
      </button>
    </div>
    <!-- 탭 행 우측 (관심 강좌 정렬 select) — 독립 행으로 빼면 Figma 탭 행 48을 벗어난다 -->
    <span class="learner-tabs__right"><slot name="right" /></span>
  </nav>
</template>

<script>
// 줄을 밀 때 왼쪽에 남기는 여백 — 앞 탭이 조금 보여 더 있다는 걸 안다
const REVEAL_GAP = 24;

export default {
  name: 'LearnerTabs',
  props: {
    tabs: {
      type: Array,
      default: () => [],
    },
    modelValue: {
      type: String,
      default: '',
    },
  },
  emits: ['update:modelValue'],
  watch: {
    // 폰에선 탭 줄이 옆으로 밀린다 — 강좌 상세에서 스크롤로 Q&A 가 골라져도 탭이 화면 밖에 있어 어디인지 몰랐다
    modelValue() {
      this.$nextTick(this.revealActive);
    },
  },
  methods: {
    /** 고른 탭이 줄 밖에 있으면 줄만 가로로 민다(창 세로 스크롤은 건드리지 않는다) */
    revealActive() {
      const row = this.$el;
      const tab = row?.querySelector('[role="tab"][aria-selected="true"]');
      if (!tab || row.scrollWidth <= row.clientWidth) return;
      const left = tab.getBoundingClientRect().left - row.getBoundingClientRect().left + row.scrollLeft;
      const right = left + tab.offsetWidth;
      if (left >= row.scrollLeft && right <= row.scrollLeft + row.clientWidth) return;
      row.scrollTo({ left: Math.max(0, left - REVEAL_GAP), behavior: 'smooth' });
    },
    /** 좌우 화살표·Home·End 로 탭을 고르고 포커스도 따라 옮긴다 (WAI-ARIA tabs 자동 선택 방식) */
    onKeydown(event) {
      const count = this.tabs.length;
      if (!count) return;
      const current = Math.max(this.tabs.indexOf(this.modelValue), 0);
      const nextIndex = {
        ArrowRight: (current + 1) % count,
        ArrowLeft: (current - 1 + count) % count,
        Home: 0,
        End: count - 1,
      }[event.key];
      if (nextIndex === undefined) return;
      event.preventDefault();
      this.$emit('update:modelValue', this.tabs[nextIndex]);
      // v-for ref 배열은 순서를 보장하지 않아 DOM 순서로 찾는다
      const buttons = this.$el.querySelectorAll('[role="tab"]');
      if (buttons[nextIndex]) buttons[nextIndex].focus();
    },
  },
};
</script>

<style scoped>
.learner-tabs {
  display: flex;
  gap: 24px;
  height: 48px;
  border-bottom: 2px solid var(--db-border);
}

/* 탭 묶음 — 예전엔 nav 에 바로 있던 간격·높이를 그대로 옮겼다 */
.learner-tabs__list {
  display: flex;
  flex-shrink: 0;
  gap: 24px;
  height: 100%;
}

.learner-tabs__right {
  display: flex;
  align-items: center;
  margin-left: auto;
  padding-right: 10px;
}

.learner-tabs__item {
  padding: 0 4px;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  font-size: 16px;
  font-weight: 500;
  color: var(--db-text-sub);
  white-space: nowrap;
  cursor: pointer;
}

.learner-tabs__item {
  position: relative;
  transition: color 0.18s ease;
}

/* 머리 메뉴와 같은 말투 — 올리면 글자가 진해지고 밑줄이 가운데서 펴진다. 고른 탭은 이미 밑줄이 있어 뺀다 */
.learner-tabs__item:not(.active)::after {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: 0;
  height: 2px;
  background: var(--db-accent);
  transform: scaleX(0);
  transition: transform 0.22s cubic-bezier(0.2, 0.7, 0.2, 1);
}

.learner-tabs__item:not(.active):hover {
  color: var(--db-text-title);
}

.learner-tabs__item:not(.active):hover::after {
  transform: scaleX(1);
}

.learner-tabs__item:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.learner-tabs__item.active {
  border-bottom-color: var(--db-accent);
  font-weight: 700;
  color: var(--db-accent);
}

/* 폰: 탭이 375 폭을 넘어 페이지 전체가 가로로 밀리던 문제 — 탭 줄만 가로 스크롤.
 * overflow 는 border 영역을 자르므로 밑줄은 inset 그림자로 옮긴다 */
@media (max-width: 1023px) {
  .learner-tabs {
    overflow-x: auto;
    overflow-y: hidden;
    scrollbar-width: none;
    border-bottom: 0;
    box-shadow: inset 0 -2px 0 var(--db-border);
  }

  .learner-tabs::-webkit-scrollbar {
    display: none;
  }

  .learner-tabs__item {
    flex-shrink: 0;
    margin-bottom: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .learner-tabs__item,
  .learner-tabs__item::after {
    transition: none;
  }
}

</style>
