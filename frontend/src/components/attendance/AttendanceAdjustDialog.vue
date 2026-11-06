<template>
  <!-- 출결 정정 — '지금 → 바꿀 상태'를 크게 보이고, 창 안에서도 바꿀 상태를 고칠 수 있다.
       자주 쓰는 사유는 칩으로 채우고 덧붙여 쓴다(예전엔 제목 한 줄 + 빈 칸이라 매번 처음부터 쳤다) -->
  <q-dialog
    :model-value="modelValue"
    :persistent="saving"
    @update:model-value="close"
    @before-show="reset"
    @show="focusReason"
  >
    <q-card class="att-adjust">
      <header class="att-adjust__head">
        <h2 class="att-adjust__title">출결 정정</h2>
        <button type="button" class="att-adjust__close" aria-label="닫기" :disabled="saving" @click="close(false)">
          <q-icon name="close" size="22px" aria-hidden="true" />
        </button>
      </header>

      <div class="att-adjust__body">
        <p class="att-adjust__who">
          <strong>{{ target?.name }}</strong>
          <span v-if="metaText" class="att-adjust__meta">{{ metaText }}</span>
        </p>

        <!-- 지금 → 바꿀 상태 -->
        <div class="att-adjust__change" aria-live="polite">
          <span class="att-adjust__from">
            <span class="att-adjust__cap">지금</span>
            <StatusBadge :status="fromStatus" />
          </span>
          <q-icon name="arrow_forward" size="20px" class="att-adjust__arrow" aria-hidden="true" />
          <span class="att-adjust__to">
            <span class="att-adjust__cap">바꿀 상태</span>
            <StatusBadge v-if="toStatus" :status="toStatus" />
            <span v-else class="att-adjust__none">아래에서 고르세요</span>
          </span>
        </div>

        <AttendanceStatusSegment
          v-model="toStatus"
          :locked="fromStatus"
          label="바꿀 출결 상태"
          :disabled="saving"
          class="att-adjust__seg"
        />

        <div class="att-adjust__field">
          <label class="att-adjust__label" for="att-adjust-reason">정정 사유 <span class="att-adjust__req">(필수)</span></label>
          <div class="att-adjust__chips" role="group" aria-label="자주 쓰는 사유">
            <button
              v-for="preset in REASON_PRESETS"
              :key="preset"
              type="button"
              class="att-adjust__chip"
              :class="{ 'att-adjust__chip--on': reason.trim() === preset }"
              :disabled="saving"
              @click="usePreset(preset)"
            >
              {{ preset }}
            </button>
          </div>
          <textarea
            id="att-adjust-reason"
            ref="reason"
            v-model="reason"
            class="att-adjust__textarea"
            rows="3"
            :maxlength="REASON_MAX"
            placeholder="예) 수업 중 QR이 열리지 않아 강사가 출석을 확인함"
            :disabled="saving"
            aria-describedby="att-adjust-hint"
            @keydown="onKeydown"
          />
          <div id="att-adjust-hint" class="att-adjust__hint">
            <span>정정 이력에 남습니다. 출석 시각은 바뀌지 않습니다.</span>
            <span class="att-adjust__count">{{ reason.length }}/{{ REASON_MAX }}</span>
          </div>
        </div>

        <p v-if="serverError" class="att-adjust__error" role="alert">
          <q-icon name="error_outline" size="18px" aria-hidden="true" />
          <span>{{ serverError }}</span>
        </p>
      </div>

      <footer class="att-adjust__foot">
        <span class="att-adjust__keys">Enter 저장 · Shift+Enter 줄바꿈</span>
        <q-btn flat no-caps label="취소" class="att-adjust__btn" :disable="saving" @click="close(false)" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :label="submitLabel"
          class="att-adjust__btn"
          :disable="!canSubmit"
          :loading="saving"
          @click="submit"
        />
      </footer>
    </q-card>
  </q-dialog>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import AttendanceStatusSegment from '@/components/attendance/AttendanceStatusSegment.vue';
import { adjustAttendance } from '@/services/attendance-api';
import { formatTimeKst } from '@/utils/datetime';
import { ATTENDANCE_METHOD_LABEL, STATUS_LABEL } from '@/utils/status';

// 계약(AttendanceAdjustmentRequest.reason): 1~1000자
const REASON_MAX = 1000;
// 정정 사유로 자주 쓰는 것 — 눌러서 채우고 덧붙여 쓴다
const REASON_PRESETS = ['QR 인식 오류', '공결 서류 제출', '지각 확인', '잘못 눌러 기록', '수업 시간 변경'];

