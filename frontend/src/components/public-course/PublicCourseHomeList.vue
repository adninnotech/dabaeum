<template>
  <!-- 메인 '대구 다른 기관 강좌' — 접수 상태순(접수 중 → 예정 → 모름). 머리(제목·더보기)는 IndexPage 가 그린다.
       위 섹션(인기·추천)과 같은 4칸 한 줄 — 3칸 두 줄(6개)이 홈에서 가장 큰 덩어리라 혼자 튀었다 -->
  <div v-if="loading" class="pc-home pc-home--loading" aria-busy="true">
    <div v-for="n in size" :key="n" class="pc-home__skeleton" />
  </div>
  <div v-else class="pc-home">
    <PublicCourseCard v-for="course in rows" :key="course.id" :course="course" />
  </div>
</template>

<script>
import PublicCourseCard from '@/components/public-course/PublicCourseCard.vue';
import { listPublicCourses } from '@/services/public-course-api';
import { publicReceiptState } from '@/utils/public-course';

const FETCH_SPARE = 2;

export default {
  name: 'PublicCourseHomeList',
  components: { PublicCourseCard },
  props: {
    size: { type: Number, default: 4 },
  },
  /** count: 불러온 수(실패하면 0) — 0이면 IndexPage 가 블록을 통째로 숨긴다 */
  emits: ['update:count'],
  data() {
    return { rows: [], loading: true };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        // receiptStatus: 접수 중(마감 임박 순) → 접수 예정 → 접수 기간 모름 → 마감 — 서버가 한 번에 정렬한다.
        // 접수 중이 적어도(2026-10 기준 5건) 남은 칸은 예정·모름으로 차고, 마감은 맨 뒤라 6칸 안엔 거의 없지만 걸러 둔다
        // 마감을 거르고도 칸이 차게 조금 넉넉히 받는다
        const result = await listPublicCourses({ page: 0, size: this.size + FETCH_SPARE, sort: 'receiptStatus,asc' });
        this.rows = result.data.filter((row) => publicReceiptState(row).tone !== 'closed').slice(0, this.size);
      } catch (error) {
        // 메인의 보조 블록이라 오류 배너 대신 숨긴다 — 원인은 콘솔에 남긴다
        console.warn('[PublicCourseHomeList] 대구 다른 기관 강좌를 불러오지 못했습니다', error);
        this.rows = [];
      } finally {
        this.loading = false;
        this.$emit('update:count', this.rows.length);
      }
    },
  },
};
</script>

<style scoped>
.pc-home {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.pc-home__skeleton {
  height: 188px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

@media (max-width: 1023px) {
  .pc-home {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 599px) {
  .pc-home {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }
}
</style>
