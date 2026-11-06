<template>
  <!-- 단계 배지 기준표 편집 — 단계별 이름·이미지·최소 이수 건수. 발급된 배지가 있으면(locked) 이름·이미지만 바꾼다 -->
  <div class="tier-editor">
    <div class="tier-editor__scroll">
      <table class="tier-table">
        <thead>
          <tr>
            <th scope="col" class="tier-table__level">단계</th>
            <th scope="col" class="tier-table__image">이미지</th>
            <th scope="col">이름</th>
            <th scope="col" class="tier-table__min">최소 이수</th>
            <th scope="col" class="tier-table__range">범위</th>
            <th v-if="!locked" scope="col" class="tier-table__del"><span class="sr-only">삭제</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in levels" :key="index">
            <td class="tier-table__level">{{ index + 1 }}</td>
            <td class="tier-table__image">
              <button
                type="button"
                class="tier-image"
                :disabled="uploadingIndex !== null"
                :aria-label="`${index + 1}단계 이미지 ${item.imageFileId ? '바꾸기' : '올리기'}`"
                @click="pickImage(index)"
              >
                <q-spinner v-if="uploadingIndex === index" color="primary" size="20px" />
                <BadgeMedal v-else :image-url="item.imageUrl || ''" :image-file-id="item.imageFileId || ''" :size="36" />
              </button>
              <button
                v-if="item.imageFileId"
                type="button"
                class="tier-image__clear"
                :aria-label="`${index + 1}단계 이미지 빼기`"
                @click="update(index, { imageFileId: null, imageUrl: '' })"
              >
                그림 빼기
              </button>
            </td>
            <td>
              <q-input
                :model-value="item.name"
                outlined
                dense
                maxlength="20"
                :aria-label="`${index + 1}단계 이름`"
                @update:model-value="update(index, { name: $event })"
              />
            </td>
            <td class="tier-table__min">
              <q-input
                :model-value="item.minCount"
                outlined
                dense
                type="number"
                min="1"
                max="1000"
                suffix="건"
                :readonly="locked"
                :aria-label="`${index + 1}단계 최소 이수 건수`"
                @update:model-value="update(index, { minCount: $event === '' ? '' : Number($event) })"
              />
            </td>
            <td class="tier-table__range">{{ tierRangeText(levels, index) }}</td>
            <td v-if="!locked" class="tier-table__del">
              <q-btn
                flat
                round
                dense
                icon="close"
                size="sm"
                :disable="levels.length <= TIER_LEVEL_MIN"
                :aria-label="`${index + 1}단계 삭제`"
                @click="remove(index)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <input ref="fileInput" type="file" accept="image/*" class="tier-editor__file" @change="onFile" />
    <div class="tier-editor__foot">
      <q-btn
        v-if="!locked"
        flat
        no-caps
        dense
        color="primary"
        icon="add"
        label="단계 추가"
        :disable="levels.length >= TIER_LEVEL_MAX"
        @click="add"
      />
      <!-- 잠겼을 때의 안내는 창 위(획득 조건)에 이미 있다 — 두 번 보였다 -->
      <span v-if="!locked" class="tier-editor__hint">
        {{ `${TIER_LEVEL_MIN}~${TIER_LEVEL_MAX}단계, 최소 이수 건수는 위 단계보다 커야 합니다. 이미지가 없으면 배지 대표 이미지를 씁니다.` }}
      </span>
    </div>
  </div>
</template>

<script>
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import { BADGE_IMAGE_PURPOSE } from '@/services/badge-api';
import { uploadFile } from '@/services/file-api';
import { TIER_LEVEL_MAX, TIER_LEVEL_MIN, tierRangeText } from '@/utils/badge-rule';

export default {
  name: 'BadgeTierLevelsEditor',
  components: { BadgeMedal },
  props: {
    /** [{ name, minCount, imageFileId, imageUrl }] — level 은 순서로 정한다 */
    levels: { type: Array, required: true },
    locked: { type: Boolean, default: false },
  },
  emits: ['update:levels', 'uploading'],
  data() {
    // localUrls: 저장 전 미리보기로 만든 blob URL — 창이 닫히면 놓아 준다
    return { TIER_LEVEL_MIN, TIER_LEVEL_MAX, uploadingIndex: null, pendingIndex: null, localUrls: [] };
  },
  beforeUnmount() {
    this.localUrls.forEach((url) => URL.revokeObjectURL(url));
  },
  methods: {
    tierRangeText,
    update(index, patch) {
      this.$emit(
        'update:levels',
        this.levels.map((item, i) => (i === index ? { ...item, ...patch } : item)),
      );
    },
    add() {
      const last = this.levels[this.levels.length - 1];
      const next = { name: '', minCount: last ? Number(last.minCount || 0) + 5 : 1, imageFileId: null, imageUrl: '' };
      this.$emit('update:levels', [...this.levels, next]);
    },
    remove(index) {
      this.$emit('update:levels', this.levels.filter((_, i) => i !== index));
    },
    pickImage(index) {
      this.pendingIndex = index;
      this.$refs.fileInput.click();
    },
    async onFile(event) {
      const file = event?.target?.files?.[0];
      if (event?.target) event.target.value = '';
      const index = this.pendingIndex;
      if (!file || index === null) return;
      if (!file.type.startsWith('image/')) {
        this.$q.notify({ type: 'warning', message: '이미지 파일만 올릴 수 있습니다.', position: 'top' });
        return;
      }
      this.uploadingIndex = index;
      this.$emit('uploading', true);
      try {
        const stored = await uploadFile(file, BADGE_IMAGE_PURPOSE);
        // 저장 전 파일은 올린 사람 인증이 있어야 열려 <img> 로는 403 — 고른 파일을 바로 보인다.
        // imageUrl 은 화면 전용(저장 요청엔 imageFileId 만 간다)
        const localUrl = URL.createObjectURL(file);
        this.localUrls = [...this.localUrls, localUrl];
        this.update(index, { imageFileId: stored.id, imageUrl: localUrl });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '이미지를 올리지 못했습니다.', position: 'top' });
      } finally {
        this.uploadingIndex = null;
        this.pendingIndex = null;
        this.$emit('uploading', false);
      }
    },
  },
};
</script>

