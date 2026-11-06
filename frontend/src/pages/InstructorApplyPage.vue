<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body apply-page">
      <!-- 마이페이지에서 들어오는 학습자 화면 — 콘솔용 app-page-header 대신 학습자 머리(h1) -->
      <LearnerPageHead
        title="강사 신청"
        subtitle="가르치고 싶은 기관에 강사로 신청합니다."
        :back="{ label: '마이페이지', to: '/mypage' }"
      />

      <ApplySteps :steps="['기관을 골라 신청', '기관 관리자가 검토', '승인되면 강사 콘솔에서 강의를 엽니다']" />

      <p v-if="!isLoggedIn" class="apply-note" role="status">로그인 후 신청할 수 있습니다.</p>
      <p v-else-if="isInstructor" class="apply-note apply-note--ok" role="status">
        이미 강사 역할이 있습니다. 다른 기관에도 신청할 수 있습니다.
      </p>

      <!-- 고객센터 '문의하기'와 같은 흰 카드·이름표 칸·주 버튼 -->
      <section class="apply-card" aria-labelledby="apply-new-title">
        <h2 id="apply-new-title" class="apply-card__title">새 신청</h2>
        <!-- 글자를 쳐서 거른다 — '기관명 (코드·UUID)' 50개를 훑어 찾아야 했다.
             신청 중·승인된 기관은 고를 수 없다(또 신청하면 서버 오류만 떴다) -->
        <q-select
          v-model="institutionId"
          outlined
          emit-value
          map-options
          use-input
          fill-input
          hide-selected
          input-debounce="0"
          :options="filteredOptions"
          :option-disable="(opt) => opt.disable"
          :loading="loadingInstitutions"
          label="신청 기관 *"
          @filter="filterInstitutions"
        >
          <template #no-option>
            <q-item><q-item-section class="text-grey-7">맞는 기관이 없습니다.</q-item-section></q-item>
          </template>
        </q-select>
        <q-input
          v-model="applicationMessage"
          outlined
          type="textarea"
          autogrow
          :input-style="{ minHeight: '80px' }"
          label="지원 사유 (선택)"
          maxlength="1000"
          counter
        />
        <q-btn
          color="primary"
          unelevated
          no-caps
          label="강사 신청하기"
          class="apply-card__submit"
          :loading="submitting"
          :disable="!isLoggedIn || !institutionId"
          @click="submitApply"
        />
      </section>

      <ApplyHistoryList :items="historyItems" :loading="loading" :error="errorMessage" />
    </div>
  </q-page>
</template>

<script>
import { qFormatDateTimeKst } from '@/utils/datetime';
import LearnerPageHead from '@/components/LearnerPageHead.vue';
import ApplyHistoryList from '@/components/apply/ApplyHistoryList.vue';
import ApplySteps from '@/components/apply/ApplySteps.vue';
import { mapState } from 'pinia';
import { listInstitutions } from '@/services/institution-api';
import { applyInstructor, listMyInstructorApplications } from '@/services/instructor-api';
import { useAuthStore } from '@/stores/auth-store';

// 기관 고르기 목록 — 글자로 거르니 넉넉히(서버 한 쪽 최대 100)
const INSTITUTION_PAGE_SIZE = 100;

export default {
  components: { ApplyHistoryList, ApplySteps, LearnerPageHead },
  name: 'InstructorApplyPage',
  data() {
    return {
      institutionId: null,
      applicationMessage: '',
      institutions: [],
      filterText: '',
      loadingInstitutions: false,
      loading: false,
      submitting: false,
      errorMessage: '',
      rows: [],
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'isInstructor']),
    /** 기관 id → 내 신청 상태(신청 중·승인) */
    myStatusByInstitution() {
      return this.rows.reduce((acc, row) => {
        if (row.status === 'PENDING' || row.status === 'APPROVED') return { ...acc, [row.institutionId]: row.status };
        return acc;
      }, {});
    },
    institutionOptions() {
      return this.institutions.map((item) => {
        const mine = this.myStatusByInstitution[item.id];
        const suffix = mine === 'PENDING' ? ' (신청 중)' : mine === 'APPROVED' ? ' (승인됨)' : '';
        return { label: `${item.name}${suffix}`, value: item.id, disable: !!mine };
      });
    },
    filteredOptions() {
      const q = this.filterText.trim().toLowerCase();
      return q ? this.institutionOptions.filter((opt) => opt.label.toLowerCase().includes(q)) : this.institutionOptions;
    },
    institutionNameById() {
      return Object.fromEntries(this.institutions.map((item) => [item.id, item.name]));
    },
    // 예전 표 첫 칸이 '기관 ID'(UUID)였다 — 기관 이름으로
    historyItems() {
      return this.rows.map((row) => ({
        id: row.id,
        title: this.institutionNameById[row.institutionId] || '기관',
        status: row.status,
        meta: [
          `${qFormatDateTimeKst(row.appliedAt)} 신청`,
          row.reviewedAt ? `${qFormatDateTimeKst(row.reviewedAt)} 처리` : '',
        ].filter(Boolean).join(' · '),
        note: this.noteOf(row),
      }));
    },
  },
  mounted() {
    this.loadInstitutions();
    this.loadMine();
  },
  methods: {
    filterInstitutions(value, update) {
      update(() => {
        this.filterText = value || '';
      });
    },
    noteOf(row) {
      if (row.status === 'REJECTED') return `거절 사유: ${row.rejectionReason || '사유 없음'}`;
      if (row.status === 'APPROVED') return '강사 콘솔을 쓸 수 있습니다. 메뉴에 보이지 않으면 다시 로그인해 주세요.';
      return row.applicationMessage ? `지원 사유: ${row.applicationMessage}` : '기관 관리자가 검토 중입니다.';
    },
    async loadInstitutions() {
      this.loadingInstitutions = true;
      try {
        const page = await listInstitutions({ page: 0, size: INSTITUTION_PAGE_SIZE, sort: 'name,asc' });
        this.institutions = page.data || [];
      } catch {
        this.institutions = [];
      } finally {
        this.loadingInstitutions = false;
      }
    },
    async loadMine() {
      if (!this.isLoggedIn) return;
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listMyInstructorApplications({ page: 0, size: 50, sort: 'appliedAt,desc' });
        this.rows = Array.isArray(page.data) ? page.data : [];
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '신청 이력을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async submitApply() {
      if (!this.institutionId) return;
      this.submitting = true;
      try {
        await applyInstructor(this.institutionId, {
          applicationMessage: this.applicationMessage.trim() || null,
        });
        this.applicationMessage = '';
        this.institutionId = null;
        this.$q.notify({ type: 'positive', message: '강사 신청이 접수되었습니다.', position: 'top' });
        await this.loadMine();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '신청에 실패했습니다.', position: 'top' });
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
}

.apply-note--ok {
  color: var(--db-status-green);
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

.apply-card__submit {
  align-self: flex-start;
  min-height: 44px;
  padding: 0 20px;
  border-radius: 6px;
}

@media (max-width: 599px) {
  .apply-card {
    padding: 16px;
  }

  .apply-card__submit {
    align-self: stretch;
  }
}
</style>
