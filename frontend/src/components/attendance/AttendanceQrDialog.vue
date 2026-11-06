<template>
  <!-- 디자인: docs/images/ui_design/QR 출석체크/QR_출석체크.png
       QR 을 띄운 동안 명단이 가려져 누가 찍었는지 몰랐다 — 창 안에 출석 수를 두고,
       빔프로젝터용 [크게 보기]로 창을 화면 가득 키운다 -->
  <q-dialog
    :model-value="modelValue"
    :maximized="large"
    @update:model-value="onToggle"
    @hide="onHide"
  >
    <q-card class="qr-dialog" :class="{ 'qr-dialog--large': large }">
      <q-card-section class="qr-dialog__head">
        <div>
          <div class="qr-dialog__title">출석 QR</div>
          <div class="qr-dialog__sub">
            수강생이 휴대폰 카메라로 QR을 찍으면 다대구 인증 뒤 출석이 기록됩니다.
          </div>
        </div>
        <q-btn flat round dense icon="close" aria-label="닫기" @click="close" />
      </q-card-section>

      <q-card-section class="qr-dialog__chips">
        <span class="qr-chip qr-chip--info">{{ courseLabel || '-' }}</span>
        <span class="qr-chip qr-chip--info">{{ sessionLabel || '-' }}</span>
        <span class="qr-chip" :class="stateChip.cls">{{ stateChip.label }}</span>
      </q-card-section>

      <q-card-section class="qr-dialog__body">
        <!-- 지금까지 출석한 수 — QR 이 바뀔 때·몇 초마다 명단을 다시 받아 늘어난다 -->
        <div v-if="totalCount" class="qr-dialog__count" role="status">
          출석 <strong>{{ attendedCount }}</strong> / {{ totalCount }}명
        </div>

        <div class="qr-dialog__frame">
          <img
            v-if="qrImage && !errorMessage"
            :src="qrImage"
            alt="출석 QR"
            class="qr-dialog__img"
          />
          <div v-else-if="errorMessage" class="qr-dialog__error" role="alert">
            <q-icon name="error_outline" size="32px" />
            <div>{{ errorMessage }}</div>
            <div v-if="retryIn" class="qr-dialog__retry-text">
              {{ retryIn }}초 뒤 다시 시도합니다
            </div>
            <q-btn
              unelevated
              no-caps
              color="primary"
              label="다시 시도"
              :loading="issuing"
              @click="issue"
            />
          </div>
          <q-spinner v-else size="40px" color="primary" />
        </div>

        <div v-if="qrImage && !errorMessage" class="qr-dialog__timer">
          <q-circular-progress
            show-value
            :value="remaining"
            :max="ttl || 1"
            size="36px"
            :thickness="0.18"
            color="primary"
            track-color="grey-3"
            class="qr-dialog__ring"
            :aria-label="`${remaining}초 남음`"
          >
            {{ remaining }}
          </q-circular-progress>
          <span>초 뒤 새 QR로 자동으로 바뀝니다</span>
        </div>

        <div v-if="!large" class="qr-dialog__note">
          QR은 약 {{ ttl || 60 }}초마다 바뀌고, 바뀌면 이전 QR은 바로 쓸 수 없습니다. 캡처해 전달한
          QR로는 출석할 수 없습니다.
        </div>
      </q-card-section>

      <!-- 자동으로 바뀌므로 [새로 받기]는 보조, 화면 키우기가 수업 중 더 잦다 -->
      <q-card-actions class="qr-dialog__actions">
        <q-btn
          outline
          no-caps
          color="primary"
          :icon="large ? 'fullscreen_exit' : 'fullscreen'"
          :label="large ? '작게 보기' : '크게 보기'"
          class="qr-dialog__btn"
          @click="large = !large"
        />
        <q-btn
          flat
          no-caps
          icon="refresh"
          label="QR 새로 받기"
          class="qr-dialog__btn qr-dialog__btn--sub"
          :loading="issuing"
          @click="issue"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import QRCode from 'qrcode';
