<template>
  <q-page class="pf-page">
    <div class="learner-container pf-body">
      <!-- 학습지갑으로 돌아갈 길이 없었다 -->
      <router-link to="/wallet" class="pf-back no-print">
        <q-icon name="arrow_back" size="18px" aria-hidden="true" />학습지갑
      </router-link>
      <header class="pf-head">
        <div class="pf-head__who">
          <h1 class="pf-head__title">학습 포트폴리오</h1>
          <p v-if="portfolio" class="pf-head__meta">
            <strong>{{ portfolio.name }}</strong>
            <span>{{ formatDateKst(portfolio.joinedAt) }} 가입</span>
          </p>
        </div>
        <div class="pf-head__actions no-print">
          <q-btn outline no-caps color="primary" icon="print" label="인쇄 · PDF 저장" :disable="!portfolio" @click="print" />
          <q-btn
            unelevated
            no-caps
            color="primary"
            icon="link"
            label="공유 링크 만들기"
            :disable="!portfolio"
            @click="shareOpen = true"
          />
        </div>
      </header>

      <AppErrorBanner v-if="errorMessage" :message="errorMessage" />

      <template v-else-if="portfolio">
        <div class="pf-tiles">
          <div v-for="tile in tiles" :key="tile.label" class="pf-tile">
            <div class="pf-tile__label">{{ tile.label }}</div>
            <div class="pf-tile__value">
              {{ tile.value }}<span class="pf-tile__unit">{{ tile.unit }}</span>
            </div>
          </div>
        </div>

        <!-- 분야별 수료(대분류) — 계열 하나라 범례 없이 제목이 무엇인지 말한다 -->
        <section class="pf-card">
          <h2 class="pf-card__title">분야별 수료</h2>
          <p v-if="!distribution.length" class="pf-card__empty">아직 수료한 강좌가 없습니다.</p>
          <ul v-else class="pf-dist">
            <li v-for="row in distribution" :key="row.classificationCode" class="pf-dist__row">
              <span class="pf-dist__name">{{ row.classificationName }}</span>
              <span class="pf-dist__track">
                <span class="pf-dist__bar" :style="{ width: `${row.ratio}%` }" />
              </span>
              <span class="pf-dist__count">{{ row.completedCount }}건</span>
            </li>
          </ul>
        </section>

        <section class="pf-card">
          <h2 class="pf-card__title">
            수료한 강좌 <span class="pf-card__count">{{ portfolio.completions.length }}</span>
          </h2>
          <p v-if="!portfolio.completions.length" class="pf-card__empty">아직 수료한 강좌가 없습니다.</p>
          <!-- '수료증' 칸이 같은 강좌를 한 번 더 보였다 — 수료한 강좌 줄에 수료증 상태·진위 확인을 붙인다 -->
          <ul v-else class="pf-list">
            <li v-for="item in completionRows" :key="item.courseId" class="pf-list__row">
              <span class="pf-list__main">
                <span class="pf-list__title">{{ item.title }}</span>
                <span class="pf-list__meta">
                  {{ [item.institutionName, item.completedAt ? `${formatDateKst(item.completedAt)} 수료` : ''].filter(Boolean).join(' · ') || '-' }}
                </span>
                <span v-if="item.credential?.credentialNo" class="pf-list__no">수료증 번호 {{ item.credential.credentialNo }}</span>
              </span>
              <span v-if="item.credential" class="pf-list__side">
                <StatusBadge :status="item.credential.status" size="sm" />
                <router-link
                  v-if="item.credential.credentialNo"
                  :to="{ name: 'credential-verify-no', params: { credentialNo: item.credential.credentialNo } }"
                  target="_blank"
                  rel="noopener"
                  class="pf-list__verify no-print"
                >
                  진위 확인
                </router-link>
              </span>
            </li>
          </ul>
        </section>

        <PortfolioRecords
          :credentials="unmatchedCredentials"
          hide-empty-credentials
          :badges="portfolio.badges"
          :badge-catalog="portfolio.badgeCatalog || []"
        />

        <!-- 공유 링크 — 인쇄에는 넣지 않는다 -->
        <section class="pf-card no-print">
          <h2 class="pf-card__title">공유 링크</h2>
          <AppErrorBanner v-if="sharesError" :message="sharesError" />
          <p v-else-if="!shares.length" class="pf-card__empty">
            만든 링크가 없습니다. 링크는 만들 때 한 번만 보여 드립니다.
          </p>
          <!-- 폰: 6칸 표가 화면 밖(504px)으로 넘쳤다 — 줄마다 카드 -->
          <ul v-if="shares.length" class="pf-share-cards">
            <li v-for="share in shares" :key="share.id" class="pf-share-card">
              <div class="pf-share-card__top">
                <span>{{ formatDateKst(share.createdAt) }} 만듦</span>
                <StatusBadge
                  :label="SHARE_STATUS[share.status]?.label || share.status"
                  :color="SHARE_STATUS[share.status]?.color"
                />
              </div>
              <div class="pf-share-card__meta">
                수료증 {{ share.credentialCount }} · 배지 {{ share.badgeCount }} · {{ formatDateKst(share.expiresAt) }}까지 ·
                열람 {{ share.viewCount }}회
              </div>
              <q-btn
                v-if="share.status === 'ACTIVE'"
                flat
                dense
                no-caps
                label="폐기"
                class="pf-shares__revoke pf-share-card__revoke"
                :loading="revokingId === share.id"
                @click="confirmRevoke(share)"
              />
            </li>
          </ul>
          <div v-if="shares.length" class="pf-shares">
            <table class="pf-shares__table">
              <thead>
                <tr>
                  <th scope="col">만든 날</th>
                  <th scope="col">담은 항목</th>
                  <th scope="col">만료</th>
                  <th scope="col" class="pf-num">열람</th>
                  <th scope="col">상태</th>
                  <th scope="col"><span class="sr-only">폐기</span></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="share in shares" :key="share.id">
                  <td>{{ formatDateKst(share.createdAt) }}</td>
                  <td>수료증 {{ share.credentialCount }} · 배지 {{ share.badgeCount }}</td>
                  <td>{{ formatDateKst(share.expiresAt) }}</td>
                  <td class="pf-num">
                    {{ share.viewCount }}
                    <span v-if="share.lastViewedAt" class="pf-shares__last">
                      (최근 {{ formatDateKst(share.lastViewedAt) }})
                    </span>
                  </td>
                  <td>
                    <StatusBadge
                      :label="SHARE_STATUS[share.status]?.label || share.status"
                      :color="SHARE_STATUS[share.status]?.color"
                    />
                  </td>
                  <td>
                    <q-btn
                      v-if="share.status === 'ACTIVE'"
                      flat
                      dense
                      no-caps
                      label="폐기"
                      class="pf-shares__revoke"
                      :loading="revokingId === share.id"
                      @click="confirmRevoke(share)"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>

    <PortfolioShareDialog
      v-if="portfolio"
      v-model="shareOpen"
      :credentials="portfolio.credentials"
      :badges="portfolio.badges"
      @created="loadShares"
    />

    <q-inner-loading :showing="loading">
      <q-spinner color="primary" size="32px" />
    </q-inner-loading>
  </q-page>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import PortfolioRecords from '@/components/portfolio/PortfolioRecords.vue';
