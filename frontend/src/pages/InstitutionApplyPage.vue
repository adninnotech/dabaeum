<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body apply-page">
      <!-- 마이페이지에서 들어오는 학습자 화면 — 콘솔용 app-page-header 대신 학습자 머리(h1) -->
      <LearnerPageHead
        title="기관 등록 신청"
        subtitle="이미 가입한 계정으로 교육기관 등록을 신청합니다."
        :back="{ label: '마이페이지', to: '/mypage' }"
      />

      <ApplySteps :steps="['기관 정보를 적어 신청', '플랫폼 관리자가 검토', '승인되면 이 계정이 기관 관리자가 됩니다']" />

      <p v-if="!isLoggedIn" class="apply-note" role="status">로그인 후 신청할 수 있습니다.</p>
      <p v-else-if="pendingApplication" class="apply-note apply-note--wait" role="status">
        심사 중인 신청이 있습니다({{ pendingApplication.institutionName }}). 결과가 나온 뒤 새로 신청할 수 있습니다.
      </p>

      <section class="apply-card" aria-labelledby="apply-new-title">
        <h2 id="apply-new-title" class="apply-card__title">기관 정보</h2>
        <form class="apply-form" novalidate @submit.prevent="submitApply">
          <q-input
            v-for="field in FIELDS"
            :key="field.key"
            :model-value="form[field.key]"
            outlined
            :type="field.type || 'text'"
            :label="field.required ? `${field.label} *` : field.label"
            :placeholder="field.placeholder"
            :maxlength="field.max"
            :error="!!errors[field.key]"
            :error-message="errors[field.key]"
            no-error-icon
            :class="{ 'apply-form__wide': field.wide }"
            @update:model-value="setField(field, $event)"
          />
          <div class="apply-form__foot">
            <q-btn
              type="submit"
              color="primary"
              unelevated
              no-caps
              label="기관 등록 신청하기"
              class="apply-card__submit"
              :loading="submitting"
              :disable="!isLoggedIn || !!pendingApplication"
            />
          </div>
        </form>
      </section>

      <ApplyHistoryList :items="historyItems" :loading="loading" :error="errorMessage" />
    </div>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import LearnerPageHead from '@/components/LearnerPageHead.vue';
import ApplyHistoryList from '@/components/apply/ApplyHistoryList.vue';
import ApplySteps from '@/components/apply/ApplySteps.vue';
import { applyInstitutionJoin, listMyInstitutionJoinApplications } from '@/services/institution-api';
import { useAuthStore } from '@/stores/auth-store';
import { qFormatDateTimeKst } from '@/utils/datetime';
import { formatTelephone } from '@/utils/phone';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/* 서버 InstitutionJoinApplicationCreateRequest 와 같은 칸·길이. 기관 코드는 비우면 승인 때 서버가 만든다 —
 * 이미 있는 코드를 넣으면 승인 때 409 가 나므로 받지 않는다 */
const FIELDS = [
  { key: 'institutionName', label: '기관명', required: true, max: 200, placeholder: '기관 이름' },
  { key: 'representativeName', label: '대표자명', required: true, max: 100, placeholder: '대표자 이름' },
  { key: 'contactPhone', label: '기관 전화번호', required: true, max: 50, phone: true, placeholder: '053-000-0000' },
  {
    key: 'contactEmail',
    label: '기관 이메일',
    required: true,
    max: 255,
    type: 'email',
    placeholder: 'example@email.com',
  },
  { key: 'address', label: '주소', max: 500, wide: true, placeholder: '기관 주소' },
];

const EMPTY_FORM = Object.freeze(Object.fromEntries(FIELDS.map((field) => [field.key, ''])));

function validate(form) {
  const errors = {};
  FIELDS.forEach((field) => {
    const value = String(form[field.key] || '').trim();
    if (field.required && !value) errors[field.key] = '필수 입력 항목입니다.';
    else if (field.key === 'contactEmail' && value && !EMAIL.test(value)) {
      errors[field.key] = '올바른 이메일을 입력해 주세요.';
    }
  });
  return errors;
}

