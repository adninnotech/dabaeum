<template>
  <div class="cls-picker">
    <div class="cls-picker__row" :class="{ 'cls-picker__row--single': level1Leaf }">
      <q-select
        :model-value="level1"
        outlined
        dense
        emit-value
        map-options
        :clearable="!leafOnly"
        :options="level1Options"
        :loading="loading"
        :disable="disable || !!loadError"
        label="대분류"
        class="cls-picker__select"
        @update:model-value="pickLevel1"
      />
      <q-select
        v-if="!level1Leaf"
        :model-value="level2"
        outlined
        dense
        emit-value
        map-options
        :clearable="!leafOnly"
        :options="level2Options"
        :disable="disable || !level1"
        label="중분류"
        class="cls-picker__select"
        @update:model-value="pickLevel2"
      />
      <q-select
        v-if="!level1Leaf"
        :model-value="level3"
        outlined
        dense
        emit-value
        map-options
        :clearable="!leafOnly"
        :options="level3Options"
        :disable="disable || !level2"
        label="소분류"
        class="cls-picker__select"
        @update:model-value="pickLevel3"
      />
    </div>

    <p v-if="loadError" class="cls-picker__msg cls-picker__msg--error" role="alert">
      강좌 분류를 불러오지 못했습니다.
      <button type="button" class="cls-picker__retry" @click="load">다시 시도</button>
    </p>
    <!-- 저장된 코드가 사용 중 트리에 없다 = 관리자가 사용 중지한 분류. 게시하려면 다시 골라야 한다 -->
    <p v-else-if="orphanLabel" class="cls-picker__msg cls-picker__msg--error" role="alert">
      지금 쓸 수 없는 분류입니다({{ orphanLabel }}). 다시 선택해 주세요.
    </p>
    <p v-else-if="leafOnly && level1 && !modelValue" class="cls-picker__msg">
      소분류까지 골라야 강의에 붙습니다.
    </p>
  </div>
</template>

<script>
import { listCourseClassifications } from '@/services/classification-api';
import {
  classificationOptions,
  classificationPathText,
  findClassificationTrail,
  isLeafClassification,
} from '@/utils/classification';

/* 표준 강좌 분류 선택(대→중→소). 피그마 B-9 평면 9개를 대체한다.
 * leafOnly(기본): 강좌 등록·수정 — 하위 없는 코드만 값이 된다. 중간 단계면 null 을 올린다.
 * leafOnly=false: 목록 필터 — 어느 단계든 값이 된다(서버가 하위 포함으로 거른다) */
export default {
  name: 'ClassificationPicker',
  props: {
    /** 분류 코드(TSIX_AABBCC) 또는 null */
    modelValue: { type: String, default: null },
    leafOnly: { type: Boolean, default: true },
    disable: { type: Boolean, default: false },
    /** 저장된 강좌의 classificationPath — 사용 중지된 코드일 때 이름을 보여 주는 데만 쓴다 */
    currentPath: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      tree: [],
      loading: false,
      loadError: false,
      level1: null,
      level2: null,
      level3: null,
    };
  },
  computed: {
    level1Node() {
      return this.tree.find((node) => node.code === this.level1) || null;
    },
    level2Node() {
      return this.level1Node?.children?.find((node) => node.code === this.level2) || null;
    },
    level1Leaf() {
      return !!this.level1Node && isLeafClassification(this.level1Node);
    },
    level1Options() {
      return classificationOptions(this.tree);
    },
    level2Options() {
      return classificationOptions(this.level1Node?.children);
    },
    level3Options() {
      return classificationOptions(this.level2Node?.children);
    },
    orphanLabel() {
      if (!this.modelValue || this.loading || !this.tree.length) return '';
      if (findClassificationTrail(this.tree, this.modelValue).length) return '';
      return classificationPathText(this.currentPath, this.modelValue);
    },
  },
  watch: {
    modelValue() {
      this.syncFromValue();
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.loadError = false;
      try {
        this.tree = await listCourseClassifications();
        this.syncFromValue();
      } catch {
        this.loadError = true;
      } finally {
        this.loading = false;
      }
    },
    // 바깥 값 → 세 칸. 중간 단계를 고르는 중(leafOnly 에서 값이 null)이면 칸을 비우지 않는다
    syncFromValue() {
      if (!this.modelValue) {
        if (!this.leafOnly) this.setLevels([]);
        return;
      }
      const trail = findClassificationTrail(this.tree, this.modelValue);
      if (trail.length) this.setLevels(trail.map((node) => node.code));
    },
    setLevels([first = null, second = null, third = null]) {
      this.level1 = first;
      this.level2 = second;
      this.level3 = third;
    },
    emitFor(node) {
      if (!node) {
        this.$emit('update:modelValue', null);
        return;
      }
      const usable = !this.leafOnly || isLeafClassification(node);
      this.$emit('update:modelValue', usable ? node.code : null);
    },
    pickLevel1(code) {
      this.setLevels([code || null]);
      this.emitFor(this.tree.find((node) => node.code === code) || null);
    },
    pickLevel2(code) {
      this.setLevels([this.level1, code || null]);
      const node = this.level1Node?.children?.find((item) => item.code === code) || null;
      // 필터에서 중분류를 지우면 대분류로 거른다
      this.emitFor(node || (this.leafOnly ? null : this.level1Node));
    },
    pickLevel3(code) {
      this.setLevels([this.level1, this.level2, code || null]);
      const node = this.level2Node?.children?.find((item) => item.code === code) || null;
      this.emitFor(node || (this.leafOnly ? null : this.level2Node));
    },
  },
};
</script>

<style scoped>
.cls-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.cls-picker__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.cls-picker__row--single {
  grid-template-columns: minmax(0, 1fr);
}

.cls-picker__select {
  min-width: 0;
}

.cls-picker__msg {
  margin: 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.cls-picker__msg--error {
  color: var(--db-status-pink);
}

.cls-picker__retry {
  padding: 0;
  margin-left: 4px;
  border: 0;
  background: none;
  font: inherit;
  font-weight: 700;
  color: var(--db-accent);
  text-decoration: underline;
  cursor: pointer;
}

.cls-picker__retry:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

@media (max-width: 599px) {
  .cls-picker__row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
