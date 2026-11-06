<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body support-body">
      <!-- 학습자 화면 머리(h1) — 콘솔용 app-page-header 를 걷어냈다 -->
      <LearnerPageHead title="고객센터" subtitle="자주 묻는 질문을 먼저 보고, 해결되지 않으면 문의를 남겨 주세요." />

      <!-- 공지 화면은 머리 메뉴 '고객센터' 아래인데 여기서 가는 길이 없었다(홈 아래쪽 [전체 보기]뿐) — 최근 공지 + 전체 보기 -->
      <section class="support-card relative-position" aria-labelledby="support-notice-title">
        <div class="support-card__head">
          <h2 id="support-notice-title" class="support-title">공지사항</h2>
          <router-link to="/notices" class="support-link">전체 보기 &gt;</router-link>
        </div>
        <q-inner-loading :showing="loadingNotices" />
        <ul v-if="notices.length" class="support-notices">
          <li v-for="notice in notices" :key="notice.id">
            <router-link :to="`/notices/${notice.id}`" class="support-notice">
              <span class="support-notice__title">{{ notice.title }}</span>
              <span class="support-notice__date">{{ formatDateKst(notice.publishedAt || notice.createdAt, '-') }}</span>
            </router-link>
          </li>
        </ul>
        <AppEmptyState v-else-if="!loadingNotices" compact description="등록된 공지사항이 없습니다." />
      </section>

      <!-- 제목이 없어 목록이 머리에 붙어 보였다 — 아래 '문의하기'와 같은 무게의 구역으로 -->
      <section class="support-card relative-position" aria-labelledby="support-faq-title">
        <h2 id="support-faq-title" class="support-title">자주 묻는 질문</h2>
        <q-inner-loading :showing="loadingFaqs" />
        <!-- 홈 FAQ 와 같은 모양(FaqAccordion) — 홈 [전체 보기]로 오면 같은 FAQ 가 다르게 보였다 -->
        <FaqAccordion v-if="faqs.length" :faqs="faqs" id-prefix="support-faq" />
        <AppEmptyState v-else-if="!loadingFaqs" compact description="등록된 FAQ가 없습니다." />
      </section>

      <section class="support-card" aria-labelledby="support-ask-title">
        <div class="support-card__head">
          <h2 id="support-ask-title" class="support-title">문의하기</h2>
          <router-link v-if="isLoggedIn" :to="INQUIRIES_PATH" class="support-link">내 문의 보기 &gt;</router-link>
        </div>
        <!-- 쓰기 전에 알린다 — 답을 어디서 보는지, 강좌 질문은 어디로 가야 강사에게 바로 닿는지 -->
        <ul class="support-guide">
          <li>답변은 <strong>학습관리 &gt; 나의 문의</strong>에서 확인합니다.</li>
          <li>특정 강좌에 대한 질문은 강좌 상세의 <strong>Q&amp;A</strong>에 남겨 주세요. 강사에게 바로 전달됩니다.</li>
        </ul>

        <template v-if="!isLoggedIn">
          <!-- 로그인 안 한 채로 다 쓰고 등록을 누르면 로그인으로 넘어가며 쓴 내용이 사라졌다 — 쓰기 전에 알린다 -->
          <p class="support-note">문의는 로그인 후 남길 수 있습니다.</p>
          <q-btn
            class="support-submit"
            color="primary"
            unelevated
            no-caps
            label="로그인하고 문의하기"
            :to="{ path: '/login', query: { redirect: '/support' } }"
          />
        </template>
        <template v-else>
          <!-- 빈 칸은 알림 대신 칸 아래에 — 어느 칸인지 바로 보이게 -->
          <q-input
            v-model="subject"
            outlined
            dense
            label="제목 *"
            :maxlength="SUBJECT_MAX"
            :error="showErrors && !subject.trim()"
            error-message="제목을 입력해 주세요."
            no-error-icon
            class="support-field"
          />
          <!-- 늘어나는 칸이지만 처음엔 제목 칸과 같은 한 줄이라 짧은 글을 쓰는 칸처럼 보였다 -->
          <q-input
            v-model="message"
            outlined
            type="textarea"
            label="내용 *"
            counter
            :input-style="{ minHeight: '120px' }"
            autogrow
            :error="showErrors && !message.trim()"
            error-message="내용을 입력해 주세요."
            no-error-icon
            class="support-field"
          />
          <q-btn
            class="support-submit"
            color="primary"
            unelevated
            no-caps
            label="문의 등록"
            :loading="submitting"
            @click="submitInquiry"
          />
          <!-- 등록 후 답변을 어디서 보는지 바로 이어 준다 -->
          <p v-if="submitted" class="support-done" role="status">
            문의가 등록되었습니다. 답변은
            <router-link :to="INQUIRIES_PATH" class="support-done__link">학습관리 &gt; 나의 문의</router-link>
            에서 확인할 수 있습니다.
          </p>
        </template>
      </section>
    </div>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import AppEmptyState from '@/components/AppEmptyState.vue';