function buildBody(form) {
  const address = form.address.trim();
  return {
    institutionName: form.institutionName.trim(),
    representativeName: form.representativeName.trim(),
    contactEmail: form.contactEmail.trim(),
    contactPhone: form.contactPhone.trim(),
    address: address || null,
  };
}

export default {
  name: 'InstitutionApplyPage',
  components: { ApplyHistoryList, ApplySteps, LearnerPageHead },
  data() {
    return {
      FIELDS,
      form: { ...EMPTY_FORM },
      errors: {},
      submitting: false,
      loading: false,
      errorMessage: '',
      rows: [],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn']),
    // 넓은 표(폰에서 넘쳤다) 대신 카드 — 결과 문구는 note 로
    historyItems() {
      return this.rows.map((row) => ({
        id: row.id,
        title: row.institutionName || '기관',
        status: row.status,
        meta: [
          `${qFormatDateTimeKst(row.createdAt)} 신청`,
          row.decidedAt ? `${qFormatDateTimeKst(row.decidedAt)} 처리` : '',
        ].filter(Boolean).join(' · '),
        note: row.status === 'REJECTED' ? `거절 사유: ${this.resultText(row)}` : this.resultText(row),
      }));
    },
    pendingApplication() {
      return this.rows.find((row) => row.status === 'PENDING') || null;
    },
  },
  mounted() {
    this.loadMine();
  },
  methods: {
    setField(field, value) {
      const text = String(value ?? '');
      this.form = { ...this.form, [field.key]: field.phone ? formatTelephone(text) : text };
      if (this.errors[field.key]) {
        this.errors = Object.fromEntries(Object.entries(this.errors).filter(([key]) => key !== field.key));
      }
    },
    resultText(row) {
      if (row.status === 'REJECTED') return row.rejectionReason || '거절 사유 없음';
      if (row.status === 'APPROVED') return '기관 콘솔을 쓸 수 있습니다. 메뉴에 보이지 않으면 다시 로그인해 주세요.';
      return '플랫폼 관리자가 심사 중입니다.';
    },
    async loadMine() {
      if (!this.isLoggedIn) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listMyInstitutionJoinApplications({ page: 0, size: 50 });
        this.rows = Array.isArray(page.data) ? page.data : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error?.message || '신청 이력을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    submitError(error) {
      // 신청자당 심사 중 1건 — 서버 INSTITUTION_APPLICATION_CONFLICT
      if (error?.payload?.code === 'INSTITUTION_APPLICATION_CONFLICT') {
        return '이미 심사 중인 기관 등록 신청이 있습니다. 결과가 나온 뒤 다시 신청해 주세요.';
      }
      if (error?.status === 422) return '입력한 기관 정보를 확인해 주세요. 길이나 형식이 맞지 않는 칸이 있습니다.';
      return error?.message || '신청에 실패했습니다.';
    },
    async submitApply() {
      if (this.submitting) return;
      this.errors = validate(this.form);
      if (Object.keys(this.errors).length) return;
      this.submitting = true;
      try {
        await applyInstitutionJoin(buildBody(this.form));
        this.form = { ...EMPTY_FORM };
        this.$q.notify({ type: 'positive', message: '기관 등록 신청이 접수되었습니다.', position: 'top' });
        await this.loadMine();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: this.submitError(error), position: 'top' });
        if (error?.payload?.code === 'INSTITUTION_APPLICATION_CONFLICT') await this.loadMine();
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.apply-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.apply-note {
  margin: 0;
  padding: 12px 14px;
  font-size: 14px;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  border-radius: 8px;
  word-break: keep-all;
}

.apply-note--wait {
  color: var(--db-status-orange);
}

.apply-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border: 1px solid var(--db-border);
  border-radius: 12px;
  background: var(--db-bg-white);
}

.apply-card__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.apply-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}

.apply-form__wide,
.apply-form__foot {
  grid-column: 1 / -1;
}

.apply-card__submit {
  min-height: 44px;
  padding: 0 20px;
  border-radius: 6px;
}

@media (max-width: 599px) {
  .apply-card {
    padding: 16px;
  }

  .apply-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .apply-card__submit {
    width: 100%;
  }
}
</style>
