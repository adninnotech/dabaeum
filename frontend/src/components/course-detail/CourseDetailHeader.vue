<template>
  <header class="detail-header">
    <!-- 좌: 썸네일 + 요약. 피그마의 액션 버튼 4개는 미정의라 렌더하지 않는다 -->
    <div class="detail-header__left">
      <img v-if="thumbnailUrl" :src="thumbnailUrl" alt="대표 이미지" class="detail-header__thumb" />
      <div v-else class="detail-header__thumb detail-header__thumb--empty" />

      <div class="detail-header__summary">
        <div class="detail-header__title">{{ course?.title || '-' }}</div>

        <div class="detail-header__line">
          <span>{{ institutionName || '-' }}</span>
          <i class="detail-header__sep" />
          <span>{{ instructorName ? `${instructorName} 강사` : '강사 미배정' }}</span>
          <i class="detail-header__sep" />
          <span>{{ classificationText }}</span>
        </div>

        <div class="detail-header__line">
          <span>교육비 {{ tuitionAmountText }}</span>
          <i class="detail-header__sep" />
          <span>재료비 {{ materialFeeText }}</span>
        </div>

        <div v-if="tagList.length" class="detail-header__tags">
          <AppTag v-for="tag in tagList" :key="tag">{{ tag }}</AppTag>
        </div>
      </div>
    </div>

    <!-- 우: 5행 요약 -->
    <dl class="detail-header__right">
      <div v-for="row in metaRows" :key="row.label" class="detail-header__row">
        <dt class="detail-header__label">{{ row.label }}</dt>
        <dd class="detail-header__value" :class="{ accent: row.accent }">{{ row.value }}</dd>
      </div>
    </dl>
  </header>
</template>

<script>
import AppTag from '@/components/AppTag.vue';
import { storedFileContentUrl } from '@/services/file-api';
import { daysBetween, daysFromToday, formatDateKst } from '@/utils/datetime';
import { classificationPathText } from '@/utils/classification';
import { tuitionText } from '@/utils/course-fields';
import { educationTypeLabel, statusLabel } from '@/utils/status';

export default {
  name: 'CourseDetailHeader',
  components: { AppTag },
  props: {
    course: { type: Object, default: null },
    institutionName: { type: String, default: '' },
    instructorName: { type: String, default: '' },
  },
  computed: {
    classificationText() {
      return classificationPathText(this.course?.classificationPath, '미분류');
    },
    thumbnailUrl() {
      const fileId = this.course?.thumbnailFileId;
      return fileId ? storedFileContentUrl(fileId) : '';
    },
    tagList() {
      // ⚠ tags 는 detail 객체 아래에 중첩돼 있다 — 최상위에서 평탄하게 읽으면 항상 undefined
      return String(this.course?.detail?.tags || '')
        .split(/[\s,]+/)
        .filter(Boolean)
        .map((tag) => (tag.startsWith('#') ? tag : `#${tag}`));
    },
    tuitionAmountText() {
      const d = this.course?.detail;
      return d ? tuitionText(d.tuitionType, d.tuitionAmount) : '-';
    },
    materialFeeText() {
      const d = this.course?.detail;
      return d ? tuitionText(d.materialFeeType, d.materialFeeAmount) : '-';
    },
    metaRows() {
      const c = this.course || {};
      return [
        { label: '모집 기간', value: this.recruitPeriod },
        { label: '학습 기간', value: this.learningPeriod },
        { label: '강의방식', value: educationTypeLabel(c.educationType) || '-' },
        { label: '정원', value: c.capacity ? `${c.capacity}명` : '-' },
        { label: '상태', value: statusLabel(c.status) || '-', accent: true },
      ];
    },
    recruitPeriod() {
      const c = this.course || {};
      const base = this.period(c.recruitStartDate, c.recruitEndDate);
      if (base === '-' || !c.recruitEndDate) return base;
      const left = daysFromToday(c.recruitEndDate);
      if (left === null) return base;
      return left < 0 ? `${base} (마감)` : `${base} (${left}일 남음)`;
    },
    learningPeriod() {
      const c = this.course || {};
      const base = this.period(c.startDate, c.endDate);
      if (base === '-' || !c.startDate || !c.endDate) return base;
      const days = daysBetween(c.startDate, c.endDate);
      return days === null ? base : `${base} (${days + 1}일)`;
    },
  },
  methods: {
    period(from, to) {
      if (!from && !to) return '-';
      return `${formatDateKst(from, '-')} ~ ${formatDateKst(to, '-')}`;
    },
  },
};
</script>

<style scoped>
.detail-header {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 214px;
  padding: 20px 16px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.detail-header__left {
  display: flex;
  width: 800px;
  max-width: 100%;
  flex-shrink: 0;
  gap: 16px;
  padding: 10px;
}

.detail-header__thumb {
  width: 184px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
}

.detail-header__thumb--empty {
  background: var(--db-border-avatar);
}

.detail-header__summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.detail-header__title {
  font-size: 24px;
  line-height: 32px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-header__line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 24px;
  font-size: 16px;
  line-height: 24px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.detail-header__sep {
  width: 1px;
  height: 14px;
  margin: 0 4px;
  background: var(--db-border);
}

.detail-header__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 5행 × 24 + 4 × 8 + padding 20 = 172 → 카드 212, min-height로 214 */
.detail-header__right {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 400px;
  margin: 0;
  padding: 10px;
}

.detail-header__row {
  display: flex;
  align-items: center;
  min-height: 24px;
}

.detail-header__label {
  width: 80px;
  flex-shrink: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-header__value {
  margin: 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 400;
  color: var(--db-text-sub);
}

.detail-header__value.accent {
  font-weight: 700;
  color: var(--db-accent);
}

@media (max-width: 1023px) {
  .detail-header__right {
    width: 100%;
  }
}

/* 폰: 사진 옆에 남는 폭이 100px 남짓이라 제목이 한 글자씩 세로로 쌓였다 — 사진을 위로 올린다 */
@media (max-width: 599px) {
  .detail-header {
    min-height: 0;
    padding: 12px;
  }

  .detail-header__left {
    flex-direction: column;
    padding: 4px;
  }

  .detail-header__thumb {
    width: 100%;
    height: auto;
    aspect-ratio: 184 / 120;
  }

  .detail-header__title {
    font-size: 20px;
    line-height: 28px;
  }

  .detail-header__line,
  .detail-header__label,
  .detail-header__value {
    font-size: 14px;
    line-height: 22px;
  }

  .detail-header__right {
    padding: 4px;
  }
}
</style>
