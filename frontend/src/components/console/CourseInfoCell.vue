<template>
  <div class="cell-course">
    <!-- 80×60 사진 위에 수업 방식 배지를 얹으니 사진이 거의 다 가려졌다 — 배지는 아래 정보 줄 맨 앞으로 -->
    <div class="cell-course__thumb">
      <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" />
    </div>
    <div class="cell-course__text">
      <RouterLink :to="to" class="cell-title">{{ row.title }}</RouterLink>
      <div class="cell-note cell-note--sub">
        <StatusBadge v-if="row.educationType" size="sm" class="cell-course__badge" :status="row.educationType" />
        <span>{{ row.institutionName || '-' }}</span>
        <i class="cell-sep" />
        <span>{{ row.instructorName ? `${row.instructorName} 강사` : '강사 미배정' }}</span>
        <i class="cell-sep" />
        <span :title="classificationTitle">{{ classificationName }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import { classificationLeafName, classificationPathText } from '@/utils/classification';

export default {
  name: 'CourseInfoCell',
  components: { StatusBadge },
  props: {
    row: { type: Object, required: true },
    to: { type: String, required: true },
  },
  computed: {
    // 행은 toCourseCard 결과(classificationName)이거나 원본 Course(classificationPath)다
    classificationName() {
      return this.row.classificationName || classificationLeafName(this.row.classificationPath, '미분류');
    },
    classificationTitle() {
      return classificationPathText(this.row.classificationPath, '');
    },
  },
};
</script>

<style scoped>
.cell-course__thumb {
  position: relative;
  width: 80px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 6px;
  background: var(--db-border-avatar);
  overflow: hidden;
}

.cell-course__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cell-course__badge {
  flex-shrink: 0;
  margin-right: 2px;
}

.cell-course__text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.cell-title {
  text-decoration: none;
}

.cell-title:hover {
  text-decoration: underline;
}

.cell-note {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

/* 좁은 칸·폰 카드에서 기관·강사·분류가 '…' 없이 잘려 나갔다 — 조각마다 줄임표로 */
.cell-note span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell-title {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell-sep {
  width: 1px;
  height: 10px;
  background: var(--db-border);
}
</style>
