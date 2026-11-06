<template>
  <div
    class="badge-medal"
    :class="{ 'badge-medal--muted': muted }"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <img v-if="src && !broken" :src="src" :alt="alt" class="badge-medal__img" @error="broken = true" />
    <q-icon v-else name="o_military_tech" :size="`${Math.round(size * 0.5)}px`" class="badge-medal__icon" />
  </div>
</template>

<script>
import { resolveStoredFileUrl, storedFileContentUrl } from '@/services/file-api';

/* 배지 이미지 — 이미지가 없거나 깨지면 훈장 아이콘. muted 는 아직 못 딴 배지(흑백·흐림) */
export default {
  name: 'BadgeMedal',
  props: {
    imageUrl: { type: String, default: '' },
    imageFileId: { type: String, default: '' },
    size: { type: Number, default: 72 },
    muted: { type: Boolean, default: false },
    alt: { type: String, default: '' },
  },
  data() {
    return { broken: false };
  },
  computed: {
    src() {
      if (this.imageUrl) return resolveStoredFileUrl(this.imageUrl);
      return this.imageFileId ? storedFileContentUrl(this.imageFileId) : '';
    },
  },
  watch: {
    src() {
      this.broken = false;
    },
  },
};
</script>

<style scoped>
.badge-medal {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--db-accent-soft);
}

.badge-medal__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 흰 바탕 배지 그림이 어두운 화면에서 흰 원으로 번쩍였다 — 다크에선 조금 낮춘다 */
:global(body.body--dark .badge-medal__img) {
  filter: brightness(0.85);
}

:global(body.body--dark .badge-medal--muted .badge-medal__img) {
  filter: grayscale(1) brightness(0.85);
}

.badge-medal__icon {
  color: var(--db-accent);
}

.badge-medal--muted {
  background: var(--db-bg-page);
}

.badge-medal--muted .badge-medal__img {
  filter: grayscale(1);
  opacity: 0.45;
}

.badge-medal--muted .badge-medal__icon {
  color: var(--db-text-sub);
  opacity: 0.6;
}
</style>
