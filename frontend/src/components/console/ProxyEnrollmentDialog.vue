<template>
  <!-- 기관 대리 수강신청 — 학습자를 이름 + 생년월일 + 휴대전화로 찾아(셋이 모두 맞는 1명) 확인한 뒤 신청한다(중앙 #56, #142).
       예전엔 학습자 회원 UUID 를 직접 넣어야 해 기관 관리자는 쓸 수 없었다. 평생학번이 없어졌고 다대구 회원은 이메일이
       없어 다대구 인증으로 받은 세 값으로 찾는다. 셋 다 맞아야 해 다른 기관 회원 명단을 모으지 못한다 -->
  <q-dialog
    :model-value="modelValue"
    :persistent="saving"
    @update:model-value="$emit('update:modelValue', $event)"
    @before-show="reset"
  >
    <q-card class="proxy-dialog">
      <q-card-section class="proxy-dialog__title">대리 수강신청</q-card-section>
      <q-card-section class="proxy-dialog__body">
        <q-select
          v-model="courseId"
          outlined
          dense
          emit-value
          map-options
          :options="courseOptions"
          label="강의"
        />

        <div class="proxy-dialog__lookup">
          <p class="proxy-dialog__guide">이름·생년월일·휴대전화가 모두 맞는 학습자 1명을 찾습니다.</p>
          <div class="proxy-dialog__find">
            <q-input
              :model-value="name"
              outlined
              dense
              label="이름"
              autocomplete="off"
              maxlength="100"
              hide-bottom-space
              class="proxy-dialog__input"
              @update:model-value="onNameChange"
              @keydown="onKeydown"
            />
            <AppDateInput
              :model-value="birthDate"
              outlined
              dense
              hide-bottom-space
              label="생년월일"
              default-view="Years"
              no-future
              class="proxy-dialog__birth"
              @update:model-value="onBirthDateChange"
            />
            <q-input
              :model-value="phone"
              outlined
              dense
              label="휴대전화"
              type="tel"
              inputmode="tel"
              autocomplete="off"
              placeholder="예: 010-1234-5678"
              hide-bottom-space
              class="proxy-dialog__input"
              @update:model-value="onPhoneChange"
              @keydown="onKeydown"
            />
            <q-btn
              outline
              no-caps
              color="primary"
              label="찾기"
              class="proxy-dialog__find-btn"
              :loading="finding"
              :disable="!name.trim() || !birthDate || !phone.trim()"
              @click="find"
            />
          </div>
          <p v-if="formatError" class="proxy-dialog__error" role="alert">{{ formatError }}</p>
          <p v-if="lookupError" class="proxy-dialog__error" role="alert">{{ lookupError }}</p>
        </div>

        <!-- 신청 전에 이름으로 한 번 더 확인 — 생년월일·휴대전화는 관리자가 이미 넣은 값이라 서버가 다시 내려주지 않는다 -->
        <div v-if="learner" class="proxy-dialog__found" role="group" aria-label="찾은 학습자">
          <q-icon name="person" size="20px" class="proxy-dialog__found-icon" aria-hidden="true" />
          <div class="proxy-dialog__found-text">
            <div class="proxy-dialog__found-name">{{ learner.name || '-' }}</div>
            <!-- 날짜가 '1990-01-' / '15' 로 갈라졌다 — 덩어리째 줄을 바꾼다 -->
            <div class="proxy-dialog__found-sub">
              <span>생년월일 {{ foundBirthDate }} 일치</span>
              <span>휴대전화 일치</span>
            </div>
          </div>
        </div>
      </q-card-section>
      <q-card-actions align="right">
        <q-btn v-close-popup flat no-caps label="닫기" :disable="saving" />
        <q-btn
          color="primary"
          unelevated
          no-caps
          label="신청"
          :loading="saving"
          :disable="!courseId || !learner"
          @click="enroll"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>

<script>
import AppDateInput from '@/components/AppDateInput.vue';
import { createProxyEnrollment } from '@/services/enrollment-api';
import { lookupInstitutionLearner } from '@/services/institution-api';
import { lookupQuery, retryAfterText } from '@/utils/learner-lookup';

// 서버는 값이 틀렸는지·없는 사람인지·여러 명인지 구분해 주지 않는다(명단 수집 방지) — 모두 같은 안내
const NOT_FOUND_MESSAGE =
  '이름·생년월일·휴대전화가 모두 일치하는 대리 신청 가능 학습자가 없습니다. 같은 정보의 회원이 여러 명이어도 찾을 수 없습니다.';
const NO_INSTITUTION_MESSAGE = '기관을 먼저 선택해 주세요.';

// 429 는 둘 중 하나 — 짧은 시간에 너무 많이 조회했거나, 같은 휴대전화 번호를 누가 조회하든 하루 10회를 넘김(생년월일 맞히기 방지).
// 서버가 둘을 같은 코드로 주니 함께 안내한다. Retry-After 가 24시간이면 초로는 읽을 수 없어 시간으로 바꾼다
function rateLimitMessage(error) {
  const wait = retryAfterText(error?.retryAfter);
  const base = '조회가 잠시 제한되었습니다. 같은 학습자를 여러 번 조회했거나 짧은 시간에 많이 조회했습니다.';
  return wait ? `${base} ${wait} 뒤 다시 시도해 주세요.` : `${base} 잠시 뒤 다시 시도해 주세요.`;
}

function lookupErrorMessage(error) {
  if (error?.status === 404) return NOT_FOUND_MESSAGE;
  if (error?.status === 429) return rateLimitMessage(error);
  return error?.message || '학습자를 찾지 못했습니다.';
}

export default {
  name: 'ProxyEnrollmentDialog',
  components: { AppDateInput },
  props: {
    modelValue: { type: Boolean, default: false },
    institutionId: { type: String, default: '' },
    courseOptions: { type: Array, default: () => [] },
    /** 창을 열 때 고를 강의 — 목록에서 고른 강의가 있으면 그것 */
    initialCourseId: { type: String, default: null },
  },
  emits: ['update:modelValue', 'enrolled'],
  data() {
    return {
      courseId: null,
      name: '',
      birthDate: '',
      phone: '',
      // 찾을 때 쓴 생년월일 — 확인 카드에 '일치'로 보인다
      foundBirthDate: '',
      formatError: '',
      lookupError: '',
      learner: null,
      finding: false,
      // 늦게 온 옛 조회 응답이 바뀐 입력 위에 뜨지 않게 — 입력이 바뀔 때마다 올린다
      lookupSeq: 0,
      saving: false,
    };
  },
  methods: {
    reset() {
      this.courseId = this.initialCourseId || this.courseOptions[0]?.value || null;
      this.clearInput();
    },
    clearInput() {
      this.name = '';
      this.birthDate = '';
      this.phone = '';
      this.clearResult();
    },
    // 입력이 바뀌면 찾은 사람을 지우고 진행 중인 조회 응답도 버린다 — 다른 사람으로 신청되지 않게
    clearResult() {
      this.lookupSeq += 1;
      this.finding = false;
      this.formatError = '';
      this.lookupError = '';
      this.learner = null;
      this.foundBirthDate = '';
    },
    onNameChange(value) {
      this.name = value ?? '';
      this.clearResult();
    },
    onPhoneChange(value) {
      this.phone = value ?? '';
      this.clearResult();
    },
    onBirthDateChange(value) {
      this.birthDate = value ?? '';
      this.clearResult();
    },
    onKeydown(event) {
      if (event.key === 'Enter') this.find();
    },
    async find() {
      // 조회 중 Enter 를 거듭 누르면 같은 요청이 여러 번 나가 호출 제한을 헛쓴다
      if (this.finding) return;
      const { query, error } = lookupQuery({ name: this.name, birthDate: this.birthDate, phone: this.phone });
      this.clearResult();
      if (!query) {
        this.formatError = error;
        return;
      }
      if (!this.institutionId) {
        this.lookupError = NO_INSTITUTION_MESSAGE;
        return;
      }
      const seq = this.lookupSeq;
      this.finding = true;
      try {
        const learner = await lookupInstitutionLearner(this.institutionId, query);
        if (seq !== this.lookupSeq) return;
        this.learner = learner;
        this.foundBirthDate = query.birthDate;
      } catch (lookupError) {
        if (seq !== this.lookupSeq) return;
        this.lookupError = lookupErrorMessage(lookupError);
      } finally {
        if (seq === this.lookupSeq) this.finding = false;
      }
    },
    async enroll() {
      if (!this.courseId || !this.learner?.userId) return;
      this.saving = true;
      try {
        await createProxyEnrollment(this.courseId, { userId: this.learner.userId });
        this.$q.notify({
          type: 'positive',
          message: `${this.learner.name || '학습자'} 님 대리 수강신청을 마쳤습니다.`,
          position: 'top',
        });
        this.$emit('enrolled');
        this.$emit('update:modelValue', false);
      } catch (error) {
        // 실패하면 창을 닫지 않는다 — 다른 강의로 바꿔 다시 신청할 수 있다
        this.$q.notify({ type: 'negative', message: error?.message || '대리 수강신청을 하지 못했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
/* 폰에서 넘치지 않게 */
.proxy-dialog {
  width: 520px;
  max-width: 94vw;
}

.proxy-dialog__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.proxy-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 0;
}

.proxy-dialog__lookup {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.proxy-dialog__guide {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--db-text-sub);
  word-break: keep-all;
}

/* 이름·생년월일·휴대전화가 한 줄에 다 안 들어가 감싼다 */
.proxy-dialog__find {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 8px;
}

.proxy-dialog__input {
  flex: 1 1 160px;
  min-width: 0;
}

.proxy-dialog__birth {
  flex: 1 1 140px;
  min-width: 0;
}

/* dense 입력칸(40px)과 같은 높이 */
.proxy-dialog__find-btn {
  min-height: 40px;
  white-space: nowrap;
}

.proxy-dialog__error {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--db-status-pink);
  word-break: keep-all;
}

.proxy-dialog__found {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.proxy-dialog__found-icon {
  flex-shrink: 0;
  color: var(--db-text-sub);
}

.proxy-dialog__found-text {
  min-width: 0;
}

.proxy-dialog__found-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  font-variant-numeric: tabular-nums;
}

.proxy-dialog__found-sub {
  display: flex;
  flex-wrap: wrap;
  /* 가운뎃점은 줄이 바뀌면 새 줄 맨 앞에 남아 간격으로만 나눈다 */
  column-gap: 12px;
  margin-top: 2px;
  font-size: 13px;
  color: var(--db-text-sub);
}

.proxy-dialog__found-sub span {
  white-space: nowrap;
}
</style>
