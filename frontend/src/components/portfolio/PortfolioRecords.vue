<template>
  <div class="pf-records">
    <!-- 내 포트폴리오는 수료한 강좌 줄에 수료증을 합쳐 보인다 — 거기 못 붙은 것만 넘어오면 이 칸을 그린다 -->
    <section v-if="!hideEmptyCredentials || credentials.length" class="pf-block">
      <h2 class="pf-block__title">
        수료증 <span class="pf-block__count">{{ credentials.length }}</span>
      </h2>
      <p v-if="!credentials.length" class="pf-block__empty">발급된 수료증이 없습니다.</p>
      <ul v-else class="pf-creds">
        <li v-for="item in credentials" :key="item.id" class="pf-cred">
          <div class="pf-cred__main">
            <span class="pf-cred__title">{{ item.courseTitle }}</span>
            <span class="pf-cred__meta">
              {{ [item.institutionName, completedLabel(item)].filter(Boolean).join(' · ') }}
            </span>
            <span class="pf-cred__no">수료증 번호 {{ item.credentialNo }}</span>
          </div>
          <div class="pf-cred__side">
            <StatusBadge :status="item.status" size="sm" />
            <!-- 서버 JSON(verifyPath) 대신 사람이 읽는 공개 검증 화면으로 보낸다 -->
            <router-link
              v-if="item.credentialNo"
              :to="verifyRoute(item.credentialNo)"
              target="_blank"
              rel="noopener"
              class="pf-cred__verify no-print"
            >
              진위 확인
            </router-link>
          </div>
        </li>
      </ul>
    </section>

    <!-- 포트폴리오는 남에게 보이고 인쇄하는 기록 — 딴 배지만(못 딴 6단계가 회색으로 함께 나왔다).
         단계 배지는 지금 단계 하나로. 다음 단계 진행은 학습지갑에 있다. 공개 포트폴리오는 badgeCatalog 가 없어 받은 배지만 -->
    <section v-if="badgeCatalog.length" class="pf-block">
      <h2 class="pf-block__title">
        학습배지 <span class="pf-block__count">{{ shownCatalog.length }}</span>
      </h2>
      <p v-if="!shownCatalog.length" class="pf-block__empty">받은 배지가 없습니다.</p>
      <ul v-else class="pf-catalog">
        <li
          v-for="item in shownCatalog"
          :key="item.key"
          class="pf-catalog__item"
          :class="{ 'pf-catalog__item--missing': !item.owned, 'pf-catalog__item--current': item.current }"
        >
          <BadgeMedal :image-url="item.imageUrl || ''" :size="56" :muted="!item.owned" :alt="item.name" />
          <div class="pf-catalog__body">
            <p v-if="item.groupLabel" class="pf-catalog__group">{{ item.groupLabel }}</p>
            <p class="pf-catalog__head">
              <span class="pf-catalog__name">{{ item.name }}</span>
              <span v-if="item.current" class="pf-catalog__owned pf-catalog__owned--current">보유 중</span>
              <span v-else-if="item.owned" class="pf-catalog__owned">보유</span>
              <span v-else class="pf-catalog__missing">미보유</span>
            </p>
            <p v-if="item.issuedAt" class="pf-catalog__date">
              {{ formatDateKst(item.issuedAt) }} {{ item.current ? '이 단계 도달' : '획득' }}
            </p>
            <BadgeCriteria
              class="pf-catalog__criteria"
              :fallback="item.criteria || ''"
            />
          </div>
        </li>
      </ul>
    </section>

    <section v-else class="pf-block">
      <h2 class="pf-block__title">
        학습배지 <span class="pf-block__count">{{ badges.length }}</span>
      </h2>
      <p v-if="!badges.length" class="pf-block__empty">받은 배지가 없습니다.</p>
      <ul v-else class="pf-badges">
        <li v-for="item in badges" :key="item.id" class="pf-badge">
          <BadgeMedal :image-url="item.imageUrl || ''" :size="56" :alt="item.name" />
          <span class="pf-badge__name">{{ item.name }}</span>
          <span v-if="tierLabel(item)" class="pf-badge__tier">{{ tierLabel(item) }}</span>
          <span v-if="item.issuedAt" class="pf-badge__date">{{ formatDateKst(item.issuedAt) }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<script>
import BadgeCriteria from '@/components/badge/BadgeCriteria.vue';
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { tierLabel } from '@/utils/badge-status';
import { expandTierLevels } from '@/utils/badge-tier';
import { formatDateKst } from '@/utils/datetime';

/* 포트폴리오 수료증·배지 목록 — 내 포트폴리오와 공개 포트폴리오가 같이 쓴다(인쇄에도 그대로 나온다) */
export default {
  name: 'PortfolioRecords',
  components: { BadgeCriteria, BadgeMedal, StatusBadge },
  props: {
    credentials: { type: Array, default: () => [] },
    badges: { type: Array, default: () => [] },
    /** 내 포트폴리오 전용 — [{ definitionId, name, imageUrl, criteria, owned, badge }] */
    badgeCatalog: { type: Array, default: () => [] },
    /** 수료증이 0건이면 칸을 숨긴다(내 포트폴리오 — 수료한 강좌 줄이 수료증을 맡을 때) */
    hideEmptyCredentials: { type: Boolean, default: false },
  },
  computed: {
    // 단계 배지는 단계마다 카드 하나로 푼다(씨앗·새싹… 각각 보유/미보유)
    catalogCards() {
      return this.badgeCatalog.flatMap((item) => {
        // 단계표는 카탈로그가 직접 준다(levels, 단계 배지가 아니면 null)
        if ((item.levels || []).length) {
          const source = { definitionId: item.definitionId, name: item.name, imageUrl: item.imageUrl, levels: item.levels };
          return expandTierLevels(source, item.owned ? item.badge : null).map((tier) => ({
            key: tier.key,
            name: tier.name,
            groupLabel: `${item.name} · ${tier.level}단계`,
            tiered: true,
            imageUrl: tier.imageUrl,
            owned: tier.owned,
            current: tier.current,
            // 이 단계 도달일 = 마지막 변동(갱신·복구) 시각. 최초 발급일을 쓰면 단계가 올라가도 옛 날짜가 남는다
            issuedAt: tier.current ? item.badge?.lastEvent?.occurredAt || item.badge?.issuedAt || null : null,
            criteria: tier.criteria,
          }));
        }
        return [
          {
            key: item.definitionId,
            name: item.name,
            groupLabel: '',
            imageUrl: (item.owned && item.badge?.imageUrl) || item.imageUrl,
            owned: item.owned,
            current: false,
            issuedAt: item.owned ? item.badge?.issuedAt || null : null,
            criteria: item.criteria,
          },
        ];
      });
    },
    ownedCatalog() {
      return this.catalogCards.filter((item) => item.owned);
    },
    // 딴 배지만 — 단계 배지는 지금 단계(current) 하나만, 아래 단계는 겹쳐 보이지 않게
    shownCatalog() {
      return this.ownedCatalog.filter((item) => !item.tiered || item.current);
    },
  },
  methods: {
    formatDateKst,
    tierLabel,
    completedLabel(item) {
      const date = item.completedAt || item.issuedAt;
      return date ? `${formatDateKst(date)} 수료` : '';
    },
    verifyRoute(credentialNo) {
      return { name: 'credential-verify-no', params: { credentialNo } };
    },
  },
};
</script>

<style scoped>
.pf-records {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pf-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  break-inside: avoid;
}

.pf-block__title {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.pf-block__count {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-accent);
  font-variant-numeric: tabular-nums;
}

.pf-block__empty {
  margin: 0;
  font-size: 14px;
  color: var(--db-text-sub);
}

.pf-creds {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-cred {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 12px 0;
  break-inside: avoid;
}

.pf-cred + .pf-cred {
  border-top: 1px solid var(--db-border);
}

.pf-cred__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.pf-cred__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.pf-cred__meta,
.pf-cred__no {
  font-size: 14px;
  color: var(--db-text-sub);
}

.pf-cred__no {
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.pf-cred__side {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.pf-cred__verify {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-accent);
  white-space: nowrap;
}

.pf-badges {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(112px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
  break-inside: avoid;
}

.pf-badge__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-body);
  word-break: keep-all;
}

.pf-badge__tier {
  font-size: 13px;
  font-weight: 700;
  color: var(--db-primary);
}

/* 보유/미보유 */
.pf-catalog {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  /* 모든 줄을 같은 높이로 — 기준 문장이 긴 카드 하나에 그 줄만 높아지지 않게 */
  grid-auto-rows: 1fr;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.pf-catalog__item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--db-accent) 45%, var(--db-border));
  border-radius: 8px;
  break-inside: avoid;
}

