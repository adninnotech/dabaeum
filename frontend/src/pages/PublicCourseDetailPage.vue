<template>
  <q-page class="pc-detail">
    <div class="learner-container pc-detail__inner">
      <router-link to="/courses" class="pc-detail__back" @click="goBack">
        <q-icon name="arrow_back" size="16px" aria-hidden="true" />강좌 찾기
      </router-link>

      <q-inner-loading :showing="loading">
        <q-spinner color="primary" size="32px" />
      </q-inner-loading>

      <AppEmptyState
        v-if="!loading && notFound"
        title="강좌를 찾을 수 없습니다."
        description="운영기관이 강좌 정보를 내렸거나 주소가 바뀌었습니다."
        action-label="강좌 찾기로"
        action-to="/courses"
      />
      <AppErrorBanner v-else-if="!loading && errorMessage" :message="errorMessage">
        <template #action>
          <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
        </template>
      </AppErrorBanner>

      <!-- 외부 기관 원문 — 모두 글자로만(v-html 금지) -->
      <article v-else-if="course" class="pc-detail__body">
        <header class="pc-detail__head">
          <div class="pc-detail__badges">
            <span class="pc-detail__source">대구 다른 기관 강좌</span>
            <StatusBadge v-if="methodLabel" :status="course.method" :label="methodLabel" size="sm" />
            <span v-if="regionLabel" class="pc-detail__chip">{{ regionLabel }}</span>
          </div>
          <h1 class="pc-detail__title">{{ course.lectureName || '이름 없는 강좌' }}</h1>
          <div class="pc-detail__org">{{ course.operInstitutionName || course.insttName || '운영기관 정보 없음' }}</div>
        </header>

        <p v-if="course.active === false" class="pc-detail__inactive" role="status">
          <q-icon name="info" size="18px" aria-hidden="true" />
          모집 정보가 더 이상 제공되지 않습니다. 신청 전 운영기관에 확인해 주세요.
        </p>

        <div class="pc-detail__layout">
          <section class="pc-detail__card" aria-labelledby="pc-info-title">
            <h2 id="pc-info-title" class="pc-detail__card-title">강좌 정보</h2>
            <dl class="pc-detail__facts">
              <div v-for="fact in facts" :key="fact.label" class="pc-detail__fact">
                <dt>{{ fact.label }}</dt>
                <dd>{{ fact.value }}</dd>
              </div>
            </dl>
          </section>

          <aside class="pc-detail__card pc-detail__apply" aria-labelledby="pc-apply-title">
            <h2 id="pc-apply-title" class="pc-detail__card-title">신청</h2>
            <div class="pc-detail__receipt" :class="`pc-detail__receipt--${receipt.tone}`">{{ receipt.text }}</div>
            <q-btn
              v-if="homepageUrl"
              :unelevated="receipt.tone !== 'closed'"
              :outline="receipt.tone === 'closed'"
              no-caps
              color="primary"
              class="pc-detail__cta"
              :href="homepageUrl"
              target="_blank"
              rel="noopener noreferrer"
              icon-right="open_in_new"
              :label="receipt.tone === 'closed' ? '운영기관 홈페이지' : '운영기관에서 신청'"
              :aria-label="`${receipt.tone === 'closed' ? '운영기관 홈페이지' : '운영기관 홈페이지에서 신청'} (새 창)`"
            />
            <q-btn
              v-else-if="phoneHref"
              outline
              no-caps
              color="primary"
              class="pc-detail__cta"
              :href="phoneHref"
              icon="call"
              :label="`문의 ${course.operPhone}`"
            />
            <p class="pc-detail__note">
              다배움에서 신청·출석·수료를 다루지 않는 외부 강좌입니다.
              {{ homepageUrl || phoneHref ? '' : '운영기관에 직접 문의해 주세요.' }}
            </p>
          </aside>
        </div>

        <section v-if="course.lectureContent" class="pc-detail__card" aria-labelledby="pc-content-title">
          <h2 id="pc-content-title" class="pc-detail__card-title">강좌 내용</h2>
          <p class="pc-detail__content">{{ course.lectureContent }}</p>
        </section>

        <p v-if="course.referenceDate" class="pc-detail__source-note">
          출처: 공공데이터포털 전국평생학습강좌표준데이터 · 기준일 {{ formatDateKst(course.referenceDate) }}
        </p>
      </article>
    </div>
  </q-page>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { getPublicCourse } from '@/services/public-course-api';
import { formatDateKst } from '@/utils/datetime';
import {
  publicCostLabel,
  publicMethodLabel,
  publicPeriodLabel,
  publicReceiptLabel,
  publicReceiptState,
  publicRegionLabel,
  publicScheduleLabel,
  safeHomepageUrl,
} from '@/utils/public-course';

/** 전화번호에서 숫자·+ 만 남겨 tel: 링크로 — 숫자가 너무 적으면 링크로 만들지 않는다 */
function telHref(phone) {
  const digits = String(phone || '').replace(/[^\d+]/g, '');
  return digits.length >= 7 ? `tel:${digits}` : '';
}

