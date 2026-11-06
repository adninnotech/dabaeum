<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="!!created || saving"
    @update:model-value="onToggle"
    @before-show="reset"
  >
    <q-card class="pf-share">
      <q-card-section class="pf-share__head">
        <div class="text-h6">{{ created ? '공유 링크를 만들었습니다' : '공유 링크 만들기' }}</div>
        <q-btn v-if="!created" v-close-popup flat round dense icon="close" aria-label="닫기" />
      </q-card-section>
      <q-separator />

      <!-- 1단계: 보여 줄 항목·기간 -->
      <q-card-section v-if="!created" class="pf-share__body">
        <p class="pf-share__lead">
          링크를 받은 사람은 로그인 없이 이름과 고른 수료증·배지만 볼 수 있습니다. 연락처·생년월일은 보이지 않습니다.
        </p>

        <fieldset class="pf-share__group">
          <legend class="pf-share__legend">
            수료증
            <button type="button" class="pf-share__all" @click="toggleAll('credential')">
              {{ allCredentials ? '모두 해제' : '모두 선택' }}
            </button>
          </legend>
          <p v-if="!credentials.length" class="pf-share__empty">발급된 수료증이 없습니다.</p>
          <q-checkbox
            v-for="item in credentials"
            :key="item.id"
            v-model="credentialIds"
            :val="item.id"
            :label="item.courseTitle"
            dense
          />
        </fieldset>

        <fieldset class="pf-share__group">
          <legend class="pf-share__legend">
            학습배지
            <button type="button" class="pf-share__all" @click="toggleAll('badge')">
              {{ allBadges ? '모두 해제' : '모두 선택' }}
            </button>
          </legend>
          <p v-if="!badges.length" class="pf-share__empty">받은 배지가 없습니다.</p>
          <q-checkbox
            v-for="item in badges"
            :key="item.id"
            v-model="badgeIds"
            :val="item.id"
            :label="item.name"
            dense
          />
        </fieldset>

        <q-select
          v-model="days"
          outlined
          dense
          emit-value
          map-options
          :options="DAY_OPTIONS"
          label="공개 기간"
          class="pf-share__days"
        />
        <p v-if="nothingPicked" class="pf-share__warn">수료증이나 배지를 하나 이상 골라 주세요.</p>
      </q-card-section>

      <!-- 2단계: 링크 1회 표시 -->
      <q-card-section v-else class="pf-share__body">
        <div class="pf-share__url">
          <code class="pf-share__url-value">{{ shareUrl }}</code>
          <q-btn
            unelevated
            no-caps
            dense
            color="primary"
            :icon="copied ? 'check' : 'content_copy'"
            :label="copied ? '복사됨' : '복사'"
            class="pf-share__copy"
            @click="copy"
          />
        </div>
        <p class="pf-share__meta">{{ formatDateTimeKst(created.expiresAt) }}까지 열립니다.</p>
        <p class="pf-share__warn" role="alert">
          이 창을 닫으면 링크를 다시 볼 수 없습니다. 복사해서 전달해 주세요. 필요 없어지면 목록에서 폐기할 수 있습니다.
        </p>
      </q-card-section>

      <q-card-actions align="right" class="pf-share__actions">
        <template v-if="!created">
          <q-btn v-close-popup flat no-caps label="취소" :disable="saving" />
          <q-btn
            unelevated
            no-caps
            color="primary"
            label="링크 만들기"
            :loading="saving"
            :disable="nothingPicked"
            @click="submit"
          />
        </template>
        <q-btn
          v-else
          unelevated
          no-caps
          color="primary"
          :label="copied ? '닫기' : '복사하지 않고 닫기'"
          @click="close"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import { copyToClipboard } from 'quasar';
import {
  createPortfolioShare,
  portfolioShareUrl,
  SHARE_DEFAULT_DAYS,
} from '@/services/portfolio-api';
import { formatDateTimeKst } from '@/utils/datetime';

