<template>
  <!-- 카드 전체 클릭은 제목 링크를 카드 크기로 늘려(stretched link) 만든다 —
       article @click 은 키보드로 닿지 않았다. 하트 버튼은 z-index 로 링크 위에 둔다.
       사진 위엔 모집 배지 하나만 — 수업 방식·하트·마감까지 얹어 사진이 가려졌다(dev-event 카드 참고) -->
  <article
    class="course-card"
    :class="{ 'course-card--fluid': fluid, 'course-card--closed': recruit.label && !recruit.open }"
  >
    <div class="course-card__thumb">
      <img v-if="thumbnailUrl" :src="thumbnailUrl" :alt="course.title" />
      <div v-else class="course-card__thumb-empty">
        <q-icon name="image" size="32px" class="course-card__thumb-icon" />
      </div>

      <div v-if="rank || thumbBadge" class="course-card__badges">
        <!-- 인기 순위 — 배지와 한 줄로 -->
        <span v-if="rank" class="course-card__rank" :class="{ 'course-card__rank--top': rank <= 3 }" :aria-label="`${rank}위`">
          {{ rank }}
        </span>
        <!-- 앞말('모집중 ·')은 폰의 작은 사진에서 숨긴다 — 분홍 배지가 이미 '모집 중·마감 임박' 이다 -->
        <span
          v-if="thumbBadge"
          class="course-card__badge"
          :class="`course-card__badge--${thumbBadge.tone}`"
          :aria-label="thumbBadge.prefix ? `${thumbBadge.prefix} ${thumbBadge.text}` : null"
        >
          <span v-if="thumbBadge.prefix" class="course-card__badge-prefix" aria-hidden="true">{{ thumbBadge.prefix }}</span>
          {{ thumbBadge.text }}
        </span>
      </div>
    </div>

    <div class="course-card__body">
      <!-- 기관 줄 오른쪽에 하트 — 사진 위에서 내렸다 -->
      <div class="course-card__top">
        <span class="course-card__institution">{{ course.institutionName || '-' }}</span>
        <button
          type="button"
          class="course-card__like"
          :class="{ 'course-card__like--on': interested }"
          :aria-label="interested ? '관심 강좌에서 빼기' : '관심 강좌에 담기'"
          :aria-pressed="interested ? 'true' : 'false'"
          @click.stop="toggleLike"
        >
          <q-icon :name="interested ? 'favorite' : 'favorite_border'" size="22px" aria-hidden="true" />
        </button>
      </div>
      <h3 class="course-card__title">
        <router-link :to="detailPath" class="course-card__link">{{ course.title }}</router-link>
      </h3>

      <div class="course-card__dates">
        <span v-if="recruit.deadline && recruitEndLabel" class="course-card__date">
          <span class="course-card__date-key">마감</span>{{ recruitEndLabel }}
        </span>
        <span class="course-card__date">
          <span class="course-card__date-key">개강</span>{{ startLabel }}
        </span>
      </div>

      <!-- 수업 방식·교육비·태그를 같은 회색 칩 한 줄로 -->
      <div v-if="chips.length" class="course-card__chips">
        <span
          v-for="chip in chips"
          :key="chip.key"
          class="course-card__chip"
          :class="{ 'course-card__chip--tag': chip.tag, 'course-card__chip--type': chip.key === 'type' }"
        >{{ chip.text }}</span>
      </div>

      <div class="course-card__seats">
        <q-icon name="group" size="18px" aria-hidden="true" />
        <span>{{ course.enrolledCount ?? '-' }} / {{ course.capacity ?? '-' }}명</span>
      </div>
      <!-- 카드 아래 덧붙이는 한 줄(인기 강좌의 신청·조회 수) — 카드 밖에 두면 다음 카드에 붙어 보였다 -->
      <div v-if="$slots.footer" class="course-card__footer"><slot name="footer" /></div>
    </div>
  </article>
</template>

<script>
import { storedFileContentUrl } from '@/services/file-api';
import { courseRecruitState } from '@/utils/course-fields';
import { EDUCATION_TYPE_LABEL } from '@/utils/status';

/* 카드 칩 줄 — 넘치면 한 줄에서 잘리니 태그는 2개까지 */
const CARD_TAG_LIMIT = 2;
const TUITION_CHIP = { FREE: '무료', PAID: '유료' };

function monthDay(value) {
  const date = String(value || '').slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(date) ? `${date.slice(5, 7)}.${date.slice(8, 10)}` : '';
}

