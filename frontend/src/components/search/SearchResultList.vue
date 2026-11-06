<template>
  <!-- 통합검색 결과 한 묶음 — '전체' 요약과 종류별 탭이 같이 쓴다 -->
  <FaqAccordion v-if="kind === 'faqs'" :faqs="items" id-prefix="search-faq" />
  <ul v-else class="search-all__list">
    <li v-for="item in items" :key="item.id">
      <!-- 강좌 -->
      <router-link v-if="kind === 'courses'" :to="`/courses/${item.id}`" class="search-row">
        <span class="search-row__main">
          <span class="search-row__title">
            <template v-for="(part, i) in parts(item.title)" :key="i">
              <mark v-if="part.hit" class="search-row__hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
            </template>
          </span>
          <span class="search-row__meta">
            {{ [item.institutionName, pathText(item.classificationPath)].filter(Boolean).join(' · ') || '-' }}
          </span>
        </span>
        <span class="search-row__side">
          <!-- 강좌 찾기 카드와 같은 학습자 말(모집중·모집마감·진행중·종료) — '수강중'은 콘솔 말이었다 -->
          <span
            v-if="recruitLabel(item)"
            class="search-row__state"
            :class="{ 'search-row__state--open': item.status === 'RECRUITING' }"
          >
            {{ recruitLabel(item) }}
          </span>
          <span v-if="item.startDate" class="search-row__date">{{ formatDateKst(item.startDate) }} 개강</span>
        </span>
      </router-link>

      <!-- 기관: 기관 화면이 없어 그 기관 강좌 검색으로 보낸다 -->
      <router-link v-else-if="kind === 'institutions'" :to="{ path: '/courses', query: { q: item.name } }" class="search-row">
        <span class="search-row__main">
          <span class="search-row__title">
            <template v-for="(part, i) in parts(item.name)" :key="i">
              <mark v-if="part.hit" class="search-row__hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
            </template>
          </span>
          <span class="search-row__meta">{{ item.region || '-' }}</span>
        </span>
        <span class="search-row__side search-row__hint">이 기관 강좌 보기</span>
      </router-link>

      <!-- 공지: 창이 아니라 공지 상세 화면으로(뒤로가기·주소 공유가 된다) -->
      <router-link v-else :to="`/notices/${item.id}`" class="search-row">
        <span class="search-row__main">
          <span class="search-row__title">
            <template v-for="(part, i) in parts(item.title)" :key="i">
              <mark v-if="part.hit" class="search-row__hit">{{ part.text }}</mark><template v-else>{{ part.text }}</template>
            </template>
          </span>
        </span>
        <span v-if="item.publishedAt" class="search-row__side search-row__date">{{ formatDateKst(item.publishedAt) }}</span>
      </router-link>
    </li>
  </ul>
</template>

<script>
import FaqAccordion from '@/components/FaqAccordion.vue';
import { classificationPathText } from '@/utils/classification';
import { courseRecruitState } from '@/utils/course-fields';
import { formatDateKst } from '@/utils/datetime';

export default {
  name: 'SearchResultList',
  components: { FaqAccordion },
  props: {
    /** 'courses' | 'institutions' | 'notices' | 'faqs' */
    kind: { type: String, required: true },
    items: { type: Array, default: () => [] },
    /** 제목에서 굵게 보일 검색어 */
    query: { type: String, default: '' },
  },
  methods: {
    formatDateKst,
    pathText(path) {
      return classificationPathText(path, '');
    },
    recruitLabel(item) {
      return courseRecruitState({ status: item.status }).label;
    },
    /** 제목을 검색어 앞뒤로 쪼갠다(대소문자 무시) — v-html 없이 <mark> 로 표시하려고 */
    parts(text) {
      const value = String(text || '');
      const q = this.query.trim();
      if (!q) return [{ text: value, hit: false }];
      const lower = value.toLowerCase();
      const needle = q.toLowerCase();
      const out = [];
      let from = 0;
      let at = lower.indexOf(needle, from);
      while (at !== -1) {
        if (at > from) out.push({ text: value.slice(from, at), hit: false });
        out.push({ text: value.slice(at, at + q.length), hit: true });
        from = at + q.length;
        at = lower.indexOf(needle, from);
      }
      if (from < value.length) out.push({ text: value.slice(from), hit: false });
      return out;
    },
  },
};
</script>

<style scoped>
.search-all__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.search-all__list li + li {
  border-top: 1px solid var(--db-border);
}

.search-row {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
  padding: 14px 4px;
  text-align: left;
  text-decoration: none;
  color: inherit;
}

.search-row--button {
  border: 0;
  background: none;
  font: inherit;
  cursor: pointer;
}

.search-row:hover .search-row__title {
  color: var(--db-accent);
}

.search-row:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.search-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.search-row__title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.search-row__meta {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  color: var(--db-text-sub);
}

.search-row__side {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.search-row__date,
.search-row__hint {
  font-size: 13px;
  color: var(--db-text-sub);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.search-row__hint {
  color: var(--db-accent);
}

.search-row__hit {
  padding: 0;
  font-weight: 800;
  color: var(--db-accent);
  background: none;
}

.search-row__state {
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  color: var(--db-text-sub);
  background: var(--db-bg-subtle);
}

.search-row__state--open {
  color: var(--db-accent);
  background: var(--db-accent-soft);
}

@media (max-width: 599px) {
  .search-row__date {
    display: none;
  }
}
</style>
