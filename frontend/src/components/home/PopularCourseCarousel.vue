<template>
  <!-- 홈 인기 강좌 — 큰 카드 4개(태블릿 2·폰 1)를 보이고 5초마다 한 화면(4개)씩 통째로 넘긴다(배너와 같은 간격).
       마우스·키보드 초점이 안에 있으면 멈추고, [일시정지]로 계속 멈출 수 있다(움직이는 내용은 멈출 수 있어야 한다) -->
  <div
    class="popular-carousel"
    aria-roledescription="carousel"
    aria-label="인기 강좌"
    @mouseenter="pause"
    @mouseleave="resume"
    @focusin="onFocusIn"
    @focusout="resume"
  >
    <div
      class="popular-carousel__viewport"
      @touchstart.passive="onTouchStart"
      @touchend="onTouchEnd"
    >
      <div class="popular-carousel__track" :style="trackStyle" :aria-live="autoplaying ? 'off' : 'polite'">
        <div
          v-for="(course, i) in courses"
          :key="course.id"
          class="popular-carousel__slide"
          role="group"
          aria-roledescription="slide"
          :aria-label="`${i + 1} / ${courses.length}`"
          :data-index="i"
        >
          <CourseCard
            :course="course"
            :rank="i + 1"
            :interested="isInterested(course.id)"
            @toggle-interest="$emit('toggle-interest', $event)"
          />
        </div>
      </div>
    </div>

    <div v-if="canSlide" class="popular-carousel__controls">
      <q-btn
        outline
        round
        color="grey-8"
        icon="chevron_left"
        class="popular-carousel__btn"
        aria-label="이전 강좌"
        @click="step(-1)"
      />
      <span class="popular-carousel__count" aria-hidden="true">{{ positionLabel }}</span>
      <q-btn
        outline
        round
        color="grey-8"
        icon="chevron_right"
        class="popular-carousel__btn"
        aria-label="다음 강좌"
        @click="step(1)"
      />
      <q-btn
        flat
        no-caps
        color="grey-8"
        class="popular-carousel__toggle"
        :icon="userPaused ? 'play_arrow' : 'pause'"
        :label="userPaused ? '자동 넘김' : '일시정지'"
        :aria-pressed="userPaused ? 'true' : 'false'"
        @click="toggleAutoplay"
      />
    </div>
  </div>
</template>

<script>
import CourseCard from '@/components/course/CourseCard.vue';

// 배너(BANNER_AUTOPLAY_MS)와 같은 간격
const AUTOPLAY_MS = 5000;
const SLIDE_GAP_PX = 16;
// 폰에서 이만큼 옆으로 밀면 한 화면 넘긴다(세로 스크롤과 헷갈리지 않게 가로가 더 클 때만)
const SWIPE_MIN_PX = 40;

function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
}

