<template>
  <q-page padding>
    <div class="page-inner">
      <q-btn
        flat
        dense
        color="primary"
        icon="arrow_back"
        label="목록"
        class="q-mb-md"
        @click="$router.push('/enrollment')"
      />

      <q-banner v-if="errorMessage" class="bg-red-1 text-negative q-mb-md rounded-borders">
        {{ errorMessage }}
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadDetail" />
        </template>
      </q-banner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <template v-if="enrollment">
          <div class="row items-start justify-between q-mb-md">
            <div>
              <div class="text-h5 text-weight-bold">{{ courseTitle }}</div>
              <div class="text-grey-7">Enrollment ID: {{ enrollment.id }}</div>
            </div>
            <div class="row q-gutter-sm">
              <q-btn
                v-if="canCancel"
                outline
                color="negative"
                label="신청 취소"
                :loading="acting"
                @click="onCancel"
              />
              <q-btn
                v-if="canWithdraw"
                outline
                color="warning"
                label="수강 철회"
                :loading="acting"
                @click="onWithdraw"
              />
            </div>
          </div>

          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-6">
              <q-card flat bordered>
                <q-card-section class="text-subtitle1 text-weight-bold">신청 정보</q-card-section>
                <q-separator />
                <q-list>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>상태</q-item-label>
                      <q-item-label>
                        <StatusBadge :status="enrollment.status" />
                      </q-item-label>
                    </q-item-section>
                  </q-item>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>신청유형</q-item-label>
                      <q-item-label>{{ enrollment.applicationType || '-' }}</q-item-label>
                    </q-item-section>
                  </q-item>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>신청자</q-item-label>
                      <q-item-label>{{ applicantLabel }}</q-item-label>
                    </q-item-section>
                  </q-item>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>신청일시</q-item-label>
                      <q-item-label>{{ $formatDateTimeKst(enrollment.appliedAt || enrollment.createdAt) }}</q-item-label>
                    </q-item-section>
                  </q-item>
                </q-list>
              </q-card>
            </div>
            <div class="col-12 col-md-6">
              <q-card flat bordered>
                <q-card-section class="text-subtitle1 text-weight-bold">출결 요약</q-card-section>
                <q-separator />
                <q-card-section>
                  <div v-if="summary">
                    <div class="text-h4 text-primary text-weight-bold">
                      {{ Math.round((summary.attendanceRate || 0) * 100) }}%
                    </div>
                    <div class="text-caption text-grey-7">GET /enrollments/{id}/attendance-summary</div>
                    <div class="row q-col-gutter-sm q-mt-md">
                      <div class="col-6">출석 {{ summary.presentCount ?? 0 }}</div>
                      <div class="col-6">지각 {{ summary.lateCount ?? 0 }}</div>
                      <div class="col-6">결석 {{ summary.absentCount ?? 0 }}</div>
                      <div class="col-6">공결 {{ summary.excusedCount ?? 0 }}</div>
                    </div>
                  </div>
                  <div v-else class="text-grey-7">출결 요약 정보가 없습니다.</div>
                </q-card-section>
              </q-card>
            </div>
          </div>

          <q-card flat bordered class="q-mt-md">
            <q-card-section class="row items-center justify-between">
              <div class="text-subtitle1 text-weight-bold">이수 상태</div>
              <q-btn
                outline
                color="primary"
                label="출석 QR 스캔"
                @click="
                  $router.push({
                    path: '/attendance/scan',
                    query: { enrollmentId: enrollment.id },
                  })
                "
              />
            </q-card-section>
            <q-separator />
            <q-card-section>
              <div v-if="completion">
                <StatusBadge :status="completion.status" />
                <div class="q-mt-sm">
                  평가 출석률: {{ Math.round((completion.attendanceRate || 0) * 100) }}%
                </div>
                <div>이수 분: {{ completion.completedMinutes || '-' }}분</div>
                <div>학점: {{ completion.creditValue || '-' }}</div>
              </div>
              <div v-else class="text-grey-7">이수 정보가 없습니다.</div>
            </q-card-section>
          </q-card>
        </template>

        <div v-else-if="!loading" class="text-center q-pa-xl text-grey-7">
          신청 정보를 찾을 수 없습니다.
        </div>
      </div>
    </div>
  </q-page>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import { getAttendanceSummary } from '@/services/attendance-api';
import { getCompletion } from '@/services/completion-api';
import { getCourse } from '@/services/course-api';
import {
  cancelEnrollment,
  fetchUserCached,
  getEnrollment,
  withdrawEnrollment,
} from '@/services/enrollment-api';
import { formatUserDisplayName } from '@/utils/user-display';

export default {
  components: { StatusBadge },
  name: 'EnrollmentDetailPage',
  data() {
    return {
      enrollment: null,
      applicantUser: null,
      summary: null,
      completion: null,
      courseTitle: '',
      loading: false,
      acting: false,
      errorMessage: '',
    };
  },
  computed: {
    canCancel() {
      const status = this.enrollment?.status;
      return status === 'APPLIED' || status === 'WAITLISTED';
    },
    canWithdraw() {
      return this.enrollment?.status === 'APPROVED';
    },
    applicantLabel() {
      return formatUserDisplayName(
        this.applicantUser || { userId: this.enrollment?.userId, id: this.enrollment?.userId },
      );
    },
  },
  watch: {
    '$route.params.enrollmentId'() {
      this.loadDetail();
    },
  },
  mounted() {
    this.loadDetail();
  },
  methods: {
    async loadDetail() {
      const enrollmentId = this.$route.params.enrollmentId;
      if (!enrollmentId) {
        this.enrollment = null;
        this.errorMessage = '수강신청 ID가 없습니다.';
        return;
      }

      this.loading = true;
      this.errorMessage = '';
      this.enrollment = null;
      this.applicantUser = null;
      this.summary = null;
      this.completion = null;
      this.courseTitle = '';

      try {
        const enrollment = await getEnrollment(enrollmentId);
        this.enrollment = enrollment;
        this.courseTitle = enrollment.courseTitle || enrollment.courseCode || '수강신청 상세';

        if (enrollment.userId) {
          this.applicantUser = await fetchUserCached(enrollment.userId);
        }

        if (enrollment.courseId) {
          try {
            const course = await getCourse(enrollment.courseId);
            this.courseTitle = course?.title || this.courseTitle;
          } catch {
            // 제목은 enrollment 필드로 대체
          }
        }

        try {
          this.summary = await getAttendanceSummary(enrollmentId);
        } catch {
          this.summary = null;
        }

        try {
          this.completion = await getCompletion(enrollmentId);
        } catch {
          this.completion = null;
        }
      } catch (error) {
        this.enrollment = null;
        this.errorMessage = error.message || '수강신청 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    async onCancel() {
      if (!this.enrollment?.id) return;
      this.acting = true;
      try {
        await cancelEnrollment(this.enrollment.id);
        this.$q.notify({ type: 'info', message: '신청이 취소되었습니다.', position: 'top' });
        await this.loadDetail();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '취소에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.acting = false;
      }
    },
    async onWithdraw() {
      if (!this.enrollment?.id) return;
      this.acting = true;
      try {
        await withdrawEnrollment(this.enrollment.id);
        this.$q.notify({ type: 'info', message: '수강이 철회되었습니다.', position: 'top' });
        await this.loadDetail();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '철회에 실패했습니다.',
          position: 'top',
        });
      } finally {
        this.acting = false;
      }
    },
  },
};
</script>

<style scoped>
.page-inner {
  max-width: 960px;
  margin: 0 auto;
}
.rounded-borders {
  border-radius: 12px;
}
</style>
