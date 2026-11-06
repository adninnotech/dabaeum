<template>
  <!-- 회차 순서 바꾸기 — PUT /courses/{id}/sessions/order 를 부르는 화면이 없었다(QA CRS-10).
       위·아래로 옮긴 뒤 저장하면 서버가 그 순서대로 회차 번호를 다시 매긴다. 회차 전부를 한 번에 보낸다 -->
  <q-dialog :model-value="modelValue" :persistent="saving" @update:model-value="close" @before-show="reset">
    <q-card class="session-order">
      <q-card-section class="session-order__head">
        <div class="text-h6">회차 순서 바꾸기</div>
        <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
      </q-card-section>
      <q-separator />

      <q-card-section class="session-order__body">
        <p class="session-order__help">저장하면 이 순서대로 1회차부터 번호가 다시 매겨집니다. 일정은 바뀌지 않습니다.</p>
        <ol class="session-order__list">
          <li v-for="(row, index) in ordered" :key="row.id" class="session-order__item">
            <span class="session-order__no num">{{ index + 1 }}</span>
            <span class="session-order__text">
              <span class="session-order__topic">{{ row.topic || `${row.sessionNo}회차` }}</span>
              <span class="session-order__meta">{{ scheduleText(row) }}</span>
            </span>
            <q-btn
              flat
              round
              dense
              icon="arrow_upward"
              :disable="index === 0 || saving"
              :aria-label="`${index + 1}번째 회차를 위로`"
              @click="move(index, -1)"
            />
            <q-btn
              flat
              round
              dense
              icon="arrow_downward"
              :disable="index === ordered.length - 1 || saving"
              :aria-label="`${index + 1}번째 회차를 아래로`"
              @click="move(index, 1)"
            />
          </li>
        </ol>
        <p v-if="error" class="session-order__error" role="alert">{{ error }}</p>
      </q-card-section>

      <q-card-actions align="right" class="session-order__actions">
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn unelevated no-caps color="primary" label="순서 저장" :disable="!changed" :loading="saving" @click="submit" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { reorderCourseSessions } from '@/services/session-api';
import { formatSessionScheduleKst } from '@/utils/datetime';

export default {
  name: 'SessionReorderDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    courseId: { type: String, required: true },
    /** 회차 전부(번호순) */
    sessions: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue', 'saved'],
  data() {
    return { ordered: [], error: '', saving: false };
  },
  computed: {
    changed() {
      return this.ordered.some((row, index) => row.id !== this.sessions[index]?.id);
    },
  },
  methods: {
    scheduleText(row) {
      return row.startsAt ? formatSessionScheduleKst(row.startsAt, row.endsAt) : '일정 없음';
    },
    close(value) {
      if (!this.saving) this.$emit('update:modelValue', value);
    },
    reset() {
      this.ordered = [...this.sessions];
      this.error = '';
    },
    move(index, delta) {
      const target = index + delta;
      if (target < 0 || target >= this.ordered.length) return;
      const next = [...this.ordered];
      [next[index], next[target]] = [next[target], next[index]];
      this.ordered = next;
    },
    async submit() {
      this.saving = true;
      this.error = '';
      try {
        await reorderCourseSessions(
          this.courseId,
          this.ordered.map((row) => row.id),
        );
        this.$q.notify({ type: 'positive', message: '회차 순서를 저장했습니다.', position: 'top' });
        this.saving = false;
        this.$emit('update:modelValue', false);
        this.$emit('saved');
      } catch (error) {
        this.error = error.message || '순서를 저장하지 못했습니다.';
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.session-order {
  width: 520px;
  max-width: 94vw;
}

.session-order__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-order__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 60vh;
  overflow-y: auto;
}

.session-order__help {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.session-order__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.session-order__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.session-order__no {
  width: 24px;
  font-weight: 700;
  text-align: center;
  color: var(--db-text-body);
}

.session-order__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.session-order__topic {
  overflow-wrap: anywhere;
  color: var(--db-text-body);
}

.session-order__meta {
  font-size: 12px;
  color: var(--db-text-sub);
}

.session-order__error {
  margin: 0;
  font-size: 13px;
  color: var(--db-status-pink);
}

.session-order__actions {
  padding: 8px 16px 16px;
}
</style>