import PortfolioShareDialog from '@/components/portfolio/PortfolioShareDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import {
  getMyPortfolio,
  listPortfolioShares,
  revokePortfolioShare,
} from '@/services/portfolio-api';
import { formatDateKst } from '@/utils/datetime';

const SHARE_STATUS = {
  ACTIVE: { label: '열림', color: 'var(--db-status-green)' },
  EXPIRED: { label: '만료', color: 'var(--db-status-gray)' },
  REVOKED: { label: '폐기', color: 'var(--db-status-gray)' },
};

/* 학습 포트폴리오(설계서 §6) — 마이페이지 하위. 피그마에 없어 마이페이지 카드 톤을 따르고, 인쇄 CSS 로 PDF 저장을 대신한다 */
export default {
  name: 'PortfolioPage',
  components: { AppErrorBanner, PortfolioRecords, PortfolioShareDialog, StatusBadge },
  data() {
    return {
      SHARE_STATUS,
      portfolio: null,
      loading: false,
      errorMessage: '',
      shares: [],
      sharesError: '',
      shareOpen: false,
      revokingId: '',
    };
  },
  computed: {
    tiles() {
      const s = this.portfolio?.summary || {};
      const hours = Number(s.totalLearningHours || 0);
      return [
        // 다른 화면과 같은 단위('강좌 n건') — '과목'이 섞였다
        { label: '수료한 강좌', value: s.completedCourseCount ?? 0, unit: '건' },
        {
          label: '학습 시간',
          value: Number.isInteger(hours) ? hours : hours.toFixed(1),
          unit: '시간',
        },
        { label: '수료증', value: s.credentialCount ?? 0, unit: '건' },
        { label: '학습배지', value: s.badgeCount ?? 0, unit: '개' },
      ];
    },
    /** 수료 줄 → 그 수료의 수료증(completion.credentialId — 수료증 응답엔 강좌 id 가 없다) */
    credentialById() {
      return Object.fromEntries((this.portfolio?.credentials || []).map((item) => [item.id, item]));
    },
    completionRows() {
      return (this.portfolio?.completions || []).map((item) => ({
        ...item,
        credential: item.credentialId ? this.credentialById[item.credentialId] || null : null,
      }));
    },
    // 수료한 강좌 줄에 붙지 못한 수료증만 아래 '수료증' 칸으로
    unmatchedCredentials() {
      const shown = new Set(this.completionRows.map((row) => row.credential?.id).filter(Boolean));
      return (this.portfolio?.credentials || []).filter((item) => !shown.has(item.id));
    },
    distribution() {
      const rows = this.portfolio?.categoryDistribution || [];
      const max = Math.max(1, ...rows.map((row) => row.completedCount || 0));
      return [...rows]
        .sort((a, b) => b.completedCount - a.completedCount)
        .map((row) => ({ ...row, ratio: (row.completedCount / max) * 100 }));
    },
  },
  created() {
    this.load();
    this.loadShares();
  },
  methods: {
    formatDateKst,
    print() {
      window.print();
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.portfolio = await getMyPortfolio();
      } catch (error) {
        this.portfolio = null;
        this.errorMessage = error?.message || '포트폴리오를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async loadShares() {
      this.sharesError = '';
      try {
        this.shares = await listPortfolioShares();
      } catch (error) {
        this.shares = [];
        this.sharesError = error?.message || '공유 링크 목록을 불러오지 못했습니다.';
      }
    },
    confirmRevoke(share) {
      this.$q
        .dialog({
          title: '공유 링크 폐기',
          message: '이 링크는 바로 열리지 않게 됩니다. 되돌릴 수 없고, 필요하면 새 링크를 만들어야 합니다.',
          cancel: { flat: true, noCaps: true, label: '취소' },
          ok: { unelevated: true, noCaps: true, color: 'negative', label: '폐기' },
          persistent: true,
        })
        .onOk(() => this.revoke(share));
    },
    async revoke(share) {
      this.revokingId = share.id;
      try {
        await revokePortfolioShare(share.id);
        await this.loadShares();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error?.message || '폐기하지 못했습니다.',
          position: 'top',
        });
      } finally {
        this.revokingId = '';
      }
    },
  },
};
</script>