import FaqAccordion from '@/components/FaqAccordion.vue';
import LearnerPageHead from '@/components/LearnerPageHead.vue';
import { createInquiry } from '@/services/inquiry-api';
import { listNotices } from '@/services/notice-api';
import { listFaqs } from '@/services/support-api';
import { useAuthStore } from '@/stores/auth-store';

const INQUIRIES_PATH = '/learning?tab=inquiries';
// 제목 상한은 계약(InquiryCreateRequest.title maxLength 200)과 같게. 내용은 상한이 없어 글자 수만 센다
const SUBJECT_MAX = 200;
const NOTICE_PREVIEW = 3;

export default {
  components: { AppEmptyState, FaqAccordion, LearnerPageHead },
  name: 'SupportPage',
  data() {
    return {
      INQUIRIES_PATH,
      SUBJECT_MAX,
      showErrors: false,
      submitted: false,
      subject: '',
      message: '',
      faqs: [],
      loadingFaqs: false,
      notices: [],
      loadingNotices: false,
      submitting: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn']),
  },
  mounted() {
    this.loadFaqs();
    this.loadNotices();
  },
  methods: {
    // 홈 공지 카드와 같은 조건(공개 대상 최근 공지)
    async loadNotices() {
      this.loadingNotices = true;
      try {
        const page = await listNotices({ page: 0, size: NOTICE_PREVIEW, audience: 'PUBLIC' });
        this.notices = page.data || [];
      } catch {
        this.notices = [];
      } finally {
        this.loadingNotices = false;
      }
    },
    async loadFaqs() {
      this.loadingFaqs = true;
      try {
        const page = await listFaqs({ page: 0, size: 50 });
        this.faqs = page.data || [];
      } catch {
        this.faqs = [];
      } finally {
        this.loadingFaqs = false;
      }
    },
    async submitInquiry() {
      // 비로그인이면 폼 대신 로그인 안내가 보이므로 여기 올 일은 없다 — 세션 만료 대비로만 막는다
      if (!this.isLoggedIn) {
        this.$q.notify({ type: 'warning', message: '로그인 후 문의를 등록할 수 있습니다.', position: 'top' });
        return;
      }
      this.submitted = false;
      if (!this.subject.trim() || !this.message.trim()) {
        this.showErrors = true;
        return;
      }
      this.showErrors = false;
      this.submitting = true;
      try {
        await createInquiry({ title: this.subject.trim(), content: this.message.trim() });
        this.subject = '';
        this.message = '';
        this.submitted = true;
        this.$q.notify({
          type: 'positive',
          message: '문의가 등록되었습니다. 답변은 학습관리 > 나의 문의에서 확인할 수 있습니다.',
          position: 'top',
          actions: [
            { label: '나의 문의 보기', color: 'white', handler: () => this.$router.push(INQUIRIES_PATH) },
          ],
        });
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '문의 등록에 실패했습니다.', position: 'top' });
      } finally {
        this.submitting = false;
      }
    },
  },
};
</script>

<style scoped>
.support-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.support-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 12px;
}

.support-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.support-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--db-text-title);
}

.support-notices {
  margin: 0;
  padding: 0;
  list-style: none;
}

.support-notices li + li {
  border-top: 1px solid var(--db-border-light);
}

.support-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: inherit;
  text-decoration: none;
}

.support-notice:hover .support-notice__title {
  color: var(--db-accent);
}

.support-notice:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.support-notice__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  color: var(--db-text-title);
}

.support-notice__date {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.support-link {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  flex-shrink: 0;
  font-size: 14px;
  white-space: nowrap;
  color: var(--db-accent);
  text-decoration: none;
}

.support-link:hover,
.support-link:focus-visible {
  text-decoration: underline;
}

.support-guide {
  margin: 0;
  padding: 12px 12px 12px 28px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  border-radius: 8px;
  word-break: keep-all;
}

.support-guide strong {
  font-weight: 600;
  color: var(--db-text-title);
}

.support-field + .support-field {
  margin-top: 4px;
}

.support-submit {
  align-self: flex-start;
  border-radius: 6px;
}

.support-note {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.support-done {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.support-done__link {
  color: var(--db-accent);
  font-weight: 600;
}

@media (max-width: 599px) {
  .support-card {
    padding: 16px;
  }

  .support-submit {
    align-self: stretch;
  }
}
</style>
