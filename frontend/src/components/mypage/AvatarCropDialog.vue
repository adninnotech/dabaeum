<template>
  <!-- 프로필 사진 자르기 — 고른 사진 위에 원을 띄우고, 끌어서 옮기고 확대해 [저장하기]를 누르면 원 안만 남긴다.
       예전엔 사진을 그대로 올려 길쭉한 사진이 동그라미 안에서 찌그러졌다.
       올리는 동안엔 창을 닫지 않는다(바로 닫으면 끝났는지 몰라 다시 눌렀다). 실패하면 창 안에 알리고 다시 누르게 -->
  <q-dialog
    :model-value="modelValue"
    no-backdrop-dismiss
    :persistent="saving"
    @update:model-value="$emit('update:modelValue', $event)"
    @show="onShow"
    @hide="reset"
  >
    <q-card class="avatar-crop" aria-labelledby="avatar-crop-title">
      <q-card-section class="avatar-crop__head">
        <h2 id="avatar-crop-title" class="avatar-crop__title">프로필 사진 자르기</h2>
        <p id="avatar-crop-hint" class="avatar-crop__hint">
          사진을 끌어 옮기고, 아래 [−] [+] 로 크기를 맞추세요. 원 안만 저장됩니다.
        </p>
      </q-card-section>

      <q-card-section class="avatar-crop__body">
        <div
          class="avatar-crop__stage"
          :class="{ 'avatar-crop__stage--ready': image, 'avatar-crop__stage--dragging': drag }"
          :style="{ width: `${stage}px`, height: `${stage}px` }"
          tabindex="0"
          role="group"
          aria-label="자를 위치"
          aria-describedby="avatar-crop-hint avatar-crop-keys"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerUp"
          @wheel.prevent="onWheel"
          @keydown="onKeydown"
        >
          <img v-if="image" :src="imageUrl" alt="" class="avatar-crop__image" :style="placeImage(stage, circle)" draggable="false" />
          <q-spinner v-else size="32px" class="avatar-crop__loading" />
          <div class="avatar-crop__circle" :style="{ width: `${circle}px`, height: `${circle}px` }" aria-hidden="true" />
        </div>
        <span id="avatar-crop-keys" class="avatar-crop__sr">화살표 키로 옮기고 + - 키로 확대·축소합니다.</span>

        <div class="avatar-crop__zoom">
          <q-btn flat round icon="remove" aria-label="작게" class="avatar-crop__zoom-btn" :disable="!image || zoom <= 1" @click="setZoom(zoom - BUTTON_ZOOM_STEP)" />
          <q-slider
            :model-value="zoom"
            :min="1"
            :max="maxZoom"
            :step="0.01"
            :disable="!image"
            aria-label="사진 크기"
            class="avatar-crop__slider"
            @update:model-value="setZoom"
          />
          <q-btn flat round icon="add" aria-label="크게" class="avatar-crop__zoom-btn" :disable="!image || zoom >= maxZoom" @click="setZoom(zoom + BUTTON_ZOOM_STEP)" />
        </div>

        <!-- 실제 아바타 크기로 미리 보기 — 큰 무대에선 괜찮아 보여도 머리의 작은 동그라미에선 얼굴이 잘릴 수 있다 -->
        <div class="avatar-crop__preview" aria-hidden="true">
          <span class="avatar-crop__preview-label">미리 보기</span>
          <span v-for="size in PREVIEW_SIZES" :key="size" class="avatar-crop__preview-circle" :style="{ width: `${size}px`, height: `${size}px` }">
            <img v-if="image" :src="imageUrl" alt="" class="avatar-crop__image" :style="placeImage(size, size)" />
          </span>
        </div>

        <p v-if="errorMessage" class="avatar-crop__error" role="alert">{{ errorMessage }}</p>
      </q-card-section>

      <q-card-actions class="avatar-crop__actions">
        <!-- 320 폭에선 세 버튼이 한 줄에 안 들어가 글자를 접는다(두 줄로 꺾이지 않게) -->
        <q-btn
          flat
          no-caps
          icon="restart_alt"
          :label="$q.screen.xs ? undefined : '처음 위치로'"
          aria-label="처음 위치로"
          :disable="!image || saving || isInitial"
          @click="resetPlacement"
        />
        <q-space />
        <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
        <q-btn unelevated no-caps color="primary" label="저장하기" :loading="saving" :disable="!image" @click="save" />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import {
  AVATAR_MAX_ZOOM,
  clampOffset,
  coverScale,
  renderCircleAvatar,
  sourceSquare,
} from '@/utils/avatar-crop';