.pf-catalog__item.pf-catalog__item--current {
  border-color: var(--db-primary);
  box-shadow: inset 0 0 0 1px var(--db-primary);
}

.pf-catalog__group {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.pf-catalog__owned.pf-catalog__owned--current {
  background: var(--db-primary);
  color: var(--db-on-color);
}

.pf-catalog__item--missing {
  border-color: var(--db-border);
  border-style: dashed;
  background: var(--db-bg-subtle);
}

.pf-catalog__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.pf-catalog__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
}

.pf-catalog__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
  word-break: keep-all;
}

.pf-catalog__item--missing .pf-catalog__name {
  color: var(--db-text-sub);
}

.pf-catalog__owned,
.pf-catalog__missing {
  padding: 0 6px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
}

.pf-catalog__owned {
  background: var(--db-accent-soft);
  color: var(--db-primary);
}

.pf-catalog__missing {
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  color: var(--db-text-sub);
}

.pf-catalog__date {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.pf-catalog__criteria {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
  margin: 0;
  font-size: 14px;
  line-height: 19px;
  color: var(--db-text-body);
  word-break: keep-all;
}

.pf-badge__date {
  font-size: 13px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

@media print {
  .pf-block {
    border-color: var(--db-border);
  }

  .no-print {
    display: none !important;
  }
}
</style>