<style scoped>
.pf-back {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 4px;
  font-size: 14px;
  color: var(--db-text-sub);
  text-decoration: none;
}

.pf-back:hover,
.pf-back:focus-visible {
  color: var(--db-accent);
}

.pf-list__no {
  font-size: 13px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.pf-list__side {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.pf-list__verify {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
}

.pf-share-cards {
  display: none;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-share-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.pf-share-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-title);
}

.pf-share-card__meta {
  font-size: 14px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.pf-share-card__revoke {
  align-self: flex-end;
}

.pf-page {
  position: relative;
  padding: 24px 0;
  background: var(--db-bg-page);
}

.pf-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.pf-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 10px;
}

.pf-head__title {
  margin: 0;
  font-size: 26px;
  line-height: 31px;
  font-weight: 700;
  color: var(--db-text-body);
}

.pf-head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.pf-head__meta strong {
  color: var(--db-text-title);
}

.pf-head__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pf-head__actions .q-btn {
  border-radius: 6px;
}

.pf-tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.pf-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  break-inside: avoid;
}

.pf-tile__label {
  font-size: 14px;
  color: var(--db-text-sub);
}

.pf-tile__value {
  font-size: 26px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
}

.pf-tile__unit {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.pf-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  break-inside: avoid;
}

.pf-card__title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pf-card__count {
  font-size: 15px;
  color: var(--db-accent);
  font-variant-numeric: tabular-nums;
}

