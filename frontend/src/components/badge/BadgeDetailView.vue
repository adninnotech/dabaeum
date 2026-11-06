<template>
  <!-- NFT 학습배지 현황 상세(설계서 §11.4) — 관리자·학습자 공통. 배지 단계·이수기준·이름·발급기관·발급일시 +
       변동 이력(발급·갱신·취소·복구와 사유) + 학생 수강 내역을 한 화면에 -->
  <div class="badge-detail">
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>

    <template v-if="badge">
      <!-- 머리: 배지·단계·상태 -->
      <section class="bd-hero" aria-labelledby="bd-title">
        <BadgeMedal
          :image-url="badge.imageUrl || ''"
          :alt="badge.badgeName"
          :size="96"
          :muted="badge.status === 'REVOKED'"
        />
        <div class="bd-hero__text">
          <p class="bd-hero__issuer">{{ badge.issuerName }} 발급</p>
          <h1 id="bd-title" class="bd-hero__name">{{ badge.badgeName }}</h1>
          <div class="bd-hero__chips">
            <span v-if="tierLabel(badge)" class="bd-tier">{{ tierLabel(badge) }}</span>
            <StatusBadge :label="statusView.label" :color="statusView.color" />
          </div>
        </div>
        <!-- 관리자만, 유지(ISSUED) 중일 때만 — 폐기하면 조건을 다시 채워도 자동으로 돌아오지 않는다 -->
        <q-btn
          v-if="audience === 'admin' && badge.status === 'ISSUED'"
          outline
          no-caps
          color="negative"
          icon="o_block"
          label="배지 폐기"
          class="bd-hero__action"
          @click="revokeOpen = true"
        />
      </section>

      <CorrectionReasonDialog
        v-model="revokeOpen"
        title="학습배지 폐기"
        :target="revokeTarget"
        :notes="REVOKE_NOTES"
        confirm-label="폐기"
        confirm-color="negative"
        reason-label="폐기 사유"
        reason-hint="변동 이력에 사유와 처리한 관리자가 남습니다."
        :reason-max="REVOKE_REASON_MAX"
        :action="runRevoke"
        :error-message="revokeErrorMessage"
        @done="onRevoked"
      />

      <!-- 단계 사다리 — 정의의 단계표가 있을 때만 -->
      <ol v-if="tierLevels.length" class="bd-ladder" aria-label="배지 단계">
        <li
          v-for="(level, index) in tierLevels"
          :key="level.level"
          class="bd-ladder__step"
          :class="{
            'bd-ladder__step--done': badge.tierLevel && level.level < badge.tierLevel,
            'bd-ladder__step--current': level.level === badge.tierLevel,
          }"
          :aria-current="level.level === badge.tierLevel ? 'step' : undefined"
        >
          <span class="bd-ladder__dot" aria-hidden="true">{{ level.level }}</span>
          <span class="bd-ladder__name">{{ level.name }}</span>
          <span class="bd-ladder__range">{{ tierRangeText(tierLevels, index) }}</span>
        </li>
      </ol>

      <!-- 정보 -->
      <section class="bd-card bd-card--info" aria-labelledby="bd-info-title">
        <h2 id="bd-info-title" class="bd-card__title">배지 정보</h2>
        <dl class="bd-info">
          <div class="bd-info__row">
            <dt>배지 단계</dt>
            <dd>{{ tierLabel(badge) || '단계 없는 배지' }}</dd>
          </div>
          <div class="bd-info__row">
            <dt>발급기관</dt>
            <dd>{{ badge.issuerName }}</dd>
          </div>
          <div class="bd-info__row bd-info__row--wide">
            <dt>이수기준</dt>
            <dd>
              <!-- 단계표는 위 사다리가 보여 준다 — 여기선 무엇을 세는지만 -->
              <template v-if="tierLevels.length">강좌 이수 건수에 따라 단계가 정해집니다 · 단계별 건수는 위 단계표</template>
              <BadgeCriteria
                v-else
                :rule="definitionRule"
                :fallback="badge.criteria || ''"
                :classification-tree="classificationTree"
              />
            </dd>
          </div>
          <div class="bd-info__row">
            <dt>이름</dt>
            <!-- 관리자는 받은 사람의 회원 상세로 바로 간다 -->
            <dd v-if="audience === 'admin' && badge.userId">
              <RouterLink :to="`/admin/students/${badge.userId}`" class="bd-link">
                {{ badge.recipientName || '이름 정보 없음' }}
              </RouterLink>
            </dd>
            <dd v-else>{{ badge.recipientName || '이름 정보 없음' }}</dd>
          </div>
          <div class="bd-info__row">
            <dt>발급일시</dt>
            <dd class="bd-num">{{ formatDateMinuteKst(badge.issuedAt, '발급 전') }}</dd>
          </div>
          <div class="bd-info__row">
            <dt>유효기간</dt>
            <dd>{{ badge.expiresAt ? `${formatDateKst(badge.expiresAt)}까지` : '만료 없음' }}</dd>
          </div>
          <!-- 원장 기록 값이 실제로 있을 때만 보인다 — "준비 중·NFT·tokenId" 같은 말은 학습자에게 뜻이 없다.
               긴 값은 앞뒤만 보이고 전체는 title 로 -->
          <div v-if="badge.nftTokenId" class="bd-info__row bd-info__row--wide">
            <dt>위변조 방지 기록</dt>
            <dd>
              <code class="bd-code" :title="String(badge.nftTokenId)">{{ shortTokenId }}</code>
            </dd>
          </div>
        </dl>
      </section>

      <!-- 변동 이력 -->
      <section class="bd-card bd-card--history" aria-labelledby="bd-history-title">
        <h2 id="bd-history-title" class="bd-card__title">
          변동 이력 <span class="bd-card__count">{{ history.length }}건</span>
        </h2>
        <p v-if="historyError" class="bd-muted" role="alert">{{ historyError }}</p>
        <ol v-else-if="history.length" class="bd-history">
          <li v-for="event in history" :key="event.id" class="bd-history__item">
            <span class="bd-event" :class="`bd-event--${eventTone(event.type)}`">
              {{ BADGE_EVENT_LABEL[event.type] || event.type }}
            </span>
            <div class="bd-history__body">
              <p class="bd-history__head">
                <span v-if="badgeEventLevelText(event)" class="bd-history__level">{{ badgeEventLevelText(event) }}</span>
                <span class="bd-history__reason">{{ event.reason }}</span>
              </p>
              <p class="bd-history__meta">
                {{ formatDateMinuteKst(event.occurredAt) }} · {{ event.actorId ? '관리자 처리' : '자동 처리' }}
              </p>
            </div>
          </li>
        </ol>
        <p v-else class="bd-muted">변동 이력이 없습니다.</p>
      </section>

      <!-- 수강 내역 -->
      <section class="bd-card" aria-labelledby="bd-enroll-title">
        <!-- 본인 화면은 배지 단계에 들어간 수료 강좌만 — 취소한 신청까지 섞여 배지와 상관없는 줄이 보였다.
             관리자는 전체 수강 내역(이수 여부 판단용) -->
        <h2 id="bd-enroll-title" class="bd-card__title">
          <template v-if="audience === 'self'">
            이수한 강좌 <span class="bd-card__count">{{ completedCount }}건</span>
          </template>
          <template v-else>
            수강 내역 <span class="bd-card__count">{{ enrollments.length }}건 · 수료 {{ completedCount }}건</span>
          </template>
        </h2>
        <p v-if="enrollmentsError" class="bd-muted" role="alert">{{ enrollmentsError }}</p>
        <!-- 폰: 표가 옆으로 밀려 상태·신청일이 화면 밖이었다 — 한 줄씩 -->
        <ul v-else-if="shownEnrollments.length && $q.screen.xs" class="bd-enroll-list">
          <li v-for="row in shownEnrollments" :key="row.id" class="bd-enroll-item">
            <div class="bd-enroll-item__head">
              <span class="bd-table__title bd-enroll-item__title">{{ row.courseTitle }}</span>
              <StatusBadge v-bind="completionBadge(row)" />
            </div>
            <div class="bd-enroll-item__meta">
              {{ row.institutionName }} · 신청 {{ formatDateKst(row.appliedAt) }}
            </div>
          </li>
        </ul>
        <div v-else-if="shownEnrollments.length" class="bd-scroll">
          <table class="bd-table">
            <thead>
              <tr>
                <th scope="col">강좌</th>
                <th scope="col">기관</th>
                <!-- 이수 기록이 없으면 수강 상태(취소 등)가 나온다 — '이수' 칸에 '취소'가 보였다 -->
                <th scope="col">상태</th>
                <th scope="col">신청일</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in shownEnrollments" :key="row.id">
                <td class="bd-table__title">{{ row.courseTitle }}</td>
                <td>{{ row.institutionName }}</td>
                <!-- 배지는 이수 건수로 정해진다 — 강좌 모집 상태 대신 이 수강의 이수 여부를 보인다 -->
                <td><StatusBadge v-bind="completionBadge(row)" /></td>
                <td class="bd-num">{{ formatDateKst(row.appliedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="bd-muted">{{ audience === 'self' ? '아직 이수한 강좌가 없습니다.' : '수강 내역이 없습니다.' }}</p>
      </section>
    </template>
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import BadgeCriteria from '@/components/badge/BadgeCriteria.vue';
import CorrectionReasonDialog from '@/components/console/CorrectionReasonDialog.vue';
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listBadgeDefinitions } from '@/services/badge-api';
import { listCourseClassifications } from '@/services/classification-api';
import { getBadge, listBadgeHistory, revokeLearningBadge } from '@/services/credential-api';
import { getCompletion } from '@/services/completion-api';
import { listEnrollmentsOfUser, listMyEnrollments } from '@/services/enrollment-api';
import { listLearningCourses } from '@/services/learner-api';
import { tierRangeText } from '@/utils/badge-rule';
import { BADGE_EVENT_LABEL, badgeEventLevelText, badgeStatusView, tierLabel } from '@/utils/badge-status';
import { formatDateKst, formatDateMinuteKst } from '@/utils/datetime';

const ENROLLMENT_PAGE_SIZE = 100;
const REVOKE_REASON_MAX = 200;
const REVOKE_NOTES = [
  '폐기한 배지는 학습자가 조건을 다시 채워도 자동으로 돌아오지 않습니다.',
  '학습자의 수강·수료 기록은 그대로 남습니다.',
];

export default {
  name: 'BadgeDetailView',
  components: { AppErrorBanner, BadgeCriteria, BadgeMedal, CorrectionReasonDialog, StatusBadge },
  props: {
    badgeId: { type: String, required: true },
    /** admin: GET /users/{userId}/enrollments, self: GET /users/me/enrollments */
    audience: { type: String, default: 'self', validator: (value) => ['admin', 'self'].includes(value) },
  },
  emits: ['loaded'],
  data() {
    return {
      BADGE_EVENT_LABEL,
      REVOKE_NOTES,
      REVOKE_REASON_MAX,
      revokeOpen: false,
      loading: false,
      errorMessage: '',
      badge: null,
      history: [],
      historyError: '',
      enrollments: [],
      enrollmentsError: '',
      // enrollmentId → 이수 상태(COMPLETED·ELIGIBLE 등). 없으면 이수 기록 없음
      completionByEnrollment: {},
      tierLevels: [],
      definitionRule: null,
      classificationTree: [],
    };
  },
  computed: {
    shownEnrollments() {
      if (this.audience !== 'self') return this.enrollments;
      return this.enrollments.filter((row) => this.completionByEnrollment[row.id] === 'COMPLETED');
    },
    completedCount() {
      return Object.values(this.completionByEnrollment).filter((status) => status === 'COMPLETED').length;
    },
    statusView() {
      return badgeStatusView(this.badge);
    },
    /** 긴 기록 값은 앞 8·뒤 6자만 — 확인용 식별에는 충분하고 줄을 넘기지 않는다 */
    shortTokenId() {
      const value = String(this.badge?.nftTokenId || '');
      return value.length > 18 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value;
    },
    revokeTarget() {
      if (!this.badge) return '';
      const who = this.badge.recipientName || '';
      const what = [this.badge.badgeName, tierLabel(this.badge)].filter(Boolean).join(' ');
      return [who, what].filter(Boolean).join(' — ');
    },
  },
  watch: {
    badgeId: { immediate: true, handler: 'load' },
  },
  methods: {
    formatDateKst,
    formatDateMinuteKst,
    tierLabel,
    tierRangeText,
    badgeEventLevelText,
    runRevoke(reason) {
      return revokeLearningBadge(this.badgeId, reason);
    },
    revokeErrorMessage(error) {
      if (error?.payload?.code === 'BADGE_CONFLICT') return '이미 폐기된 배지입니다. 화면을 새로 고쳐 주세요.';
      if (error?.status === 422) return '폐기 사유를 1~200자로 입력해 주세요.';
      return error?.message || '배지를 폐기하지 못했습니다.';
    },
    onRevoked() {
      this.$q.notify({ type: 'positive', message: '배지를 폐기했습니다.', position: 'top' });
      this.load();
    },
    eventTone(type) {
      return { ISSUED: 'ok', RENEWED: 'info', REVOKED: 'bad', RESTORED: 'ok' }[type] || 'info';
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      this.badge = null;
      try {
        const [badge, history] = await Promise.allSettled([getBadge(this.badgeId), listBadgeHistory(this.badgeId)]);
        if (badge.status === 'rejected') throw badge.reason;
        this.badge = badge.value;
        this.$emit('loaded', this.badge);
        this.history = history.status === 'fulfilled' ? history.value : [];
        this.historyError = history.status === 'rejected' ? history.reason?.message || '변동 이력을 불러오지 못했습니다.' : '';
        await Promise.all([this.loadEnrollments(), this.loadTierLevels()]);
      } catch (error) {
        this.errorMessage =
          error?.status === 404 ? '배지를 찾을 수 없습니다.' : error?.message || '배지 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadEnrollments() {
      this.enrollmentsError = '';
      try {
        const page =
          this.audience === 'admin'
            ? await listEnrollmentsOfUser(this.badge.userId, { size: ENROLLMENT_PAGE_SIZE })
            : await listMyEnrollments({ size: ENROLLMENT_PAGE_SIZE });
        this.enrollments = page.data || [];
        this.completionByEnrollment = await this.loadCompletions(this.enrollments);
      } catch (error) {
        this.enrollments = [];
        this.enrollmentsError = error?.message || '수강 내역을 불러오지 못했습니다.';
      }
    },
    /** 본인: 학습 목록 한 번(enrollmentId·status). 관리자: 승인된 수강만 이수 기록을 조회(404 = 아직 없음) */
    async loadCompletions(enrollments) {
      if (this.audience === 'self') {
        const page = await listLearningCourses({ size: ENROLLMENT_PAGE_SIZE }).catch(() => ({ data: [] }));
        return (page.data || []).reduce(
          (acc, row) => (row.enrollmentId ? { ...acc, [row.enrollmentId]: row.status } : acc),
          {},
        );
      }
      const approved = enrollments.filter((row) => row.status === 'APPROVED');
      const results = await Promise.allSettled(approved.map((row) => getCompletion(row.id)));
      return results.reduce(
        (acc, result, index) =>
          result.status === 'fulfilled' && result.value?.status
            ? { ...acc, [approved[index].id]: result.value.status }
            : acc,
        {},
      );
    },
    completionBadge(row) {
      const status = this.completionByEnrollment[row.id];
      if (status === 'COMPLETED') return { status: 'COMPLETED', label: '수료' };
      if (status === 'ELIGIBLE') return { status: 'APPROVED', label: '이수 가능' };
      // 이수 기록이 없으면 수강 단계(신청·승인·취소 등)를 그대로
      return { status: row.status };
    },
    // 정의 규칙(단계표·분야)을 받아 기준을 코드 없이 그린다 — 실패하면 서버 criteria 문장
    async loadTierLevels() {
      this.tierLevels = [];
      this.definitionRule = null;
      if (!this.badge?.definitionId) return;
      const [definitions, tree] = await Promise.allSettled([listBadgeDefinitions(), listCourseClassifications()]);
      if (tree.status === 'fulfilled') this.classificationTree = tree.value;
      if (definitions.status !== 'fulfilled') return;
      const definition = definitions.value.find((item) => item.id === this.badge.definitionId);
      this.definitionRule = definition?.rule || null;
      this.tierLevels = this.badge.tierLevel && definition?.rule?.type === 'TIERED' ? definition.rule.levels || [] : [];
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: badge detail · genre: modern-minimal · theme: system-managed (--db-* 토큰)
 * states: loading · error(재시도) · empty(이력·수강 없음) · revoked(흐림) · 부분 실패(이력/수강만 안내)
 * pre-emit critique: P4 H5 E4 S5 R4 V4
 */
.badge-detail {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
  container-type: inline-size;
}

/* 넓은 자리(관리자 콘솔 등) — 배지 정보와 변동 이력을 나란히. 한 줄로 쌓으면 카드가 다 짧아 오른쪽이 텅 비었다.
 * 머리·단계·수강 내역은 줄 전체 */
@container (min-width: 1040px) {
  .badge-detail {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }

  .badge-detail > * {
    grid-column: 1 / -1;
  }

  .badge-detail > .bd-card--info {
    grid-column: 1;
  }

  .badge-detail > .bd-card--history {
    grid-column: 2;
    align-self: stretch;
  }

  /* 반쪽 폭이라 정보는 한 줄에 하나 */
  .bd-card--info .bd-info {
    grid-template-columns: minmax(0, 1fr);
  }
}

.bd-hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.bd-hero__action {
  margin-left: auto;
  align-self: flex-start;
}

.bd-hero__text {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.bd-hero__issuer {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.bd-hero__name {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.bd-hero__chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bd-tier {
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--db-accent-soft);
  font-size: 14px;
  font-weight: 700;
  color: var(--db-primary);
}

/* 단계 사다리 */
.bd-ladder {
  display: grid;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: 4px;
  margin: 0;
  padding: 16px;
  list-style: none;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.bd-ladder__step {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
  text-align: center;
}

/* 단계를 잇는 줄 */
.bd-ladder__step:not(:first-child)::before {
  content: '';
  position: absolute;
  top: 14px;
  right: 50%;
  width: 100%;
  height: 2px;
  background: var(--db-border);
}

.bd-ladder__step--done:not(:first-child)::before,
.bd-ladder__step--current:not(:first-child)::before {
  background: var(--db-primary);
}

.bd-ladder__dot {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 2px solid var(--db-border);
  border-radius: 50%;
  background: var(--db-bg-white);
  font-size: 13px;
  font-weight: 700;
  color: var(--db-text-sub);
}

.bd-ladder__step--done .bd-ladder__dot {
  border-color: var(--db-primary);
  color: var(--db-primary);
}

.bd-ladder__step--current .bd-ladder__dot {
  border-color: var(--db-primary);
  background: var(--db-primary);
  color: var(--db-on-color);
}

.bd-ladder__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
}

.bd-ladder__step--current .bd-ladder__name {
  color: var(--db-primary);
  font-weight: 700;
}

.bd-ladder__range {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 카드 */
.bd-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.bd-card__title {
  margin: 0;
  font-size: 17px;
  line-height: 24px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
}

.bd-card__count {
  margin-left: 4px;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.bd-info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 24px;
  margin: 0;
}

.bd-info__row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--db-border-light);
}

.bd-info__row--wide {
  grid-column: 1 / -1;
}

.bd-info dt {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.bd-info dd {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.bd-num {
  font-variant-numeric: tabular-nums;
}

.bd-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.bd-muted {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

/* 변동 이력 */
.bd-history {
  margin: 0;
  padding: 0;
  list-style: none;
}

.bd-history__item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--db-border-light);
}

.bd-event {
  flex-shrink: 0;
  align-self: flex-start;
  min-width: 44px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 700;
  text-align: center;
  color: var(--db-on-color);
}

.bd-event--ok {
  background: var(--db-status-green);
}

.bd-event--info {
  background: var(--db-status-blue);
}

.bd-event--bad {
  background: var(--db-status-pink);
}

.bd-history__body {
  min-width: 0;
}

.bd-history__head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 0;
  font-size: 14px;
  color: var(--db-text-title);
}

.bd-history__level {
  font-weight: 700;
}

.bd-history__reason {
  overflow-wrap: anywhere;
}

.bd-history__meta {
  margin: 2px 0 0;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 수강 내역 */
.bd-scroll {
  overflow-x: auto;
}

.bd-table {
  width: 100%;
  min-width: 560px;
  border-collapse: collapse;
  font-size: 14px;
}

.bd-table th {
  padding: 8px 10px;
  border-bottom: 1px solid var(--db-border);
  background: var(--db-bg-table-head);
  font-weight: 600;
  text-align: left;
  color: var(--db-text-body);
  white-space: nowrap;
}

.bd-table td {
  padding: 9px 10px;
  border-bottom: 1px solid var(--db-border-light);
  color: var(--db-text-body);
}

.bd-table__title {
  color: var(--db-text-title);
  font-weight: 600;
}

.bd-link {
  color: var(--db-accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.bd-enroll-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.bd-enroll-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 0;
  border-bottom: 1px solid var(--db-border-light);
}

.bd-enroll-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.bd-enroll-item__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.bd-enroll-item__title {
  min-width: 0;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.bd-enroll-item__meta {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 599px) {
  .bd-hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px 16px;
  }

  .bd-info {
    grid-template-columns: minmax(0, 1fr);
  }

  .bd-card {
    padding: 16px;
  }

  /* [배지 폐기]가 오른쪽에 혼자 떠 있었다 — 줄 폭으로 */
  .bd-hero__action {
    align-self: stretch;
    margin-left: 0;
  }

  /* 7단계가 옆으로 넘어가 6·7단계가 잘렸다 — 칸을 좁혀 한 화면에 */
  .bd-ladder {
    gap: 0;
    padding: 12px 8px;
  }

  .bd-ladder__name {
    font-size: 13px;
  }

  .bd-ladder__range {
    font-size: 13px;
    letter-spacing: -0.2px;
    white-space: nowrap;
  }
}
</style>
