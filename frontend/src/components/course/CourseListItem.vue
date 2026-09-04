<template>
  <q-card flat bordered class="course-list-item q-mb-md cursor-pointer" @click="goDetail">
    <q-card-section class="row q-col-gutter-md items-start">
      <div class="col-auto">
        <div class="course-list-item__thumb">
          <q-img
            :src="course.image"
            class="rounded-borders"
            width="108px"
            height="108px"
            fit="cover"
          />
          <q-badge v-if="hasCreditBadge" color="positive" class="course-list-item__credit-badge">
            학점은행제 인정
          </q-badge>
        </div>
      </div>

      <div class="col">
        <div class="row items-center justify-between no-wrap">
          <q-badge color="purple-1" text-color="purple-8" class="q-px-sm">
            {{ course.recruitStatus }}
          </q-badge>
          <q-btn
            flat
            round
            dense
            :icon="liked ? 'favorite' : 'favorite_border'"
            :color="liked ? 'negative' : 'grey-6'"
            @click.stop="liked = !liked"
          />
        </div>

        <div class="text-subtitle1 text-weight-bold q-mt-xs">{{ course.title }}</div>
        <div class="text-grey-7 text-body2">{{ course.institution }}</div>

        <div class="text-caption text-grey-8 q-mt-sm">
          <q-icon name="schedule" size="14px" class="q-mr-xs" />
          {{ course.period }}
          <span v-if="course.creditLabel"> | {{ course.creditLabel }}</span>
        </div>
        <div class="text-caption text-grey-8 q-mt-xs">
          <q-icon name="place" size="14px" class="q-mr-xs" />
          {{ course.location }}
        </div>
      </div>
    </q-card-section>
  </q-card>
</template>

<script>
export default {
  name: 'CourseListItem',
  props: {
    course: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      liked: !!this.course.liked,
    };
  },
  computed: {
    hasCreditBadge() {
      return (this.course.badges || []).includes('학점은행제 인정');
    },
  },
  methods: {
    goDetail() {
      this.$router.push(`/courses/${this.course.id}`);
    },
  },
};
</script>

<style scoped lang="scss">
.course-list-item {
  border-radius: 14px;
}

.course-list-item__thumb {
  position: relative;
}

.course-list-item__credit-badge {
  position: absolute;
  top: 6px;
  left: 6px;
  font-size: 10px;
}
</style>