const STAGE_MAX = 320;
const STAGE_SIDE_GAP = 80; // 폰에서 무대가 카드 밖으로 나가지 않게 남기는 좌우 여백 합
const CIRCLE_INSET = 24; // 원 밖으로 사진이 조금 보여야 어디를 자르는지 안다
const KEY_STEP = 10;
const KEY_ZOOM_STEP = 0.1;
const BUTTON_ZOOM_STEP = 0.25;
const WHEEL_ZOOM_STEP = 0.0015;
const PREVIEW_SIZES = [72, 40]; // 마이페이지·머리 아바타 크기 즈음

export default {
  name: 'AvatarCropDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    /** 고른 원본 사진 파일 */
    file: { type: [File, Blob], default: null },
    /** 자른 PNG(File)를 받아 올리는 함수 — 끝날 때까지 창을 열어 두고, 던지면 창 안에 알린다 */
    upload: { type: Function, required: true },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      imageUrl: '',
      image: null,
      stage: STAGE_MAX,
      zoom: 1,
      offset: { x: 0, y: 0 },
      drag: null,
      saving: false,
      errorMessage: '',
      maxZoom: AVATAR_MAX_ZOOM,
      BUTTON_ZOOM_STEP,
      PREVIEW_SIZES,
    };
  },
  computed: {
    circle() {
      return this.stage - CIRCLE_INSET * 2;
    },
    scale() {
      return this.image ? coverScale(this.image, this.circle) * this.zoom : 1;
    },
    isInitial() {
      return this.zoom === 1 && this.offset.x === 0 && this.offset.y === 0;
    },
  },
  beforeUnmount() {
    this.reset();
  },
  methods: {
    onShow() {
      this.load();
      window.addEventListener('resize', this.fitStage);
    },
    load() {
      this.reset();
      if (!this.file) return;
      this.stage = this.stageSize();
      this.imageUrl = URL.createObjectURL(this.file);
      const image = new Image();
      image.onload = () => {
        this.image = { width: image.naturalWidth, height: image.naturalHeight, element: image };
      };
      image.onerror = () => {
        this.$q.notify({ type: 'negative', message: '사진을 열지 못했습니다. 다른 사진을 골라 주세요.' });
        this.$emit('update:modelValue', false);
      };
      image.src = this.imageUrl;
    },
    reset() {
      window.removeEventListener('resize', this.fitStage);
      if (this.imageUrl) URL.revokeObjectURL(this.imageUrl);
      this.imageUrl = '';
      this.image = null;
      this.zoom = 1;
      this.offset = { x: 0, y: 0 };
      this.drag = null;
      this.saving = false;
      this.errorMessage = '';
    },
    stageSize() {
      return Math.min(STAGE_MAX, window.innerWidth - STAGE_SIDE_GAP);
    },
    /** 폰을 돌리는 등 창 폭이 바뀌면 무대를 다시 맞추고, 보던 곳은 같은 비율로 옮긴다 */
    fitStage() {
      const next = this.stageSize();
      if (next === this.stage) return;
      const ratio = (next - CIRCLE_INSET * 2) / this.circle;
      this.stage = next;
      this.moveTo({ x: this.offset.x * ratio, y: this.offset.y * ratio });
    },
    /** 지름 circle 의 원을 가운데 둔 box 크기 판에서 사진 위치 — 무대와 미리 보기가 같은 계산을 쓴다 */
    placeImage(box, circle) {
      if (!this.image) return {};
      const ratio = circle / this.circle;
      const width = this.image.width * this.scale * ratio;
      const height = this.image.height * this.scale * ratio;
      const left = box / 2 + this.offset.x * ratio - width / 2;
      const top = box / 2 + this.offset.y * ratio - height / 2;
      return { width: `${width}px`, height: `${height}px`, transform: `translate(${left}px, ${top}px)` };
    },
    moveTo(offset) {
      if (!this.image) return;
      this.offset = clampOffset(offset, this.image, this.scale, this.circle);
    },
    /** 확대해도 보던 곳이 가운데에 남게 offset 을 같은 비율로 늘린다 */
    setZoom(value) {
      if (!this.image) return;
      const next = Math.min(this.maxZoom, Math.max(1, value));
      const ratio = next / this.zoom;
      this.zoom = next;
      this.moveTo({ x: this.offset.x * ratio, y: this.offset.y * ratio });
    },
    resetPlacement() {
      this.zoom = 1;
      this.offset = { x: 0, y: 0 };
    },
    onPointerDown(event) {
      if (!this.image || this.saving) return;
      event.currentTarget.setPointerCapture(event.pointerId);
      this.drag = { x: event.clientX, y: event.clientY, start: { ...this.offset } };
    },
    onPointerMove(event) {
      if (!this.drag) return;
      this.moveTo({
        x: this.drag.start.x + event.clientX - this.drag.x,
        y: this.drag.start.y + event.clientY - this.drag.y,
      });
    },
    onPointerUp() {
      this.drag = null;
    },
    onWheel(event) {
      this.setZoom(this.zoom - event.deltaY * WHEEL_ZOOM_STEP);
    },
    onKeydown(event) {
      const moves = {
        ArrowLeft: { x: KEY_STEP, y: 0 },
        ArrowRight: { x: -KEY_STEP, y: 0 },
        ArrowUp: { x: 0, y: KEY_STEP },
        ArrowDown: { x: 0, y: -KEY_STEP },
      };
      if (moves[event.key]) {
        event.preventDefault();
        this.moveTo({ x: this.offset.x + moves[event.key].x, y: this.offset.y + moves[event.key].y });
      } else if (event.key === '+' || event.key === '=') {
        event.preventDefault();
        this.setZoom(this.zoom + KEY_ZOOM_STEP);
      } else if (event.key === '-') {
        event.preventDefault();
        this.setZoom(this.zoom - KEY_ZOOM_STEP);
      }
    },
    async save() {
      if (!this.image || this.saving) return;
      this.saving = true;
      this.errorMessage = '';
      try {
        const square = sourceSquare(this.image, this.scale, this.offset, this.circle);
        const blob = await renderCircleAvatar(this.image.element, square);
        await this.upload(new File([blob], 'profile.png', { type: 'image/png' }));
        this.saving = false;
        this.$emit('update:modelValue', false);
      } catch (error) {
        this.errorMessage = `${error?.message || '사진을 저장하지 못했습니다.'} 잠시 뒤 다시 [저장하기]를 눌러 주세요.`;
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.avatar-crop {
  width: min(400px, calc(100vw - 32px));
  max-width: 100%;
}

.avatar-crop__head {
  padding-bottom: 8px;
}

.avatar-crop__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--db-text-title);
}

.avatar-crop__hint {
  margin: 4px 0 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--db-text-sub);
}