export default {
  name: 'AttendanceAdjustDialog',
  components: { StatusBadge, AttendanceStatusSegment },
  props: {
    modelValue: { type: Boolean, default: false },
    /** { attendanceId, name, fromStatus(지금), status(처음 고른 바꿀 상태), attendanceMethod?, checkedAt? } */
    target: { type: Object, default: null },
  },
  /** saved(attendanceId) — 화면이 그 행을 잠깐 강조한다 */
  emits: ['update:modelValue', 'saved'],
  data() {
    return { REASON_MAX, REASON_PRESETS, toStatus: '', reason: '', saving: false, serverError: '' };
  },
  computed: {
    fromStatus() {
      return this.target?.fromStatus || '';
    },
    /** 'QR · 10:03 기록' */
    metaText() {
      const t = this.target;
      if (!t) return '';
      const method = ATTENDANCE_METHOD_LABEL[t.attendanceMethod] || '';
      const time = t.checkedAt ? `${formatTimeKst(t.checkedAt)} 기록` : '';
      return [method, time].filter(Boolean).join(' · ');
    },
    trimmedReason() {
      return this.reason.trim();
    },
    canSubmit() {
      return Boolean(this.toStatus && this.toStatus !== this.fromStatus && this.trimmedReason) && !this.saving;
    },
    submitLabel() {
      return this.toStatus ? `${STATUS_LABEL[this.toStatus]}(으)로 정정` : '정정';
    },
  },
  methods: {
    reset() {
      const picked = this.target?.status;
      this.toStatus = picked && picked !== this.fromStatus ? picked : '';
      this.reason = '';
      this.serverError = '';
      this.saving = false;
    },
    focusReason() {
      this.$refs.reason?.focus();
    },
    close(value = false) {
      if (this.saving) return;
      this.$emit('update:modelValue', value);
    },
    /** 칸이 비었으면 채우고, 이미 쓴 글이 있으면 앞에 붙인다(덧붙여 쓴 설명은 살린다) */
    usePreset(preset) {
      const current = this.trimmedReason;
      if (!current || REASON_PRESETS.includes(current)) this.reason = preset;
      else if (!current.startsWith(preset)) this.reason = `${preset} — ${current}`.slice(0, REASON_MAX);
      this.$nextTick(() => {
        const el = this.$refs.reason;
        if (!el) return;
        el.focus();
        el.setSelectionRange(el.value.length, el.value.length);
      });
    },
    onKeydown(event) {
      // 한글 조합 중 Enter 는 조합만 끝낸다
      if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
      event.preventDefault();
      if (this.canSubmit) this.submit();
    },
    async submit() {
      if (!this.target?.attendanceId || !this.canSubmit) return;
      this.saving = true;
      this.serverError = '';
      try {
        await adjustAttendance(this.target.attendanceId, {
          status: this.toStatus,
          reason: this.trimmedReason,
        });
        this.$q.notify({
          type: 'positive',
          message: `${this.target.name} 님을 '${STATUS_LABEL[this.toStatus]}'(으)로 정정했습니다.`,
          position: 'top',
        });
        this.saving = false;
        this.$emit('update:modelValue', false);
        this.$emit('saved', this.target.attendanceId);
      } catch (error) {
        // 창을 닫지 않는다 — 쓴 사유가 날아가지 않게
        this.serverError = error?.message || '정정하지 못했습니다. 잠시 뒤 다시 시도해 주세요.';
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: dialog · genre: modern-minimal · theme: project tokens (--db-*)
 * states: default · hover · focus · active · disabled · loading · error · success
 */
.att-adjust {
  width: 520px;
  max-width: calc(100vw - 32px);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.att-adjust__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 12px 0 24px;
}

.att-adjust__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--db-text-title);
}

.att-adjust__close {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--db-text-sub);
  cursor: pointer;
}

.att-adjust__close:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.att-adjust__close:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.att-adjust__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 24px 20px;
}

.att-adjust__who {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  color: var(--db-text-title);
}

.att-adjust__meta {
  font-size: 15px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 지금 → 바꿀 상태 */
.att-adjust__change {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.att-adjust__from,
.att-adjust__to {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.att-adjust__cap {
  font-size: 14px;
  color: var(--db-text-sub);
}

.att-adjust__arrow {
  color: var(--db-text-sub);
}

.att-adjust__none {
  font-size: 15px;
  color: var(--db-text-placeholder);
}

.att-adjust__seg {
  display: grid;
  width: 100%;
}

.att-adjust__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.att-adjust__label {
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.att-adjust__req {
  font-size: 14px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.att-adjust__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.att-adjust__chip {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--db-border);
  border-radius: 999px;
  background: var(--db-bg-white);
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-body);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.att-adjust__chip--on {
  border-color: var(--db-accent);
  background: var(--db-accent-soft);
  color: var(--db-accent);
  font-weight: 600;
}

.att-adjust__chip:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.att-adjust__chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.att-adjust__textarea {
  width: 100%;
  min-height: 88px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-white);
  font-family: inherit;
  font-size: 16px;
  line-height: 1.5;
  color: var(--db-text-title);
  resize: vertical;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.att-adjust__textarea::placeholder {
  color: var(--db-text-placeholder);
}

.att-adjust__textarea:focus {
  outline: none;
  border-color: var(--db-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--db-primary) 15%, transparent);
}

.att-adjust__textarea:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.att-adjust__hint {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.att-adjust__count {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.att-adjust__error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--db-status-pink-soft);
  font-size: 15px;
  line-height: 1.45;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.att-adjust__error .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.att-adjust__foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px 20px;
  border-top: 1px solid var(--db-border);
}

.att-adjust__keys {
  margin-right: auto;
  font-size: 13px;
  color: var(--db-text-sub);
}

.att-adjust__btn {
  min-height: 44px;
  padding: 0 16px;
  font-size: 15px;
  font-weight: 600;
}

@media (hover: hover) {
  .att-adjust__close:hover:not(:disabled) {
    background: var(--db-bg-subtle);
    color: var(--db-text-title);
  }

  .att-adjust__chip:hover:not(:disabled):not(.att-adjust__chip--on) {
    border-color: var(--db-accent);
    color: var(--db-accent);
  }
}

@media (prefers-reduced-motion: reduce) {
  .att-adjust__chip,
  .att-adjust__textarea {
    transition: none;
  }
}

/* 폰: 키보드 안내는 뜻이 없다 */
@media (max-width: 599px) {
  .att-adjust__head {
    padding-left: 20px;
  }

  .att-adjust__body {
    padding: 8px 20px 16px;
  }

  .att-adjust__foot {
    justify-content: flex-end;
    padding: 12px 20px 16px;
  }

  .att-adjust__keys {
    display: none;
  }

  .att-adjust__btn {
    flex: 1;
  }
}
</style>