import { issueAttendanceQrToken } from '@/services/attendance-api';

// 만료 몇 초 전에 미리 재발급할지 — 학생이 찍는 순간 만료되는 일을 줄인다
const REFRESH_BEFORE_SECONDS = 5;
const DEFAULT_TTL_SECONDS = 60;
// 자동 갱신이 실패하면 QR 이 사라진 채 멈췄다 — 이만큼 뒤에 스스로 다시 받는다
const RETRY_SECONDS = 5;

const ERROR_MESSAGES = {
  ATTENDANCE_WINDOW_CLOSED: '지금은 출결 가능 시간이 아닙니다. 회차 출결 시간을 확인해 주세요.',
  ATTENDANCE_CONFLICT: "회차가 '진행 중'일 때만 QR을 띄울 수 있습니다.",
};

function errorMessageOf(error) {
  const code = error?.payload?.code;
  if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
  if (error?.status === 403) return '이 회차의 QR은 주강사나 기관 관리자만 띄울 수 있습니다.';
  return error?.message || '출석 QR을 만들지 못했습니다.';
}

function isRetryable(error) {
  const code = error?.payload?.code;
  if (code && ERROR_MESSAGES[code]) return false;
  return error?.status !== 403;
}

export default {
  name: 'AttendanceQrDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    sessionId: { type: String, default: null },
    courseLabel: { type: String, default: '' },
    sessionLabel: { type: String, default: '' },
    /** 이 회차에 출석(출석·지각)으로 기록된 수 / 명단 인원 — 0 이면 숫자 줄을 숨긴다 */
    attendedCount: { type: Number, default: 0 },
    totalCount: { type: Number, default: 0 },
  },
  emits: ['update:modelValue', 'issued'],
  data() {
    return {
      qrImage: '',
      ttl: 0,
      remaining: 0,
      deadline: 0,
      issuing: false,
      errorMessage: '',
      timer: null,
      retryTimer: null,
      retryIn: 0,
      large: false,
    };
  },
  computed: {
    stateChip() {
      if (this.errorMessage) return { label: '만들지 못함', cls: 'qr-chip--error' };
      if (this.issuing && !this.qrImage) return { label: '만드는 중', cls: 'qr-chip--info' };
      return { label: '사용 가능', cls: 'qr-chip--live' };
    },
  },
  watch: {
    modelValue: {
      immediate: true,
      handler(open) {
        if (open) this.issue();
        else this.stop();
      },
    },
  },
  beforeUnmount() {
    this.stop();
  },
  methods: {
    onToggle(value) {
      this.$emit('update:modelValue', value);
    },
    close() {
      this.$emit('update:modelValue', false);
    },
    onHide() {
      this.stop();
      this.large = false;
    },
    stop() {
      if (this.timer) clearInterval(this.timer);
      this.timer = null;
      this.stopRetry();
    },
    stopRetry() {
      if (this.retryTimer) clearInterval(this.retryTimer);
      this.retryTimer = null;
      this.retryIn = 0;
    },
    /** 실패 뒤 RETRY_SECONDS 초를 세고 다시 받는다 — 창이 열려 있는 동안만 */
    scheduleRetry() {
      this.stopRetry();
      this.retryIn = RETRY_SECONDS;
      this.retryTimer = setInterval(() => {
        this.retryIn -= 1;
        if (this.retryIn > 0) return;
        this.stopRetry();
        if (this.modelValue) this.issue();
      }, 1000);
    },
    async issue() {
      if (!this.sessionId || this.issuing) return;
      this.issuing = true;
      this.stop();
      try {
        const data = await issueAttendanceQrToken(this.sessionId);
        if (!data?.attendUrl) throw new Error('QR을 만들지 못했습니다. 다시 시도해 주세요.');
        // 모달을 닫는 사이 응답이 오면 버린다
        if (!this.modelValue) return;
        this.qrImage = await QRCode.toDataURL(data.attendUrl, { width: 480, margin: 1 });
        this.ttl = data.ttlSeconds || DEFAULT_TTL_SECONDS;
        this.deadline = Date.now() + this.ttl * 1000;
        this.errorMessage = '';
        this.tick();
        this.timer = setInterval(this.tick, 1000);
        this.$emit('issued', data);
      } catch (error) {
        this.qrImage = '';
        this.errorMessage = errorMessageOf(error);
        // 권한·출결 시간처럼 다시 해도 같은 오류는 기다려도 소용없다 — 일시 오류만 스스로 다시 받는다
        if (this.modelValue && isRetryable(error)) this.scheduleRetry();
      } finally {
        this.issuing = false;
      }
    },
    tick() {
      this.remaining = Math.max(0, Math.ceil((this.deadline - Date.now()) / 1000));
      // TTL 이 아주 짧게 설정돼도 재발급이 연달아 돌지 않도록 절반을 넘기지 않는다
      if (this.remaining <= Math.min(REFRESH_BEFORE_SECONDS, this.ttl / 2)) this.issue();
    },
  },
};
</script>

