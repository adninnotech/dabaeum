<template>
  <ul class="badge-grid">
    <li v-for="item in items" :key="item.id" class="badge-grid__item">
      <!-- 받은(또는 폐기된) 배지는 상세로 — detailBase 가 있을 때만 -->
      <component
        :is="item.detailTo ? 'router-link' : 'div'"
        :to="item.detailTo || undefined"
        class="badge-card"
        :class="{
          'badge-card--earned': showEarned && item.earned,
          'badge-card--current': showEarned && item.current,
          'badge-card--link': item.detailTo,
        }"
        :aria-label="item.detailTo ? `${item.name} 상세 보기` : undefined"
      >
        <BadgeMedal
          :image-url="item.displayImageUrl || ''"
          :image-file-id="item.imageFileId || ''"
          :muted="showEarned && !item.earned"
          :alt="item.name"
          :size="64"
        />
        <div class="badge-card__body">
          <!-- 단계 배지를 푼 카드는 어느 배지의 몇 단계인지 위에 작게 -->
          <p v-if="item.groupLabel" class="badge-card__group">{{ item.groupLabel }}</p>
          <div class="badge-card__head">
            <h3 class="badge-card__name">{{ item.name }}</h3>
            <span v-if="showEarned && item.current" class="badge-card__earned badge-card__earned--current">
              <q-icon name="check" size="14px" />보유 중
            </span>
            <span v-else-if="showEarned && item.earned" class="badge-card__earned">
              <q-icon name="check" size="14px" />보유
            </span>
            <span v-else-if="showEarned && item.revoked" class="badge-card__revoked">폐기</span>
          </div>
          <BadgeCriteria
            class="badge-card__rule"
            :rule="item.rule"
            :fallback="item.criteria || ''"
            :classification-tree="classificationTree"
            :course-titles="courseTitles"
          />
          <p v-if="item.description" class="badge-card__desc" :title="item.description">{{ item.description }}</p>
          <p v-if="showEarned && item.earned && item.issuedAt" class="badge-card__date">
            {{ formatDateKst(item.issuedAt) }} {{ item.current ? '이 단계 도달' : '획득' }}
          </p>
          <p v-else-if="showEarned && item.revoked" class="badge-card__date">{{ revokedNotice(item) }}</p>
        </div>
        <q-icon v-if="item.detailTo" name="chevron_right" size="20px" class="badge-card__go" aria-hidden="true" />
      </component>
    </li>
  </ul>
</template>

<script>
import BadgeCriteria from '@/components/badge/BadgeCriteria.vue';
import BadgeMedal from '@/components/badge/BadgeMedal.vue';
import { revokeCause } from '@/utils/badge-revoke';
import { expandTierLevels } from '@/utils/badge-tier';
import { formatDateKst } from '@/utils/datetime';

