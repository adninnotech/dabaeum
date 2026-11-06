<template>
  <!-- 강좌찾기 전체 메뉴 — 머리 [강좌찾기]에 마우스를 올리면 평생교육 6대 영역(대분류)과 그 아래 중분류가 6칸으로 펼쳐진다.
       하위 없는 대분류(성인진로 등)·전체 보기·대구 다른 기관 강좌는 아래 줄에 -->
  <div
    v-show="open && columns.length"
    id="course-mega-menu"
    class="course-mega"
    role="region"
    aria-label="강좌 분야 전체 메뉴"
    @mouseenter="$emit('hold')"
    @mouseleave="$emit('leave')"
    @keydown="onKeydown"
    @focusout="onFocusOut"
  >
    <div class="learner-container course-mega__inner">
      <div class="course-mega__grid">
        <section v-for="root in columns" :key="root.code" class="course-mega__col">
          <router-link :to="tabRoute(root)" class="course-mega__root" @click="$emit('close')">
            {{ root.name }}
          </router-link>
          <ul class="course-mega__list">
            <li v-for="middle in root.children" :key="middle.code">
              <router-link :to="tabRoute(root, middle)" class="course-mega__link" @click="$emit('close')">
                {{ middle.name }}
              </router-link>
            </li>
          </ul>
        </section>
      </div>
      <div class="course-mega__foot">
        <router-link
          v-for="root in singles"
          :key="root.code"
          :to="tabRoute(root)"
          class="course-mega__link"
          @click="$emit('close')"
        >
          {{ root.name }}
        </router-link>
        <span class="course-mega__spacer" />
        <router-link to="/courses?scope=public" class="course-mega__link" @click="$emit('close')">
          대구 다른 기관 강좌
        </router-link>
        <router-link to="/courses" class="course-mega__all" @click="$emit('close')">
          전체 강좌 보기
          <q-icon name="arrow_forward" size="16px" />
        </router-link>
      </div>
    </div>
  </div>
</template>

<script>
import courseClassificationMenu from '@/mixins/course-classification-menu';

export default {
  name: 'CourseMegaMenu',
  mixins: [courseClassificationMenu],
  props: {
    open: { type: Boolean, default: false },
  },
  /** hold: 패널 위에 있다(닫기 예약 취소) · leave: 패널을 벗어났다 · close: 바로 닫는다 */
  emits: ['hold', 'leave', 'close'],
  watch: {
    // 처음 펼칠 때 받는다. 못 받으면 패널을 띄우지 않는다 — 머리 [강좌찾기]는 그대로 간다
    open(value) {
      if (value && !this.tree.length) this.loadTree();
    },
  },
  methods: {
    /** 키보드로 펼쳤을 때 첫 분야로 초점을 옮긴다 */
    async focusFirst() {
      await this.loadTree();
      await this.$nextTick();
      this.$el.querySelector?.('a')?.focus();
    },
    onKeydown(event) {
      if (event.key === 'Escape') this.$emit('close', { restoreFocus: true });
    },
    // 키보드로 패널 밖으로 나가면 닫는다
    onFocusOut(event) {
      if (!this.$el.contains(event.relatedTarget)) this.$emit('leave');
    },
  },
};
</script>

<style scoped>
.course-mega {
  position: absolute;
  top: 100%;
  right: 0;
  left: 0;
  background: var(--db-bg-white);
  border-top: 1px solid var(--db-border);
  border-bottom: 1px solid var(--db-border);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
  color: var(--db-text-body);
}

.course-mega__inner {
  padding-top: 24px;
  padding-bottom: 16px;
}

.course-mega__grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

/* 칸 사이 세로선 — 어느 분야 아래 줄인지 눈으로 따라가기 쉽게(벤치마킹: 대구평생학습플랫폼 수강신청) */
.course-mega__col {
  min-width: 0;
  padding: 0 14px;
  border-left: 1px solid var(--db-border);
}

.course-mega__col:first-child {
  padding-left: 0;
  border-left: 0;
}

/* 중장년 이용자가 많다 — 글자는 본문 이상(17·16px), 줄마다 누를 자리는 44px 높이 */
.course-mega__root {
  display: block;
  padding-bottom: 10px;
  margin-bottom: 4px;
  border-bottom: 2px solid var(--db-accent);
  font-size: 17px;
  font-weight: 700;
  color: var(--db-text-title);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.course-mega__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.course-mega__link {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-size: 16px;
  color: var(--db-text-body);
  text-decoration: none;
  white-space: nowrap;
}

.course-mega__root:hover,
.course-mega__link:hover,
.course-mega__all:hover {
  color: var(--db-accent);
  text-decoration: underline;
}

.course-mega__root:focus-visible,
.course-mega__link:focus-visible,
.course-mega__all:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.course-mega__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 24px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--db-border);
}

.course-mega__spacer {
  flex: 1;
}

.course-mega__all {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  font-size: 16px;
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: none;
  white-space: nowrap;
}

@media (prefers-reduced-motion: no-preference) {
  .course-mega {
    animation: course-mega-in 160ms ease-out;
  }
}

@keyframes course-mega-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}
</style>