export default {
  name: 'CourseCard',
  props: {
    course: {
      type: Object,
      required: true,
    },
    /* 부모가 내 관심 목록(course-interest mixin)으로 정해 넘겨준다. 카드가 따로 상태를 들지 않는다 —
     * 비로그인 클릭·API 실패 때 하트만 채워진 채 남던 문제를 막는다 */
    interested: { type: Boolean, default: false },
    /* 그리드 칸을 채운다(고정 260px 대신). 폰에서는 작은 썸네일 + 글의 가로 행으로 바뀐다 — 강좌 찾기 */
    fluid: { type: Boolean, default: false },
    /** 인기 순위(1부터) — 있으면 왼쪽 위에 번호 칸 */
    rank: { type: Number, default: 0 },
  },
  emits: ['toggle-interest'],
  computed: {
    recruit() {
      return courseRecruitState(this.course);
    },
    /** 사진 위 배지 하나 — 마감 임박 '모집중 · 3일 남음'(분홍) · 모집중(파랑) · 그 밖(어두운 반투명) */
    thumbBadge() {
      const { open, label, deadline } = this.recruit;
      if (!label) return null;
      // 마감 임박도 '모집중' 을 앞에. 'D-3' 같은 줄임말 대신 '3일 남음' — 중장년 이용자에게 바로 읽히게
      if (open && deadline?.urgent) {
        return {
          prefix: '모집중 ·',
          text: deadline.days === 0 ? '오늘 마감' : `${deadline.days}일 남음`,
          tone: 'urgent',
        };
      }
      return { text: label, tone: open ? 'open' : 'muted' };
    },
    recruitEndLabel() {
      return monthDay(this.course.recruitEndDate);
    },
    detailPath() {
      return `/courses/${this.course.id}`;
    },
    thumbnailUrl() {
      if (this.course.thumbnailUrl) return this.course.thumbnailUrl;
      const fileId = this.course.thumbnailFileId;
      return fileId ? storedFileContentUrl(fileId) : '';
    },
    chips() {
      const type = EDUCATION_TYPE_LABEL[this.course.educationType];
      const tuition = TUITION_CHIP[this.course.detail?.tuitionType];
      const tags = String(this.course.detail?.tags || '')
        .split(/[\s,]+/)
        .map((tag) => tag.replace(/^#+/, ''))
        .filter(Boolean)
        .slice(0, CARD_TAG_LIMIT)
        .map((tag) => ({ key: `tag-${tag}`, text: `#${tag}`, tag: true }));
      return [
        ...(type ? [{ key: 'type', text: type }] : []),
        ...(tuition ? [{ key: 'tuition', text: tuition }] : []),
        ...tags,
      ];
    },
    startLabel() {
      return monthDay(this.course.startDate) || '미정';
    },
  },
  methods: {
    toggleLike() {
      this.$emit('toggle-interest', this.course.id);
    },
  },
};
</script>

<style scoped>
.course-card {
  position: relative;
  display: flex;
  flex-direction: column;
  /* 가로 스크롤 행(홈)에서 줄어들지 않게 — 그리드에서는 무시된다 */
  flex: 0 0 260px;
  width: 260px;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

/* 마우스를 올리면 카드가 살짝 뜨고 사진이 조금 다가오며 제목이 파래진다 — 누를 수 있는 판이라는 신호.
 * 터치 기기는 hover 가 눌린 채 남아 뺀다 */
@media (hover: hover) {
  .course-card {
    transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
  }

  .course-card__thumb img {
    transition: transform 0.35s cubic-bezier(0.2, 0.7, 0.2, 1);
  }

  .course-card__title,
  .course-card__like {
    transition: color 0.2s ease, background-color 0.2s ease;
  }

  .course-card:hover {
    transform: translateY(-3px);
    border-color: var(--db-accent);
    box-shadow: var(--db-shadow-card-hover);
  }

  .course-card:hover .course-card__thumb img {
    transform: scale(1.04);
  }

  .course-card:hover .course-card__title {
    color: var(--db-accent);
  }

  .course-card__like:hover {
    background: var(--db-bg-tag);
    color: var(--db-status-pink);
  }
}

@media (prefers-reduced-motion: reduce) {
  .course-card,
  .course-card__thumb img {
    transition: none;
  }

  .course-card:hover,
  .course-card:hover .course-card__thumb img {
    transform: none;
  }
}

.course-card__thumb {
  position: relative;
  flex-shrink: 0;
  /* hover 때 커지는 사진이 본문으로 넘치지 않게 */
  overflow: hidden;
  width: 100%;
  height: 180px;
  background: var(--db-border-avatar);
}

.course-card__thumb img,
.course-card__thumb-empty {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.course-card__thumb-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.course-card__thumb-icon {
  color: var(--db-text-sub);
}

/* 모집이 끝난 강좌 — 사진을 어둡게 덮는다(배지는 그 위라 그대로 읽힌다) */
.course-card--closed .course-card__thumb::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--db-thumb-dim);
  pointer-events: none;
}

/* 사진 왼쪽 위 — 순위와 모집 배지 한 줄. 클릭은 늘린 링크로 흘려보낸다 */
.course-card__badges {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  pointer-events: none;
}

.course-card__rank {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border-radius: 6px;
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-title);
  background: var(--db-bg-white);
  box-shadow: var(--db-shadow-badge);
}

.course-card__rank--top {
  color: var(--db-on-color);
  background: var(--db-accent-fill);
}

.course-card__badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  color: var(--db-on-color);
}

