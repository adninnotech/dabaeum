<template>
  <q-page padding class="page">
    <div class="page-inner">
      <q-btn flat dense color="primary" icon="arrow_back" label="목록" class="q-mb-md" @click="goList" />

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadDetail" />
        </template>
      </q-banner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <template v-if="course">
          <div class="row q-col-gutter-lg">
            <div class="col-12 col-md-8">
              <q-img :src="course.image" ratio="16/9" class="rounded-borders" />
              <div class="text-h5 text-weight-bold q-mt-md">{{ course.title }}</div>
              <div class="text-grey-7 q-mt-xs">{{ course.institutionName }} · {{ course.courseCode }}</div>
              <div class="row q-gutter-xs q-mt-sm">
                <StatusBadge :status="course.status" />
                <EducationTypeBadge :type="course.educationType" outline />
                <q-badge v-if="course.creditBankEligible" outline color="positive">
                  학점은행제 {{ course.creditValue }}학점
                </q-badge>
              </div>
              <p class="q-mt-md text-body1">{{ course.description }}</p>

              <q-list bordered class="rounded-borders q-mt-md">
                <q-item>
                  <q-item-section>
                    <q-item-label caption>모집기간</q-item-label>
                    <q-item-label>{{ $formatDateKst(course.recruitStartDate) }} ~ {{ $formatDateKst(course.recruitEndDate) }}</q-item-label>
                  </q-item-section>
                </q-item>
                <q-item>
                  <q-item-section>
                    <q-item-label caption>학습기간</q-item-label>
                    <q-item-label>{{ $formatDateKst(course.startDate) }} ~ {{ $formatDateKst(course.endDate) }}</q-item-label>
                  </q-item-section>
                </q-item>
                <q-item>
                  <q-item-section>
                    <q-item-label caption>정원 / 장소</q-item-label>
                    <q-item-label>{{ course.capacity }}명 · {{ course.location || '-' }}</q-item-label>
                  </q-item-section>
                </q-item>
              </q-list>

              <div class="text-subtitle1 text-weight-bold q-mt-lg q-mb-sm">배정 강사</div>
              <q-list bordered class="rounded-borders q-mb-md">
                <q-item v-if="instructors.length === 0">
                  <q-item-section class="text-grey-7">배정된 강사가 없습니다.</q-item-section>
                </q-item>
                <q-item v-for="item in instructors" :key="item.userId">
                  <q-item-section>
                    <q-item-label>{{ item.instructorName || item.userId }}</q-item-label>
                    <q-item-label caption>{{ item.instructorEmail || '-' }}</q-item-label>
                  </q-item-section>
                  <q-item-section side>
                    <q-badge :color="item.role === 'MAIN' ? 'primary' : 'grey'">{{ item.role }}</q-badge>
                  </q-item-section>
                </q-item>
              </q-list>

              <div class="text-subtitle1 text-weight-bold q-mt-lg q-mb-sm">회차 (Course Session)</div>
              <q-markup-table flat bordered>
                <thead>
                  <tr>
                    <th class="text-left">회차</th>
                    <th class="text-left">일정</th>
                    <th class="text-left">장소</th>
                    <th class="text-left">상태</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="sessions.length === 0">
                    <td colspan="4" class="text-grey-7">등록된 회차가 없습니다.</td>
                  </tr>
                  <tr v-for="session in sessions" :key="session.id">
                    <td>{{ session.sessionNo }}</td>
                    <td>{{ formatRange(session.startsAt, session.endsAt) }}</td>
                    <td>{{ session.location || '-' }}</td>
                    <td>
                      <StatusBadge :status="session.status" />
                    </td>
                  </tr>
                </tbody>
              </q-markup-table>
            </div>

            <div class="col-12 col-md-4">
              <q-card flat bordered class="sticky-card">
                <q-card-section>
                  <div class="text-subtitle1 text-weight-bold">수강신청</div>
                  <div class="text-caption text-grey-7 q-mt-xs">LEARNER 역할만 신청 가능</div>
                  <q-banner v-if="isLoggedIn && !canEnroll" dense class="bg-orange-1 text-orange-10 q-mt-sm">
                    LEARNER 역할이 없어 수강신청할 수 없습니다.
                  </q-banner>
                  <q-btn
                    class="full-width q-mt-md"
                    color="primary"
                    unelevated
                    label="수강신청하기"
                    :disable="!canApply"
                    :loading="applying"
                    @click="applyEnrollment"
                  />
                  <q-btn
                    v-if="canEnroll"
                    class="full-width q-mt-sm"
                    outline
                    color="primary"
                    label="내 수강신청 보기"
                    @click="$router.push('/enrollment')"
                  />
                </q-card-section>
              </q-card>
            </div>
          </div>
        </template>

        <div v-else-if="!loading" class="text-center q-pa-xl">
          <q-icon name="search_off" size="48px" color="grey-6" />
          <div class="text-subtitle1 q-mt-md">강좌를 찾을 수 없습니다.</div>
        </div>
      </div>
    </div>
  </q-page>
