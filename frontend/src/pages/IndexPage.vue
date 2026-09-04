<template>
  <q-page class="course-search-page">
    <section v-if="$q.screen.gt.sm" class="hero-desktop">
      <div class="hero-desktop__inner row items-center q-col-gutter-lg">
        <div class="col-12 col-md-7">
          <h1 class="hero-title text-primary">내 삶을 바꾸는 배움의 시작, 다배움</h1>
          <p class="hero-sub text-grey-8">전국의 평생학습 강좌를 쉽고 빠르게 찾아보세요.</p>

          <div class="row q-col-gutter-sm items-center q-mt-md">
            <div class="col">
              <q-input
                v-model="keyword"
                outlined
                rounded
                dense
                placeholder="어떤 강좌를 찾고 계신가요?"
                class="hero-search"
                @keyup="onSearchKeyup"
              >
                <template #append>
                  <q-btn
                    color="primary"
                    unelevated
                    rounded
                    label="검색"
                    class="q-px-md"
                    :loading="loading"
                    @click="searchCourses"
                  />
                </template>
              </q-input>
            </div>
            <div class="col-auto">
              <q-btn
                outline
                color="primary"
                rounded
                icon-right="expand_more"
                label="상세검색"
                @click="showMobileFilter = true"
              />
            </div>
          </div>

          <div class="row items-center q-gutter-sm q-mt-md">
            <span class="text-caption text-grey-7">인기 검색어</span>
            <q-chip
              v-for="word in popularKeywords"
              :key="word"
              clickable
              outline
              color="grey-6"
              text-color="grey-8"
              size="sm"
              @click="applyPopularKeyword(word)"
            >
              {{ word }}
            </q-chip>
          </div>
        </div>

        <div class="col-12 col-md-5 text-center gt-sm">
          <div class="hero-illustration">
            <BrandLogo :size="88" />
            <div class="text-subtitle1 text-primary text-weight-bold q-mt-sm">Lifelong Learning</div>
          </div>
        </div>
      </div>
    </section>

    <section v-else class="q-pa-md">
      <q-input v-model="keyword" outlined dense placeholder="어떤 강좌를 찾고 계신가요?" @keyup="onSearchKeyup">
        <template #append>
          <q-btn
            dense
            unelevated
            color="primary"
            icon="search"
            class="q-px-sm"
            :loading="loading"
            @click="searchCourses"
          />
        </template>
      </q-input>

      <div class="row q-col-gutter-sm q-mt-sm">
        <div class="col">
          <q-select
            :model-value="filterRegion"
            dense
            outlined
            emit-value
            map-options
            :options="quickRegionOptions"
            @update:model-value="setFilterRegion"
          />
        </div>
        <div class="col">
          <q-select
            :model-value="filterField"
            dense
            outlined
            emit-value
            map-options
            :options="quickFieldOptions"
            @update:model-value="setFilterField"
          />
        </div>
        <div class="col">
          <q-select
            :model-value="filterCompletionType"
            dense
            outlined
            emit-value
            map-options
            :options="quickCompletionOptions"
            @update:model-value="setFilterCompletionType"
          />
        </div>
        <div class="col-auto">
          <q-btn outline color="primary" icon="filter_list" label="필터" @click="showMobileFilter = true" />
        </div>
      </div>
    </section>

    <div class="page-body" :class="$q.screen.gt.sm ? 'q-px-xl q-pb-xl' : 'q-px-md q-pb-lg'">
      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadCourses" />
        </template>
      </q-banner>

      <div class="row q-col-gutter-lg">
        <div v-if="$q.screen.gt.sm" class="col-12 col-md-3">
          <CourseFilterPanel :model-value="courseFilter" @update:model-value="onFilterUpdate" @search="searchCourses" />
        </div>

        <div class="col-12 col-md-9 relative-position">
          <q-inner-loading :showing="loading" />

          <div class="app-page-header row items-center justify-between q-mb-lg">
            <div class="text-h6 text-weight-bold">
              {{
                $q.screen.gt.sm
                  ? '추천 강좌'
                  : `총 ${filteredCourses.length.toLocaleString()}개의 강좌`
              }}
            </div>
            <div class="row items-center q-gutter-sm">
              <q-select
                v-if="$q.screen.lt.md"
                v-model="sortBy"
                dense
                borderless
                emit-value
                map-options
                :options="sortOptions"
                style="min-width: 100px"
              />
              <q-btn v-else flat dense color="primary" label="더보기 >" />
            </div>
          </div>

          <div class="row q-gutter-sm q-mb-md no-wrap scroll-x">
            <q-btn
              v-for="cat in courseCategories"
              :key="cat"
              :unelevated="selectedCategory === cat"
              :outline="selectedCategory !== cat"
              :color="selectedCategory === cat ? 'primary' : 'grey-6'"
              :text-color="selectedCategory === cat ? 'white' : 'grey-8'"
              rounded
              no-caps
              dense
              :label="cat"
              @click="selectedCategory = cat"
            />
          </div>

          <div v-if="!loading && !errorMessage && filteredCourses.length === 0" class="text-center q-pa-xl text-grey-7">
            조건에 맞는 강좌가 없습니다.
          </div>

          <div v-if="$q.screen.gt.sm" class="row q-col-gutter-md">
            <div v-for="course in filteredCourses" :key="course.id" class="col-12 col-sm-6 col-md-4">
              <CourseCard :course="course" />
            </div>
          </div>

          <div v-else>
            <CourseListItem v-for="course in filteredCourses" :key="course.id" :course="course" />
          </div>

          <div class="q-mt-lg flex flex-center">
            <q-btn
              outline
              color="grey-7"
              icon-right="expand_more"
              label="더 많은 강좌 보기"
              class="q-px-xl"
              :loading="loading"
              @click="loadCourses"
            />
          </div>
        </div>
      </div>
    </div>

    <q-dialog v-model="showMobileFilter" position="bottom">
      <q-card style="width: 100%; max-width: 560px">
        <CourseFilterPanel
          :model-value="courseFilter"
          @update:model-value="onFilterUpdate"
          @search="onMobileFilterSearch"
        />
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script>
import BrandLogo from '@/components/BrandLogo.vue';
import CourseCard from '@/components/course/CourseCard.vue';
import CourseFilterPanel from '@/components/course/CourseFilterPanel.vue';
import CourseListItem from '@/components/course/CourseListItem.vue';
import { courseCategories, popularKeywords } from '@/data/mockCourses';
import { listCourses } from '@/services/course-api';
import { listInstitutions } from '@/services/institution-api';
import { toCourseCard } from '@/utils/course-mapper';