export default {
  name: 'PopularCourseCarousel',
  components: { CourseCard },
  props: {
    courses: { type: Array, default: () => [] },
    /** 관심 강좌 여부 — 부모의 course-interest mixin 을 그대로 쓴다 */
    isInterested: { type: Function, default: () => false },
  },
  emits: ['toggle-interest'],
  data() {
    return {
      index: 0,
      timer: null,
      hoverPaused: false,
      // 움직임 줄이기를 켠 기기는 처음부터 멈춰 둔다(누르면 넘어간다)
      userPaused: prefersReducedMotion(),
      touchStart: null,
    };
  },
  computed: {
    perView() {
      if (this.$q.screen.lt.sm) return 1;
      if (this.$q.screen.lt.md) return 2;
      return 4;
    },
    maxIndex() {
      return Math.max(0, this.courses.length - this.perView);
    },
    canSlide() {
      return this.maxIndex > 0;
    },
    autoplaying() {
      return Boolean(this.timer);
    },
    trackStyle() {
      return {
        '--per-view': this.perView,
        '--slide-gap': `${SLIDE_GAP_PX}px`,
        '--slide-index': Math.min(this.index, this.maxIndex),
      };
    },
    // 몇 번째 화면인지 — '1 / 2' (한 화면씩 넘기므로 강좌 번호 범위보다 쉽다)
    pageCount() {
      return Math.ceil(this.courses.length / this.perView);
    },
    positionLabel() {
      const index = Math.min(this.index, this.maxIndex);
      const page = index >= this.maxIndex ? this.pageCount : Math.floor(index / this.perView) + 1;
      return `${page} / ${this.pageCount}`;
    },
  },
  watch: {
    courses() {
      this.index = 0;
      this.start();
    },
    // 폭이 바뀌어 한 화면 칸 수가 달라지면 끝을 넘지 않게
    perView() {
      this.index = Math.min(this.index, this.maxIndex);
      this.start();
    },
  },
  mounted() {
    this.start();
  },
  beforeUnmount() {
    this.stop();
  },
  methods: {
    // 한 화면(perView 개)씩 통째로 넘긴다 — 한 칸씩 밀면 같은 강좌가 계속 보여 넘어갔는지 알기 어렵다는 말이 나왔다.
    // 마지막 화면은 끝에 맞춘다(7개면 1–4 → 4–7). 끝까지 가면 처음으로
    advance(delta) {
      if (!this.canSlide) return;
      const { pageCount } = this;
      const page = Math.round(Math.min(this.index, this.maxIndex) / this.perView);
      const next = (page + delta + pageCount) % pageCount;
      this.index = Math.min(next * this.perView, this.maxIndex);
    },
    // 손으로 넘기면 타이머를 처음부터 — 넘기자마자 또 넘어가지 않게
    step(delta) {
      this.advance(delta);
      this.start();
    },
    start() {
      this.stop();
      if (!this.canSlide || this.hoverPaused || this.userPaused) return;
      this.timer = window.setInterval(() => this.advance(1), AUTOPLAY_MS);
    },
    stop() {
      if (this.timer) window.clearInterval(this.timer);
      this.timer = null;
    },
    pause() {
      this.hoverPaused = true;
      this.stop();
    },
    resume(event) {
      // 초점이 안에서 옮겨 다닐 때는 계속 멈춰 둔다
      if (event?.relatedTarget && this.$el.contains(event.relatedTarget)) return;
      this.hoverPaused = false;
      this.start();
    },
    toggleAutoplay() {
      this.userPaused = !this.userPaused;
      // 재생을 누른 순간은 버튼에 마우스·초점이 있다 — 명시적 재생이 이긴다
      if (!this.userPaused) this.hoverPaused = false;
      this.start();
    },
    onTouchStart(event) {
      const touch = event.touches[0];
      this.touchStart = touch ? { x: touch.clientX, y: touch.clientY } : null;
      this.pause();
    },
    onTouchEnd(event) {
      const touch = event.changedTouches[0];
      const start = this.touchStart;
      this.touchStart = null;
      this.hoverPaused = false;
      if (!start || !touch) {
        this.start();
        return;
      }
      const dx = touch.clientX - start.x;
      const dy = touch.clientY - start.y;
      if (Math.abs(dx) >= SWIPE_MIN_PX && Math.abs(dx) > Math.abs(dy)) this.step(dx < 0 ? 1 : -1);
      else this.start();
    },
    // Tab 으로 화면 밖 카드에 들어가면 그 카드가 보이게 옮긴다
    onFocusIn(event) {
      this.pause();
      const slide = event.target.closest?.('.popular-carousel__slide');
      if (!slide) return;
      const i = Number(slide.dataset.index);
      const first = Math.min(this.index, this.maxIndex);
      // 화면 밖 카드면 그 카드가 든 화면으로(한 화면 단위 그대로)
      if (i < first || i > first + this.perView - 1) {
        this.index = Math.min(Math.floor(i / this.perView) * this.perView, this.maxIndex);
      }
    },
  },
};
</script>

<style scoped>
.popular-carousel__viewport {
  overflow: hidden;
  /* 카드 그림자·초점 테두리가 잘리지 않게 */
  padding: 4px;
  margin: -4px;
}

.popular-carousel__track {
  display: flex;
  gap: var(--slide-gap);
  transform: translateX(calc(var(--slide-index) * -1 * ((100% - (var(--per-view) - 1) * var(--slide-gap)) / var(--per-view) + var(--slide-gap))));
}

@media (prefers-reduced-motion: no-preference) {
  .popular-carousel__track {
    transition: transform 600ms ease-in-out;
  }
}

.popular-carousel__slide {
  flex: 0 0 calc((100% - (var(--per-view) - 1) * var(--slide-gap)) / var(--per-view));
  min-width: 0;
  display: flex;
}

/* 크게 — 카드가 칸을 채우고 사진은 칸 폭에 맞춘 비율로 */
.popular-carousel__slide :deep(.course-card) {
  flex: 1 1 auto;
  width: auto;
  min-width: 0;
}

.popular-carousel__slide :deep(.course-card__thumb) {
  height: auto;
  aspect-ratio: 16 / 10;
}

.popular-carousel__slide :deep(.course-card__title) {
  font-size: 20px;
  line-height: 1.4;
}

/* 폰: 한 칸이 화면을 다 채워 사진을 조금 낮게 */
@media (max-width: 599px) {
  .popular-carousel__slide :deep(.course-card__thumb) {
    aspect-ratio: 16 / 9;
  }
}

.popular-carousel__controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
}

.popular-carousel__btn {
  width: 44px;
  height: 44px;
}

.popular-carousel__count {
  min-width: 56px;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: center;
  color: var(--db-text-body);
}

.popular-carousel__toggle {
  min-height: 44px;
  font-size: 15px;
}
</style>