export default {
  name: 'PublicCourseDetailPage',
  components: { AppEmptyState, AppErrorBanner, StatusBadge },
  data() {
    return {
      course: null,
      loading: false,
      notFound: false,
      errorMessage: '',
    };
  },
  computed: {
    courseId() {
      return this.$route.params.courseId;
    },
    methodLabel() {
      return publicMethodLabel(this.course?.method);
    },
    regionLabel() {
      return publicRegionLabel(this.course?.regionCode);
    },
    homepageUrl() {
      return safeHomepageUrl(this.course?.homepageUrl);
    },
    phoneHref() {
      return telHref(this.course?.operPhone);
    },
    receipt() {
      return publicReceiptState(this.course);
    },
    /** 값이 있는 칸만 — 외부 원문이라 빈 칸이 많다 */
    facts() {
      const c = this.course || {};
      const place = [c.place, c.roadAddress].filter(Boolean).join(' · ');
      return [
        { label: '교육 기간', value: publicPeriodLabel(c) },
        { label: '요일·시간', value: publicScheduleLabel(c) },
        { label: '장소', value: place },
        { label: '교육 대상', value: (c.targetTypes || []).join(', ') },
        { label: '정원', value: Number.isFinite(c.capacity) ? `${c.capacity}명` : '' },
        { label: '수강료', value: publicCostLabel(c) },
        { label: '강사', value: c.instructorName },
        { label: '접수 기간', value: publicReceiptLabel(c) },
        { label: '접수 방법', value: c.receiptMethod },
        { label: '선정 방법', value: c.selectionMethod },
        { label: '문의', value: c.operPhone },
        { label: '학습계좌', value: c.learningAccount === true ? '인정' : '' },
        { label: '학점은행', value: c.creditBank },
        { label: '교육비 지원', value: c.trainingSubsidy },
      ].filter((fact) => fact.value);
    },
  },
  watch: {
    courseId: 'load',
  },
  created() {
    this.load();
  },
  methods: {
    formatDateKst,
    /** 강좌 찾기에서 왔으면 뒤로 — 검색어·더보기 위치가 남는다. 바로 들어왔으면 링크대로 /courses */
    goBack(event) {
      if (!window.history.state?.back) return;
      event.preventDefault();
      this.$router.back();
    },
    async load() {
      this.loading = true;
      this.notFound = false;
      this.errorMessage = '';
      try {
        this.course = await getPublicCourse(this.courseId);
      } catch (error) {
        this.course = null;
        if (error?.status === 404) this.notFound = true;
        else this.errorMessage = error?.message || '강좌 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.pc-detail {
  position: relative;
  padding: 24px 0 48px;
  background: var(--db-bg-page);
}

.pc-detail__inner {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 240px;
}

.pc-detail__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.pc-detail__back:hover,
.pc-detail__back:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}

.pc-detail__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pc-detail__head {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pc-detail__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.pc-detail__source,
.pc-detail__chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
}

.pc-detail__source {
  font-weight: 700;
  color: var(--db-accent);
  background: var(--db-accent-soft);
}

.pc-detail__chip {
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
}

.pc-detail__title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--db-text-title);
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.pc-detail__org {
  font-size: 15px;
  color: var(--db-text-sub);
}

.pc-detail__inactive {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--db-status-orange-text);
  background: color-mix(in srgb, var(--db-status-orange) 12%, var(--db-bg-white));
  word-break: keep-all;
}

.pc-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.pc-detail__card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pc-detail__card-title {
  margin: 0;
  font-size: 16px;
  line-height: 1.4;
  font-weight: 700;
  color: var(--db-text-title);
}

.pc-detail__facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  margin: 0;
}

.pc-detail__fact {
  display: flex;
  gap: 12px;
  min-width: 0;
  font-size: 14px;
}

.pc-detail__fact dt {
  flex-shrink: 0;
  width: 72px;
  color: var(--db-text-sub);
}

.pc-detail__fact dd {
  margin: 0;
  min-width: 0;
  color: var(--db-text-body);
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.pc-detail__apply {
  position: sticky;
  /* 머리 68px + 20px 여백 — 본문 zoom 만큼 나눠 화면에서도 88px */
  top: calc(88px / var(--db-content-zoom, 1));
}

.pc-detail__receipt {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-sub);
}

.pc-detail__receipt--open {
  color: var(--db-status-green-text);
}

.pc-detail__receipt--soon {
  color: var(--db-status-pink);
}

.pc-detail__cta {
  height: 48px;
  font-size: 16px;
}

.pc-detail__note {
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pc-detail__content {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--db-text-body);
  white-space: pre-line;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.pc-detail__source-note {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-help);
}

@media (max-width: 1023px) {
  .pc-detail__layout {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 폰·태블릿은 신청을 맨 위로 — 긴 정보 표 아래에 묻히지 않게 */
  .pc-detail__apply {
    position: static;
    order: -1;
  }
}

@media (max-width: 599px) {
  .pc-detail {
    padding-top: 16px;
  }

  .pc-detail__title {
    font-size: 22px;
  }

  .pc-detail__facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .pc-detail__card {
    padding: 16px;
  }
}
</style>