.pf-card__empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

/* 단일 계열 가로 막대 — dataviz: 막대 ≤24px, 끝 4px 둥글게, 값은 글자색 */
.pf-dist {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-dist__row {
  display: grid;
  grid-template-columns: minmax(0, 180px) minmax(0, 1fr) 56px;
  align-items: center;
  gap: 12px;
}

.pf-dist__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--db-text-body);
}

.pf-dist__track {
  height: 12px;
  border-radius: 0 4px 4px 0;
  background: var(--db-bg-page);
}

.pf-dist__bar {
  display: block;
  height: 100%;
  min-width: 4px;
  border-radius: 0 4px 4px 0;
  background: var(--db-accent-fill);
  print-color-adjust: exact;
  -webkit-print-color-adjust: exact;
}

.pf-dist__count {
  font-size: 14px;
  font-weight: 600;
  text-align: right;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
}

.pf-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-list__row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  break-inside: avoid;
}

.pf-list__row + .pf-list__row {
  border-top: 1px solid var(--db-border);
}

.pf-list__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pf-list__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.pf-list__meta {
  font-size: 14px;
  color: var(--db-text-sub);
}

.pf-list__date {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.pf-shares {
  /* position 이 없으면 표 안 .sr-only(absolute)가 스크롤 상자를 빠져나가 페이지를 넓힌다 */
  position: relative;
  max-width: 100%;
  overflow-x: auto;
}

.pf-shares__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.pf-shares__table th,
.pf-shares__table td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--db-border);
  text-align: left;
  white-space: nowrap;
  color: var(--db-text-body);
}

.pf-shares__table th {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.pf-num {
  text-align: right !important;
  font-variant-numeric: tabular-nums;
}

.pf-shares__last {
  font-size: 13px;
  color: var(--db-text-sub);
}

.pf-shares__revoke {
  color: var(--db-status-pink);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

@media (max-width: 599px) {
  .pf-share-cards {
    display: flex;
  }

  .pf-shares {
    display: none;
  }

  .pf-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pf-dist__row {
    grid-template-columns: minmax(0, 96px) minmax(0, 1fr) 48px;
  }

  .pf-head__actions {
    width: 100%;
  }

  .pf-head__actions .q-btn {
    flex: 1;
  }
}

/* 인쇄 = PDF 저장. 헤더·하단 탭·푸터·버튼을 빼고 본문만 */
@media print {
  :global(.q-header),
  :global(.q-footer),
  :global(.q-drawer),
  :global(.learner-footer) {
    display: none !important;
  }

  :global(.q-page-container) {
    padding: 0 !important;
  }

  .pf-page {
    padding: 0;
    background: var(--db-bg-white);
  }

  .pf-tiles {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .no-print {
    display: none !important;
  }
}
</style>