/* 배지 도감·학습지갑 공통 카드 목록. earnedByDefinition = { [definitionId]: 받은 배지 } */
export default {
  name: 'BadgeGrid',
  components: { BadgeCriteria, BadgeMedal },
  props: {
    definitions: { type: Array, default: () => [] },
    earnedByDefinition: { type: Object, default: () => ({}) },
    /** { [definitionId]: 폐기된 배지 } — 획득이 아니지만 받았다가 잃은 것을 따로 보인다 */
    revokedByDefinition: { type: Object, default: () => ({}) },
    /** false 면 획득 표시를 하지 않는다(비로그인 도감) */
    showEarned: { type: Boolean, default: true },
    classificationTree: { type: Array, default: () => [] },
    courseTitles: { type: Object, default: () => ({}) },
    /** 예: '/wallet/badges' — 있으면 받은·폐기된 배지 카드를 상세 링크로 */
    detailBase: { type: String, default: '' },
  },
  computed: {
    singleTierFamily() {
      return this.definitions.filter((item) => item.rule?.type === 'TIERED').length === 1;
    },
    items() {
      return this.definitions.flatMap((definition) => {
        const badge = this.earnedByDefinition[definition.id] || null;
        const revoked = !badge ? this.revokedByDefinition[definition.id] || null : null;
        const owned = badge || revoked;
        const detailTo = this.detailBase && owned ? `${this.detailBase}/${owned.id}` : '';
        if (definition.rule?.type === 'TIERED' && (definition.rule.levels || []).length) {
          return this.tierItems(definition, badge, revoked, detailTo);
        }
        return [
          {
            ...definition,
            displayImageUrl: badge?.imageUrl || definition.imageUrl,
            detailTo,
            earned: !!badge,
            current: false,
            revoked: !!revoked,
            revokedAt: revoked?.revokedAt || revoked?.updatedAt || null,
            revokeCause: revokeCause(revoked),
            issuedAt: badge?.issuedAt || badge?.createdAt || null,
          },
        ];
      });
    },
  },
  methods: {
    formatDateKst,
    /**
     * 폐기 안내 — 원인과 상관없이 '조건을 다시 채우면 돌아옵니다'라 했는데, 관리자가 폐기한 배지는
     * 조건을 채워도 돌아오지 않는다(QA 중앙 #54)
     */
    revokedNotice(item) {
      const date = formatDateKst(item.revokedAt);
      const { kind, reason } = item.revokeCause || {};
      if (kind === 'AUTO') return `수료 확정이 취소되어 ${date} 폐기되었습니다. 조건을 다시 채우면 돌아옵니다.`;
      if (kind === 'MANUAL') {
        return `${date} 관리자가 폐기했습니다${reason ? `(사유: ${reason})` : ''}. 조건을 다시 채워도 돌아오지 않습니다.`;
      }
      return `${date} 폐기되었습니다.`;
    },
    // 단계 배지 → 단계마다 카드 하나. 보유한 단계만 상세로 간다(같은 배지 하나의 상세)
    tierItems(definition, badge, revoked, detailTo) {
      const source = {
        definitionId: definition.id,
        name: definition.name,
        imageUrl: definition.imageUrl,
        levels: definition.rule.levels,
      };
      return expandTierLevels(source, badge, revoked).map((item) => ({
        id: item.key,
        name: item.name,
        // 단계 배지가 하나뿐이면 이름을 카드마다 되풀이하지 않는다(머리·진행 막대에 한 번)
        groupLabel: this.singleTierFamily ? `${item.level}단계` : `${definition.name} · ${item.level}단계`,
        displayImageUrl: item.imageUrl,
        imageFileId: '',
        rule: null,
        criteria: item.criteria,
        description: '',
        earned: item.owned,
        current: item.current,
        revoked: item.revoked,
        revokedAt: item.revoked ? revoked?.revokedAt || revoked?.updatedAt || null : null,
        revokeCause: revokeCause(revoked),
        // 단계 배지는 같은 행이 올라간다 — 이 단계에 도달한 때는 최초 발급이 아니라 마지막 변동(갱신·복구) 시각
        issuedAt: item.current ? badge?.lastEvent?.occurredAt || badge?.issuedAt || null : null,
        detailTo: item.owned || item.revoked ? detailTo : '',
      }));
    },
  },
};
</script>

<style scoped>
.badge-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  /* 모든 줄을 같은 높이로 — 글이 긴 카드 하나 때문에 그 줄만 높아지지 않게(긴 글은 아래에서 2줄로 줄인다) */
  grid-auto-rows: 1fr;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.badge-grid__item {
  display: flex;
  min-width: 0;
}

.badge-card {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.badge-card--link {
  color: inherit;
  text-decoration: none;
  cursor: pointer;
}

.badge-card--link:hover {
  background: var(--db-bg-subtle);
}

.badge-card--link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.badge-card__go {
  flex-shrink: 0;
  align-self: center;
  color: var(--db-text-sub);
}

.badge-card__group {
  margin: 0;
  font-size: 13px;
  color: var(--db-text-sub);
}

.badge-card.badge-card--current {
  border-color: var(--db-primary);
  box-shadow: inset 0 0 0 1px var(--db-primary);
}

.badge-card__earned.badge-card__earned--current {
  background: var(--db-primary);
  color: var(--db-on-color);
}

.badge-card--earned {
  border-color: color-mix(in srgb, var(--db-accent) 45%, var(--db-border));
}

.badge-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.badge-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.badge-card__name {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  line-height: 22px;
  font-weight: 700;
  color: var(--db-text-title);
}

.badge-card__earned {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--db-accent-soft);
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-accent);
}

.badge-card__revoked {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 4px;
  background: var(--db-bg-page);
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-text-sub);
}

.badge-card__rule {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  color: var(--db-text-body);
  word-break: keep-all;
}

.badge-card__rule,
.badge-card__desc {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.badge-card__desc {
  margin: 0;
  font-size: 14px;
  line-height: 19px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.badge-card__date {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 16px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 폰: 한 줄에 하나씩이라 7단계를 보려면 한참 내려야 했다 — 2칸 세로 카드(메달 위, 글 아래) */
@media (max-width: 599px) {
  .badge-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .badge-card {
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 14px 10px;
    text-align: center;
  }

  .badge-card__body {
    align-items: center;
    width: 100%;
  }

  .badge-card__head {
    flex-wrap: wrap;
    justify-content: center;
    row-gap: 2px;
  }

  .badge-card__rule {
    font-size: 14px;
    line-height: 18px;
  }

  /* 좁은 칸 — 설명·화살표는 상세에서 */
  .badge-card__desc,
  .badge-card__go {
    display: none;
  }

  .badge-card__date {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    overflow: hidden;
  }
}
</style>
