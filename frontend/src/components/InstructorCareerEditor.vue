<template>
  <!-- 주요 경력 입력 — 큰 글 칸 하나에 '현)·전)으로 시작하면…' 규칙을 알아서 지켜 주길 바랄 수 없었다.
       줄마다 [구분] + [내용] 칸을 주고, 저장은 지금처럼 career 글 하나('현) …' 줄 묶음)로 한다(서버 그대로) -->
  <fieldset class="career-editor">
    <legend class="career-editor__legend">주요 경력</legend>
    <div v-for="(row, index) in rows" :key="row.id" class="career-editor__row">
      <q-select
        :model-value="row.tag"
        outlined
        dense
        emit-value
        map-options
        :options="TAG_OPTIONS"
        :aria-label="`${index + 1}번째 경력 구분`"
        class="career-editor__tag"
        @update:model-value="updateRow(index, { tag: $event })"
      />
      <q-input
        :model-value="row.text"
        outlined
        dense
        :maxlength="ROW_MAX"
        :placeholder="index === 0 ? '예: ○○기관 평생교육 강사' : '경력 내용'"
        :aria-label="`${index + 1}번째 경력 내용`"
        class="career-editor__text"
        @update:model-value="updateRow(index, { text: $event || '' })"
      />
      <q-btn
        flat
        round
        dense
        icon="close"
        color="grey-7"
        :aria-label="`${index + 1}번째 경력 지우기`"
        :disable="rows.length === 1 && !row.text"
        @click="removeRow(index)"
      />
    </div>
    <div class="career-editor__foot">
      <q-btn
        outline
        no-caps
        color="primary"
        icon="add"
        label="경력 추가"
        :disable="rows.length >= ROWS_MAX"
        @click="addRow"
      />
      <span class="career-editor__count" :class="{ 'career-editor__count--over': overLimit }">
        {{ serialized.length }} / {{ TOTAL_MAX }}자
      </span>
    </div>
    <div v-if="overLimit" class="career-editor__error" role="alert">
      경력이 너무 깁니다. {{ TOTAL_MAX }}자 안으로 줄여 주세요.
    </div>
  </fieldset>
</template>

<script>
import { parseCareer } from '@/components/InstructorCareerList.vue';

// 서버 UserUpdateRequest.career maxLength
const TOTAL_MAX = 2000;
const ROW_MAX = 200;
const ROWS_MAX = 20;
const TAG_OPTIONS = [
  { label: '현재', value: 'current' },
  { label: '이전', value: 'past' },
  { label: '기타', value: '' },
];
const TAG_PREFIX = { current: '현) ', past: '전) ', '': '' };

let rowSeq = 0;
const newRow = (tag = 'current', text = '') => ({ id: `career-${(rowSeq += 1)}`, tag, text });

/** 행 → career 글('현) …' 줄 묶음). 빈 행은 뺀다 */
function serializeRows(rows) {
  return rows
    .map((row) => ({ ...row, text: row.text.trim() }))
    .filter((row) => row.text)
    .map((row) => `${TAG_PREFIX[row.tag] ?? ''}${row.text}`)
    .join('\n');
}

function rowsFrom(value) {
  const parsed = parseCareer(value).map((item) => newRow(item.tag, item.text));
  return parsed.length ? parsed : [newRow()];
}

export default {
  name: 'InstructorCareerEditor',
  props: {
    modelValue: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  data() {
    return { rows: rowsFrom(this.modelValue), TAG_OPTIONS, TOTAL_MAX, ROW_MAX, ROWS_MAX };
  },
  computed: {
    serialized() {
      return serializeRows(this.rows);
    },
    overLimit() {
      return this.serialized.length > TOTAL_MAX;
    },
  },
  watch: {
    // 밖에서 값이 바뀌면(수정 취소·다시 열기) 행을 다시 만든다 — 내가 보낸 값이면 그대로 둔다
    modelValue(value) {
      if (value !== this.serialized) this.rows = rowsFrom(value);
    },
  },
  methods: {
    emitValue(rows) {
      this.rows = rows;
      this.$emit('update:modelValue', serializeRows(rows));
    },
    updateRow(index, patch) {
      this.emitValue(this.rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
    },
    addRow() {
      this.emitValue([...this.rows, newRow('current')]);
      this.$nextTick(() => {
        const inputs = this.$el.querySelectorAll('.career-editor__text input');
        inputs[inputs.length - 1]?.focus();
      });
    },
    removeRow(index) {
      const rest = this.rows.filter((_, i) => i !== index);
      this.emitValue(rest.length ? rest : [newRow()]);
    },
  },
};
</script>

<style scoped>
.career-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  border: 0;
}

.career-editor__legend {
  margin-bottom: 4px;
  padding: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.career-editor__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.career-editor__tag {
  flex: 0 0 104px;
}

.career-editor__text {
  flex: 1;
  min-width: 0;
}

.career-editor__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.career-editor__count {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.career-editor__count--over,
.career-editor__error {
  color: var(--db-error);
}

.career-editor__error {
  font-size: 13px;
}

@media (max-width: 599px) {
  .career-editor__tag {
    flex-basis: 88px;
  }
}
</style>
