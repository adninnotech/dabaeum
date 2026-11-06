<template>
  <CourseDetailSection tab="학습목차" title="학습목차">
    <AppEmptyState v-if="!sessions.length" compact description="등록된 회차가 없습니다." />
    <table v-else class="detail-table">
      <thead>
        <tr>
          <th class="detail-table__no">회차</th>
          <th>{{ isOnline ? '강의 영상' : '날짜 · 시간' }}</th>
          <!-- 주제가 하나도 없으면 칸이 전부 '-' 였다 — 칸을 숨긴다 -->
          <th v-if="hasTopics">주제</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in sessions" :key="item.id">
          <td class="detail-table__no">{{ item.sessionNo }}회차</td>
          <!-- TODO(backend): 회차에 강의 영상 필드가 없음 -->
          <td v-if="isOnline">-</td>
          <td v-else>{{ formatSessionScheduleKst(item.startsAt, item.endsAt) }}</td>
          <td v-if="hasTopics">{{ item.topic || '-' }}</td>
        </tr>
      </tbody>
    </table>
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';
import { formatSessionScheduleKst } from '@/utils/datetime';

export default {
  name: 'CourseSessionsSection',
  components: { AppEmptyState, CourseDetailSection },
  props: {
    sessions: { type: Array, default: () => [] },
    isOnline: { type: Boolean, default: false },
  },
  computed: {
    hasTopics() {
      return this.sessions.some((item) => item.topic);
    },
  },
  methods: { formatSessionScheduleKst },
};
</script>

<style scoped>
/* 학습목차 */
.detail-table {
  width: 100%;
  border-collapse: collapse;
}

.detail-table th,
.detail-table td {
  padding: 10px;
  border-bottom: 1px solid var(--db-border);
  text-align: left;
  font-size: 14px;
}

.detail-table th {
  background: var(--db-bg-table-head);
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-table__no {
  width: 80px;
}
</style>