<style scoped>
.tier-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tier-editor__scroll {
  overflow-x: auto;
}

.tier-table {
  width: 100%;
  min-width: 460px;
  border-collapse: collapse;
  font-size: 13px;
}

.tier-table th {
  padding: 6px 6px;
  border-bottom: 1px solid var(--db-border);
  font-weight: 600;
  text-align: left;
  color: var(--db-text-sub);
  white-space: nowrap;
}

.tier-table td {
  padding: 6px;
  border-bottom: 1px solid var(--db-border-light);
  vertical-align: middle;
}

.tier-table .tier-table__level {
  width: 40px;
  text-align: center;
  font-weight: 700;
  color: var(--db-text-body);
}

.tier-table__image {
  width: 104px;
  white-space: nowrap;
}

.tier-table__min {
  width: 104px;
}

.tier-table__range {
  width: 72px;
  color: var(--db-text-sub);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.tier-table__del {
  width: 36px;
}

.tier-image {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px dashed var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  cursor: pointer;
  vertical-align: middle;
}

.tier-image:hover {
  border-color: var(--db-primary);
}

.tier-image:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.tier-image:disabled {
  cursor: default;
  opacity: 0.6;
}

.tier-image__clear {
  margin-left: 4px;
  padding: 0;
  border: 0;
  background: none;
  font-size: 12px;
  color: var(--db-text-sub);
  text-decoration: underline;
  cursor: pointer;
}

.tier-editor__file {
  display: none;
}

.tier-editor__foot {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tier-editor__hint {
  font-size: 12px;
  line-height: 17px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

/* 폰: 표가 창 폭을 넘어 최소 이수·범위가 밖에 있었다 — 한 단계를 두 줄로(위: 단계·그림·이름, 아래: 최소 이수·범위) */
@media (max-width: 599px) {
  .tier-table,
  .tier-table tbody {
    display: block;
    min-width: 0;
  }

  .tier-table thead {
    display: none;
  }

  .tier-table tr {
    display: grid;
    grid-template-columns: 24px 64px 104px minmax(0, 1fr) auto;
    grid-template-areas:
      'lv img name name del'
      'lv img min range del';
    align-items: center;
    gap: 6px;
    padding: 8px 0;
    border-bottom: 1px solid var(--db-border-light);
  }

  .tier-table td {
    width: auto;
    padding: 0;
    border-bottom: 0;
  }

  .tier-table td.tier-table__level {
    grid-area: lv;
    width: auto;
  }

  .tier-table td.tier-table__image {
    grid-area: img;
    white-space: normal;
  }

  .tier-table td:nth-child(3) {
    grid-area: name;
  }

  .tier-table td.tier-table__min {
    grid-area: min;
  }

  .tier-table td.tier-table__range {
    grid-area: range;
  }

  .tier-table td.tier-table__del {
    grid-area: del;
  }

  .tier-image__clear {
    display: block;
    margin: 4px 0 0;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
