<template>
  <!-- 외부 기관 원문이라 글자로만 그린다(v-html 금지). 카드 전체 클릭은 제목 링크를 카드 크기로 늘려서,
       '운영기관에서 신청'은 그 위에 떠 있는 별도 링크 -->
  <article class="pc-card" :class="{ 'pc-card--closed': closed }">
    <!-- 거의 모두 오프라인(491/492)이라 방식 배지는 오프라인이 아닐 때만, 구·군은 기관명에 이미 있으면 뺀다 -->
    <div v-if="badgeVisible" class="pc-card__badges">
      <!-- 다배움 강좌 카드와 같은 회색 칩 — 수업 방식만 굵게. 칩마다 색(파랑 온라인·초록 무료)이 달라 시끄러웠다 -->
      <span v-if="methodLabel" class="pc-card__chip pc-card__chip--type">{{ methodLabel }}</span>
      <span v-if="regionLabel" class="pc-card__chip">{{ regionLabel }}</span>
      <span v-if="costLabel" class="pc-card__chip">{{ costLabel }}</span>
    </div>

    <h3 class="pc-card__title">
      <router-link :to="`/public-courses/${course.id}`" class="pc-card__link">{{ course.lectureName || '이름 없는 강좌' }}</router-link>
    </h3>
    <!-- 강좌마다 다른 건 요일·시간 — 제목 바로 아래 크게 (기간은 대부분 1년짜리라 작게) -->
    <div v-if="scheduleLabel" class="pc-card__schedule">{{ scheduleLabel }}</div>
    <!-- 기관·기간을 한 줄씩 — 홈 4칸 카드에서 한 줄에 이으면 날짜가 '~' 에서 꺾였다 -->
    <div class="pc-card__org">{{ course.operInstitutionName || '운영기관 정보 없음' }}</div>
    <div v-if="periodLabel" class="pc-card__period">{{ periodLabel }}</div>

    <div class="pc-card__foot">
      <!-- 접수 기간이 없는 강좌가 대부분이라 '운영기관에서 확인'은 카드에서 뺀다(상세엔 있다) -->
      <span v-if="receipt.tone !== 'unknown'" class="pc-card__receipt" :class="`pc-card__receipt--${receipt.tone}`">{{ receipt.text }}</span>
      <a
        v-if="homepageUrl"
        :href="homepageUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="pc-card__apply"
        :aria-label="`${course.lectureName || '강좌'} ${applyLabel} (새 창)`"
      >
        {{ applyLabel }} <q-icon name="open_in_new" size="14px" aria-hidden="true" />
      </a>
    </div>
  </article>
</template>

<script>
import {
  publicCostLabel,
  publicMethodLabel,
  publicPeriodLabel,
  publicReceiptState,
  publicRegionLabel,
  publicScheduleLabel,
  safeHomepageUrl,
} from '@/utils/public-course';

export default {
  name: 'PublicCourseCard',
  props: {
    /** PublicCourseSummary */
    course: { type: Object, required: true },
  },
  computed: {
    methodLabel() {
      return this.course.method === 'OFFLINE' ? '' : publicMethodLabel(this.course.method);
    },
    regionLabel() {
      const label = publicRegionLabel(this.course.regionCode);
      return label && !String(this.course.operInstitutionName || '').includes(label) ? label : '';
    },
    badgeVisible() {
      return Boolean(this.methodLabel || this.regionLabel || this.costLabel);
    },
    costLabel() {
      return publicCostLabel(this.course);
    },
    periodLabel() {
      return publicPeriodLabel(this.course);
    },
    scheduleLabel() {
      return publicScheduleLabel(this.course);
    },
    receipt() {
      return publicReceiptState(this.course);
    },
    // 접수가 끝났으면 흐리게, 링크도 '신청'이라 하지 않는다(누르면 신청될 것처럼 보였다)
    closed() {
      return this.receipt.tone === 'closed';
    },
    applyLabel() {
      return this.closed ? '운영기관 홈페이지' : '운영기관에서 신청';
    },
    homepageUrl() {
      return safeHomepageUrl(this.course.homepageUrl);
    },
  },
};
</script>

<style scoped>
.pc-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

/* 다배움 강좌 카드와 같은 hover — 살짝 뜨고 테두리·제목이 파래진다. 터치 기기는 hover 가 눌린 채 남아 뺀다 */
@media (hover: hover) {
  .pc-card {
    transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  }

  .pc-card__link {
    transition: color 0.2s ease;
  }

  .pc-card:hover {
    transform: translateY(-3px);
    border-color: var(--db-accent);
    box-shadow: var(--db-shadow-card-hover);
  }

  .pc-card:hover .pc-card__link {
    color: var(--db-accent);
  }
}

.pc-card:focus-within {
  border-color: var(--db-accent);
}

@media (prefers-reduced-motion: reduce) {
  .pc-card {
    transition: none;
  }

  .pc-card:hover {
    transform: none;
  }
}

.pc-card--closed .pc-card__badges,
.pc-card--closed .pc-card__title,
.pc-card--closed .pc-card__schedule,
.pc-card--closed .pc-card__org,
.pc-card--closed .pc-card__period {
  opacity: 0.55;
}

.pc-card--closed .pc-card__apply {
  color: var(--db-text-sub);
  font-weight: 500;
}

.pc-card__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.pc-card__chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  color: var(--db-text-body);
  background: var(--db-bg-tag);
}

.pc-card__chip--type {
  font-weight: 700;
  color: var(--db-text-title);
}

.pc-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  word-break: keep-all;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pc-card__link {
  color: var(--db-text-title);
  text-decoration: none;
}

/* 카드 전체를 누르게 — 링크 영역을 카드 크기로 */
.pc-card__link::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 8px;
}

.pc-card__link:focus-visible {
  outline: none;
}

.pc-card__link:focus-visible::after {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
}

.pc-card__schedule {
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-body);
}

.pc-card__period {
  font-size: 15px;
  color: var(--db-text-sub);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.pc-card__org {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  color: var(--db-text-sub);
  word-break: keep-all;
}


.pc-card__foot {
  /* 접수 문구 한 줄, 그 아래 '운영기관에서 신청 ↗' 한 줄 — '신청하기' 로 줄이면 다배움에서 신청되는 줄 알았다 */
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--db-border);
}

.pc-card__receipt {
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  margin-right: auto;
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-sub);
}

.pc-card__receipt--open {
  color: var(--db-status-green-text);
}

.pc-card__receipt--soon {
  color: var(--db-status-pink);
}

/* 링크가 카드 덮개(::after) 위로 올라와 따로 눌린다 */
.pc-card__apply {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  /* 중장년 이용자 — 누르는 자리 44px */
  min-height: 44px;
  font-size: 15px;
  font-weight: 600;
  /* 여섯 카드에 파란 링크가 줄지어 시끄러웠다 — 평소엔 본문색, 가리키면 파랑 */
  color: var(--db-text-body);
  text-decoration: none;
  white-space: nowrap;
}

.pc-card__apply:hover,
.pc-card__apply:focus-visible {
  color: var(--db-accent);
  text-decoration: underline;
}
/* 폰: 카드 한 장이 214px 이라 훑기 어려웠다 — 바닥 줄을 오른쪽 위 링크로 접는다 */
@media (max-width: 599px) {
  .pc-card {
    gap: 4px;
    padding: 12px 14px;
  }

  .pc-card__title {
    font-size: 15px;
  }

  .pc-card__foot {
    padding-top: 6px;
    border-top: 0;
  }
}
</style>
