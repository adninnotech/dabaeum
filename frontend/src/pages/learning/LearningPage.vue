<template>
  <q-page padding class="bg-grey-1">
    <div class="page-wrap">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">학습관리</div>
      </div>
      <q-card flat bordered>
        <q-tabs v-model="tab" align="left" active-color="primary" indicator-color="primary" @update:model-value="syncTab">
          <q-tab v-for="item in tabs" :key="item.name" :name="item.name" :label="item.label" />
        </q-tabs>
        <q-separator />
        <q-tab-panels v-model="tab" animated>
          <q-tab-panel name="courses">
            <div v-for="course in myCourses" :key="course.id" class="course-row row items-center q-col-gutter-md">
              <div class="col-12 col-sm-auto"><q-img :src="course.image" width="150px" height="96px" class="rounded-borders" /></div>
              <div class="col"><q-badge color="primary" outline>{{ course.status }}</q-badge><div class="text-subtitle1 text-weight-bold q-mt-xs">{{ course.title }}</div><div class="text-caption text-grey-7">{{ course.institution }} · {{ course.period }}</div></div>
              <div class="col-auto"><q-btn outline color="primary" :label="course.action" @click="openCourse(course.courseId)" /></div>
            </div>
          </q-tab-panel>
          <q-tab-panel name="interests">
            <div v-if="interests.length">
              <div v-for="course in interests" :key="course.id" class="course-row row items-center q-col-gutter-md">
                <div class="col"><div class="text-subtitle1 text-weight-bold">{{ course.title }}</div><div class="text-caption text-grey-7">{{ course.institution }} · {{ course.period }}</div></div>
                <q-btn flat color="negative" icon="delete_outline" label="관심 해제" @click="removeInterest(course.id)" />
              </div>
            </div>
            <empty-state v-else />
          </q-tab-panel>
          <q-tab-panel name="inquiries">
            <q-list v-if="inquiries.length" separator><q-item v-for="item in inquiries" :key="item.id"><q-item-section><q-item-label>{{ item.title }}</q-item-label><q-item-label caption>{{ item.courseTitle }} · {{ $formatDateTimeKst(item.createdAt) }}</q-item-label></q-item-section><q-item-section side><q-badge color="orange">{{ item.status }}</q-badge></q-item-section></q-item></q-list>
            <empty-state v-else />
          </q-tab-panel>
          <q-tab-panel name="reviews">
            <q-list v-if="reviews.length" separator><q-item v-for="item in reviews" :key="item.id"><q-item-section><q-item-label class="text-weight-bold">{{ item.courseTitle }}</q-item-label><q-rating :model-value="item.rating" readonly color="amber" size="18px" /><q-item-label caption>{{ item.content }} · {{ $formatDateTimeKst(item.createdAt) }}</q-item-label></q-item-section></q-item></q-list>
            <empty-state v-else />
          </q-tab-panel>
        </q-tab-panels>
      </q-card>
    </div>
  </q-page>
</template>

<script>
import { MOCK_INTEREST_COURSES, MOCK_INQUIRIES, MOCK_MY_COURSES, MOCK_REVIEWS } from '@/data/ui-mock.js';

const EmptyState = { name: 'EmptyState', template: '<div class="text-center q-pa-xl text-grey-6"><q-icon name="inbox" size="48px" /><div class="q-mt-sm">등록된 내용이 없습니다.</div></div>' };

export default {
  name: 'LearningPage',
  components: { EmptyState },
  data() {
    const available = ['courses', 'interests', 'inquiries', 'reviews'];
    return {
      tab: available.includes(this.$route.query.tab) ? this.$route.query.tab : 'courses',
      tabs: [{ name: 'courses', label: '나의 강의' }, { name: 'interests', label: '관심 강의' }, { name: 'inquiries', label: '나의 문의' }, { name: 'reviews', label: '나의 수강평' }],
      myCourses: MOCK_MY_COURSES,
      interests: [...MOCK_INTEREST_COURSES],
      inquiries: MOCK_INQUIRIES,
      reviews: MOCK_REVIEWS,
    };
  },
  watch: {
    '$route.query.tab'(value) {
      this.tab = this.tabs.some((item) => item.name === value) ? value : 'courses';
    },
  },
  methods: {
    syncTab(value) { this.$router.replace({ path: '/learning', query: value === 'courses' ? {} : { tab: value } }); },
    removeInterest(id) { this.interests = this.interests.filter((item) => item.id !== id); this.$q.notify({ type: 'info', message: '관심 강의에서 삭제했습니다.' }); },
    openCourse(id) { this.$router.push(`/courses/${id}`); },
  },
};
</script>

<style scoped>
.page-wrap { max-width: 1100px; margin: 0 auto; }
.course-row { padding: 20px 4px; border-bottom: 1px solid #eee; }
.course-row:last-child { border-bottom: 0; }
</style>