.avatar-crop__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.avatar-crop__stage {
  position: relative;
  overflow: hidden;
  border-radius: 8px;
  background: var(--db-crop-stage);
  color: var(--db-crop-ring);
  touch-action: none;
  user-select: none;
}

.avatar-crop__stage--ready {
  cursor: grab;
}

.avatar-crop__stage--dragging {
  cursor: grabbing;
}

.avatar-crop__stage:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.avatar-crop__image {
  position: absolute;
  top: 0;
  left: 0;
  max-width: none;
  pointer-events: none;
}

.avatar-crop__loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

/* 원 밖은 어둡게 — 큰 그림자로 원 둘레만 뚫는다 */
.avatar-crop__circle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  border: 2px solid var(--db-crop-ring);
  border-radius: 50%;
  box-shadow: 0 0 0 9999px var(--db-crop-scrim);
  pointer-events: none;
}

.avatar-crop__zoom {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

/* 중장년 이용자 — 누르는 자리 44px */
.avatar-crop__zoom-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  color: var(--db-text-body);
}

.avatar-crop__slider {
  flex: 1;
}

.avatar-crop__preview {
  display: flex;
  align-items: center;
  gap: 12px;
  align-self: stretch;
}

.avatar-crop__preview-label {
  margin-right: auto;
  font-size: 14px;
  color: var(--db-text-sub);
}

.avatar-crop__preview-circle {
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
  border-radius: 50%;
  border: 1px solid var(--db-border);
  background: var(--db-avatar-bg);
}

.avatar-crop__error {
  align-self: stretch;
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--db-status-pink-soft);
  font-size: 14px;
  line-height: 1.5;
  color: var(--db-status-pink);
}

.avatar-crop__sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.avatar-crop__actions {
  padding: 8px 16px 16px;
}
</style>