const DAY_OPTIONS = [
  { label: '7일', value: 7 },
  { label: '30일', value: SHARE_DEFAULT_DAYS },
  { label: '90일', value: 90 },
  { label: '1년 (365일)', value: 365 },
];

/* 포트폴리오 공유 링크 만들기 — 토큰은 생성 응답에서 한 번만 온다. 떠 있는 동안 바깥 클릭·ESC 로 안 닫히고 닫으면 지운다 */
export default {
  name: 'PortfolioShareDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    credentials: { type: Array, default: () => [] },
    badges: { type: Array, default: () => [] },
  },
  emits: ['update:modelValue', 'created'],
  data() {
    return {
      DAY_OPTIONS,
      credentialIds: [],
      badgeIds: [],
      days: SHARE_DEFAULT_DAYS,
      saving: false,
      created: null,
      copied: false,
    };
  },
  computed: {
    allCredentials() {
      return this.credentials.length > 0 && this.credentialIds.length === this.credentials.length;
    },
    allBadges() {
      return this.badges.length > 0 && this.badgeIds.length === this.badges.length;
    },
    nothingPicked() {
      return !this.credentialIds.length && !this.badgeIds.length;
    },
    shareUrl() {
      return this.created ? portfolioShareUrl(this.created.token) : '';
    },
  },
  methods: {
    formatDateTimeKst,
    reset() {
      this.credentialIds = this.credentials.map((item) => item.id);
      this.badgeIds = this.badges.map((item) => item.id);
      this.days = SHARE_DEFAULT_DAYS;
      this.created = null;
      this.copied = false;
    },
    onToggle(open) {
      if (!open && this.created) return;
      this.$emit('update:modelValue', open);
    },
    close() {
      this.created = null;
      this.$emit('update:modelValue', false);
    },
    toggleAll(kind) {
      if (kind === 'credential') {
        this.credentialIds = this.allCredentials ? [] : this.credentials.map((item) => item.id);
      } else {
        this.badgeIds = this.allBadges ? [] : this.badges.map((item) => item.id);
      }
    },
    async submit() {
      if (this.nothingPicked) return;
      this.saving = true;
      try {
        // 서버는 목록을 빼면 "전부"로 본다 — 고른 것을 그대로 보내 뜻이 어긋나지 않게 한다(빈 목록도 그대로)
        this.created = await createPortfolioShare({
          credentialIds: this.credentialIds,
          badgeIds: this.badgeIds,
          expiresInDays: this.days,
        });
        this.$emit('created');
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message:
            error?.status === 422
              ? '고른 항목을 확인해 주세요. 내 수료증·배지만 공유할 수 있습니다.'
              : error?.message || '공유 링크를 만들지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.saving = false;
      }
    },
    async copy() {
      try {
        await copyToClipboard(this.shareUrl);
        this.copied = true;
      } catch {
        this.$q.notify({
          type: 'warning',
          message: '복사하지 못했습니다. 직접 선택해서 복사해 주세요.',
          position: 'top',
        });
      }
    },
  },
};
</script>

<style scoped>
.pf-share {
  width: 520px;
  max-width: 94vw;
}

.pf-share__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pf-share__body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 64vh;
  overflow-y: auto;
}

.pf-share__lead {
  margin: 0;
  font-size: 14px;
  line-height: 19px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pf-share__group {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding: 10px 12px 12px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pf-share__legend {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.pf-share__all {
  padding: 0;
  border: 0;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--db-accent);
  cursor: pointer;
}

.pf-share__all:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.pf-share__empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.pf-share__days {
  max-width: 200px;
}

.pf-share__url {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 8px 12px;
  background: var(--db-bg-page);
  border: 1px solid var(--db-border);
  border-radius: 6px;
}

.pf-share__url-value {
  flex: 1;
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
  user-select: all;
}

.pf-share__copy {
  flex-shrink: 0;
  padding: 0 12px;
}

.pf-share__meta {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-body);
}

.pf-share__warn {
  margin: 0;
  font-size: 14px;
  line-height: 18px;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.pf-share__actions {
  padding: 8px 16px 16px;
}
</style>