</template>

<script>
import { formatDateRangeKst } from '@/utils/datetime';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { mapState } from 'pinia';
import { getCourse } from '@/services/course-api';
import { createEnrollment } from '@/services/enrollment-api';
import { listCourseInstructors } from '@/services/instructor-api';
import { getInstitution } from '@/services/institution-api';
import { listCourseSessions } from '@/services/session-api';
import { useAuthStore } from '@/stores/auth-store';
import { toCourseCard } from '@/utils/course-mapper';

export default {
  components: { EducationTypeBadge, StatusBadge },
  name: 'CourseDetailPage',
  data() {
    return {
      course: null,
      sessions: [],
      instructors: [],
      loading: false,
      applying: false,
      errorMessage: '',
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'canEnroll', 'userId']),
    canApply() {
      if (this.course?.status !== 'RECRUITING') return false;
      // 비로그인: 클릭 시 로그인으로 이동 / 로그인: LEARNER만
      return !this.isLoggedIn || this.canEnroll;
    },
  },
  watch: {
    '$route.params.courseId': {
      immediate: false,
      handler() {
        this.loadDetail();
      },
    },
  },
  mounted() {
    this.loadDetail();
  },
  methods: {
    goList() {
      this.$router.push('/');
    },
    formatRange(start, end) {
      return formatDateRangeKst(start, end);
    },
    async loadDetail() {
      const courseId = this.$route.params.courseId;
      if (!courseId) {
        this.course = null;
        this.sessions = [];
        this.errorMessage = '강좌 ID가 없습니다.';
        return;
      }

      this.loading = true;
      this.errorMessage = '';
      this.course = null;
      this.sessions = [];
      this.instructors = [];

      try {
        const raw = await getCourse(courseId);
        let institutionName = '';
        if (raw?.institutionId) {
          try {
            const institution = await getInstitution(raw.institutionId);
            institutionName = institution?.name || '';
          } catch {
            institutionName = '';
          }
        }
        this.course = toCourseCard(raw, institutionName);

        try {
          const assigned = await listCourseInstructors(courseId);
          this.instructors = Array.isArray(assigned) ? assigned : [];
        } catch {
          this.instructors = [];
        }

        try {
          const sessionPage = await listCourseSessions(courseId, {
            page: 0,
            size: 50,
            sort: 'sessionNo,asc',
          });
          this.sessions = Array.isArray(sessionPage.data) ? sessionPage.data : [];
        } catch {
          this.sessions = [];
        }
      } catch (error) {
        this.course = null;
        this.errorMessage = error.message || '강좌 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async applyEnrollment() {
      const auth = useAuthStore();
      if (!auth.isLoggedIn) {
        this.$router.push({ path: '/login', query: { redirect: this.$route.fullPath } });
        return;
      }
      if (!auth.canEnroll) {
        this.$q.notify({
          type: 'warning',
          message: 'LEARNER 역할만 수강신청할 수 있습니다.',
          position: 'top',
        });
        return;
      }
      if (!this.course?.id || this.course.status !== 'RECRUITING') return;

      this.applying = true;
      try {
        await createEnrollment(this.course.id, {
          userId: auth.userId,
          applicationType: 'SELF',
        });
        this.$q.notify({
          type: 'positive',
          message: '수강신청이 접수되었습니다.',
          position: 'top',
        });
        this.$router.push('/enrollment');
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '수강신청에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.applying = false;
      }
    },
  },
};
</script>

<style scoped lang="scss">
.page-inner {
  max-width: 1100px;
  margin: 0 auto;
}

.sticky-card {
  border-radius: 14px;
  position: sticky;
  top: 88px;
}

.rounded-borders {
  border-radius: 12px;
}
</style>