.course-card__badge-prefix {
  margin-right: 4px;
}

.course-card__badge--open {
  background: var(--db-accent-fill);
}

.course-card__badge--urgent {
  background: var(--db-status-pink);
}

.course-card__badge--muted {
  background: var(--db-thumb-badge-muted);
}

.course-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px 16px;
}

.course-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  /* 하트(44px)가 줄 높이를 키우지 않게 위아래로 당긴다 */
  margin: -8px -10px -8px 0;
}

.course-card__institution {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 15px;
  font-weight: 500;
  color: var(--db-text-sub);
}

/* 늘린 링크(::after)보다 위 — 하트를 누르면 상세로 가지 않고 관심 강좌만 바뀐다 */
.course-card__like {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 중장년 이용자 — 누르는 자리 44px */
  width: 44px;
  height: 44px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--db-text-sub);
  cursor: pointer;
}

.course-card__like--on {
  color: var(--db-status-pink);
}

.course-card__like:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 0;
}

.course-card__title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  /* '수료 / 증 흐름'처럼 낱말 중간에서 꺾였다 — 낱말 단위로(아주 긴 낱말만 쪼갠다) */
  word-break: keep-all;
  overflow-wrap: anywhere;
  margin: 0;
  font-size: 20px;
  line-height: 26px;
  font-weight: 700;
  color: var(--db-text-title);
}

.course-card__link {
  color: inherit;
  text-decoration: none;
}

/* 카드 전체를 덮는 클릭 영역. h3 는 positioned 가 아니라 line-clamp 의 overflow 에 잘리지 않는다 */
.course-card__link::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: 8px;
}

.course-card__link:focus-visible {
  outline: none;
}

.course-card__link:focus-visible::after {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

/* '마감 10.18  개강 10.20' — 이름은 연하게, 날짜는 진하게 */
.course-card__dates {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 12px;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}

.course-card__date {
  white-space: nowrap;
}

.course-card__date-key {
  margin-right: 6px;
  font-weight: 500;
  color: var(--db-text-sub);
}

/* 한 줄 고정 — 들어가지 않는 칩은 둘째 줄로 밀려 통째로 숨는다('#…' 처럼 반쯤 잘린 칩을 보이지 않게) */
.course-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  height: 28px;
  overflow: hidden;
}

.course-card__chip {
  flex-shrink: 0;
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

.course-card__chip--type {
  font-weight: 700;
  color: var(--db-text-title);
}

.course-card__chip--tag {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  line-height: 28px;
}

.course-card__seats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-variant-numeric: tabular-nums;
  color: var(--db-text-body);
}

.course-card__footer {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--db-border);
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 그리드(강좌 찾기) — 칸 폭을 채운다 */
.course-card--fluid {
  flex: none;
  width: auto;
  min-width: 0;
}

/* 폰: 큰 사진 세로 카드는 한 화면에 1개뿐이었다 — 작은 썸네일 + 글의 가로 행(홈 '모집 중인 강좌'와 같은 꼴) */
@media (max-width: 599px) {
  .course-card--fluid {
    flex-direction: row;
    align-items: flex-start;
  }

  .course-card--fluid .course-card__thumb {
    width: 96px;
    height: 96px;
    margin: 12px 0 12px 12px;
    border-radius: 6px;
  }

  /* 작은 사진(96px) — 배지를 작게, 순위와 배지는 세로로 쌓아 사진 밖으로 나가지 않게 */
  .course-card--fluid .course-card__badges {
    top: 6px;
    left: 6px;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .course-card--fluid .course-card__rank {
    min-width: 24px;
    height: 24px;
    font-size: 13px;
  }

  /* 96px 사진 안에 들어가게 — '모집중 · 3일 남음' 은 13px 로도 사진보다 넓어 잘리고 사진을 덮었다 */
  .course-card--fluid .course-card__badge {
    height: 22px;
    padding: 0 7px;
    font-size: 12px;
  }

  .course-card--fluid .course-card__badge-prefix {
    display: none;
  }

  .course-card--fluid .course-card__body {
    flex: 1;
    min-width: 0;
    gap: 4px;
    padding: 10px 12px 12px;
  }

  .course-card--fluid .course-card__title {
    font-size: 16px;
    line-height: 22px;
  }

  /* 좁은 줄 — 수업 방식·교육비만 */
  .course-card--fluid .course-card__chip--tag {
    display: none;
  }

  .course-card--fluid .course-card__chips {
    height: 24px;
  }

  .course-card--fluid .course-card__chip {
    height: 24px;
    padding: 0 8px;
    font-size: 13px;
  }

  .course-card--fluid .course-card__dates,
  .course-card--fluid .course-card__seats {
    font-size: 14px;
  }
}
</style>