<style scoped>
.qr-dialog {
  width: 520px;
  max-width: 94vw;
  border-radius: 12px;
}

.qr-dialog--large {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: none;
  border-radius: 0;
}

.qr-dialog--large .qr-dialog__body {
  flex: 1;
  justify-content: center;
}

/* 빔프로젝터 — 화면 높이·너비 중 작은 쪽에 맞춰 QR 을 키운다.
   머리·숫자·버튼 줄이 한 화면에 들어오게 높이의 절반 남짓으로 */
.qr-dialog--large .qr-dialog__frame {
  width: min(52vh, 86vw);
  height: min(52vh, 86vw);
}

.qr-dialog--large .qr-dialog__count {
  font-size: 28px;
}

.qr-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
}

.qr-dialog__title {
  font-size: 22px;
  line-height: 28px;
  font-weight: 700;
  color: var(--db-text-title);
}

.qr-dialog__sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.qr-dialog__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-top: 0;
}

.qr-chip {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.qr-chip--info {
  background: var(--db-bg-active);
  color: var(--db-primary);
}

.qr-chip--live {
  background: color-mix(in srgb, var(--db-status-green) 12%, var(--db-bg-white));
  color: var(--db-status-green);
}

.qr-chip--error {
  background: color-mix(in srgb, var(--db-status-pink) 10%, var(--db-bg-white));
  color: var(--db-status-pink);
}

.qr-dialog__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.qr-dialog__frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 240px;
  height: 240px;
  padding: 12px;
  border: 3px solid var(--db-primary);
  border-radius: 8px;
  background: var(--db-bg-white);
}

.qr-dialog__img {
  width: 100%;
  height: 100%;
  image-rendering: pixelated;
}

.qr-dialog__count {
  font-size: 18px;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.qr-dialog__count strong {
  color: var(--db-primary);
}

.qr-dialog__retry-text {
  font-size: 12px;
  color: var(--db-text-sub);
}

.qr-dialog__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
  font-size: 13px;
  color: var(--db-status-pink);
}

.qr-dialog__timer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--db-text-body);
}

.qr-dialog__ring {
  font-size: 11px;
  font-weight: 700;
}

.qr-dialog__note {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--db-border);
  border-radius: 6px;
  background: var(--db-bg-subtle);
  font-size: 12px;
  line-height: 18px;
  color: var(--db-text-sub);
}

.qr-dialog__actions {
  display: flex;
  gap: 8px;
  padding: 0 16px 16px;
}

.qr-dialog__btn {
  flex: 1;
  height: 44px;
  border-radius: 6px;
}

.qr-dialog__btn--sub {
  color: var(--db-text-body);
}
</style>
