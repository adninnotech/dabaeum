<template>
  <q-card flat bordered class="course-card column full-height cursor-pointer" @click="goDetail">
    <div class="course-card__bg" :style="{ backgroundImage: `url(${course.image})` }" />
    <div class="course-card__gradient" />

    <div class="absolute-top-left q-pa-sm row q-gutter-xs course-card__badges">
      <q-badge
        v-for="badge in visibleBadges"
        :key="badge"
        :color="badgeColor(badge)"
        text-color="white"
      >
        {{ badge }}
      </q-badge>
    </div>

    <q-card-section class="col q-pb-none course-card__body">
      <div class="text-subtitle1 text-weight-bold ellipsis-2-lines">
        {{ course.title }}
      </div>
      <div class="text-grey-7 q-mt-xs">{{ course.institution }}</div>
      <div class="text-caption text-grey-8 q-mt-sm">
        <q-icon name="schedule" size="14px" class="q-mr-xs" />
        {{ course.duration }} · {{ course.level }}
      </div>
      <div class="text-caption text-grey-8 q-mt-xs">
        <q-icon name="event" size="14px" class="q-mr-xs" />
        {{ course.recruitStatus }} ~ {{ course.recruitEnd }}
      </div>
      <div class="text-caption text-primary text-weight-medium q-mt-xs">
        {{ course.priceLabel }}
      </div>
    </q-card-section>

    <q-card-actions align="right" class="q-pt-none course-card__actions">
      <q-btn
        flat
        round
        dense
        :icon="bookmarked ? 'bookmark' : 'bookmark_border'"
        color="primary"
        @click.stop="bookmarked = !bookmarked"
      />
    </q-card-actions>
  </q-card>
</template>

<script>
export default {
  name: 'CourseCard',
  props: {
    course: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      bookmarked: false,
    };
  },
  computed: {
    visibleBadges() {
      return (this.course.badges || []).filter((b) => b !== '학점은행제 인정');
    },
  },
  methods: {
    badgeColor(badge) {
      if (badge === '인기') return 'positive';
      if (badge === '추천') return 'warning';
      if (badge === '신규') return 'info';
      if (badge === '온라인') return 'info';
      if (badge === '오프라인') return 'teal';
      if (badge === '혼합') return 'deep-purple';
      return 'primary';
    },
    goDetail() {
      this.$router.push(`/courses/${this.course.id}`);
    },
  },
};
</script>

<style scoped lang="scss">
.course-card {
  position: relative;
  isolation: isolate;
  border-radius: 12px;
  overflow: hidden;
  min-height: 280px;
  background: transparent;
  transition:
    box-shadow 0.2s ease,
    transform 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(21, 61, 122, 0.12);
  }
}

.course-card__bg {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-position: center top;
  background-size: cover;
  background-repeat: no-repeat;
}

.course-card__gradient {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.08) 0%,
    rgba(255, 255, 255, 0.28) 28%,
    rgba(255, 255, 255, 0.72) 52%,
    rgba(255, 255, 255, 0.94) 72%,
    #ffffff 88%
  );
}

.course-card__badges {
  z-index: 3;
}

.course-card__body,
.course-card__actions {
  position: relative;
  z-index: 2;
  background: transparent;
}

.course-card__body {
  margin-top: auto;
  padding-top: 96px;
}

.ellipsis-2-lines {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
}
</style>