export default {
  name: 'IndexPage',
  components: {
    BrandLogo,
    CourseCard,
    CourseFilterPanel,
    CourseListItem,
  },
  data() {
    return {
      keyword: '',
      selectedCategory: '전체',
      sortBy: 'latest',
      showMobileFilter: false,
      filterRegion: 'all',
      filterField: 'all',
      filterCompletionType: 'all',
      courseFilter: {
        region: 'all',
        field: 'all',
        completionType: 'all',
        recruitStatus: 'all',
        fee: 'all',
        startDate: '',
        endDate: '',
      },
      popularKeywords,
      courseCategories,
      sortOptions: [
        { label: '최신순', value: 'latest' },
        { label: '인기순', value: 'popular' },
      ],
      quickRegionOptions: [
        { label: '지역 / 전체', value: 'all' },
        { label: '서울', value: 'seoul' },
        { label: '부산', value: 'busan' },
      ],
      quickFieldOptions: [
        { label: '분야 / 전체', value: 'all' },
        { label: 'IT/디지털', value: 'it' },
        { label: '인문교양', value: 'humanities' },
      ],
      quickCompletionOptions: [
        { label: '이수학점 / 전체', value: 'all' },
        { label: '학점은행제', value: 'credit' },
      ],
      courses: [],
      institutionMap: {},
      loading: false,
      errorMessage: '',
    };
  },
  computed: {
    filteredCourses() {
      const keyword = String(this.keyword || '')
        .trim()
        .toLowerCase();
      const selectedCategory = this.selectedCategory;
      return this.courses.filter((course) => {
        const category = course.category || '';
        const byCategory = selectedCategory === '전체' || category === selectedCategory;
        const haystack = [
          course.title,
          course.institution,
          course.institutionName,
          course.category,
          course.courseCode,
          course.description,
        ]
          .filter(Boolean)
          .join(' ')
          .toLowerCase();
        const byKeyword = !keyword || haystack.includes(keyword);
        return byCategory && byKeyword;
      });
    },
  },
  mounted() {
    this.bootstrap();
  },
  methods: {
    async bootstrap() {
      await this.loadInstitutions();
      await this.loadCourses();
    },
    async loadInstitutions() {
      try {
        const result = await listInstitutions({ page: 0, size: 100, sort: 'createdAt,desc' });
        const map = {};
        (result.data || []).forEach((item) => {
          if (item && item.id) {
            map[item.id] = item.name || item.institutionName || '';
          }
        });
        this.institutionMap = map;
      } catch {
        this.institutionMap = {};
      }
    },
    async loadCourses() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listCourses({ page: 0, size: 50, sort: 'createdAt,desc' });
        const rows = Array.isArray(result.data) ? result.data : [];
        this.courses = rows.map((course) =>
          toCourseCard(course, this.institutionMap[course.institutionId] || ''),
        );
      } catch (error) {
        this.courses = [];
        this.errorMessage = error.message || '강좌 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    onSearchKeyup(event) {
      if (event && event.key === 'Enter') {
        this.searchCourses();
      }
    },
    searchCourses() {
      this.loadCourses();
    },
    applyPopularKeyword(word) {
      this.keyword = word;
      this.searchCourses();
    },
    onMobileFilterSearch() {
      this.showMobileFilter = false;
      this.searchCourses();
    },
    onFilterUpdate(value) {
      this.courseFilter = { ...value };
      this.filterRegion = value.region;
      this.filterField = value.field;
      this.filterCompletionType = value.completionType;
    },
    setFilterRegion(value) {
      this.filterRegion = value;
      this.courseFilter = { ...this.courseFilter, region: value };
    },
    setFilterField(value) {
      this.filterField = value;
      this.courseFilter = { ...this.courseFilter, field: value };
    },
    setFilterCompletionType(value) {
      this.filterCompletionType = value;
      this.courseFilter = { ...this.courseFilter, completionType: value };
    },
  },
};
</script>

<style scoped lang="scss">
.course-search-page {
  background:
    radial-gradient(circle at top right, rgba(47, 120, 214, 0.08), transparent 40%),
    linear-gradient(180deg, #f7faff 0%, #ffffff 42%);
}

.hero-desktop {
  padding: 40px 48px 8px;
}

.hero-desktop__inner {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-title {
  margin: 0;
  font-size: 2rem;
  line-height: 1.35;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.hero-sub {
  margin: 10px 0 0;
  font-size: 1.05rem;
}

.hero-illustration {
  min-height: 220px;
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(145deg, rgba(26, 71, 159, 0.08), rgba(47, 120, 214, 0.16)),
    #eef4ff;
}

.page-body {
  max-width: 1280px;
  margin: 0 auto;
}

.scroll-x {
  overflow-x: auto;
  padding-bottom: 4px;
}

.rounded-borders {
  border-radius: 12px;
}
</style>
