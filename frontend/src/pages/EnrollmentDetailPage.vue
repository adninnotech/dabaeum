<template>
  <q-page class="learner-page">
    <div class="learner-container learner-page__body">
      <!-- 학습관리와 같은 학습자 머리(h1). 신청 목록은 학습관리 "수강 신청" 탭에 있다 -->
      <LearnerPageHead
        :title="courseTitle || '수강신청 상세'"
        subtitle="신청 상태와 출결·이수 현황을 봅니다."
        :back="{ label: '학습관리', to: '/learning?tab=applications' }"
      >
        <template v-if="canCancel || canWithdraw || withdrawBlocked" #actions>
          <q-btn
            v-if="canCancel"
            outline
            no-caps
            color="negative"
            label="신청 취소"
            :loading="acting"
            @click="onCancel"
          />
          <!-- 되돌릴 수 없는 동작 — 주황(경고)이 아니라 위험 색으로 신청 취소와 맞춘다 -->
          <q-btn
            v-if="canWithdraw"
            outline
            no-caps
            color="negative"
            label="수강 철회"
            :loading="acting"
            @click="onWithdraw"
          />
          <div v-else-if="withdrawBlocked" class="withdraw-blocked" role="note">
            <q-icon name="info" size="18px" aria-hidden="true" />
            <div>
              <div class="withdraw-blocked__title">{{ withdrawBlocked.title }}</div>
              <div class="withdraw-blocked__detail">{{ withdrawBlocked.detail }}</div>
            </div>
          </div>
        </template>
      </LearnerPageHead>

      <AppErrorBanner :message="errorMessage">
        <template #action>
          <q-btn flat color="negative" label="다시 시도" @click="loadDetail" />
        </template>
      </AppErrorBanner>

      <div class="relative-position">
        <q-inner-loading :showing="loading" />

        <template v-if="enrollment">
          <div class="row q-col-gutter-md">
            <div class="col-12 col-md-6">
              <q-card flat bordered>
                <q-card-section class="text-subtitle1 text-weight-bold">신청 정보</q-card-section>
                <q-separator />
                <q-list>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>진행 단계</q-item-label>
                      <EnrollmentSteps :enrollment="enrollment" :completion="completion" />
                    </q-item-section>
                  </q-item>
                  <q-item>
                    <q-item-section>
                      <q-item-label caption>신청유형</q-item-label>
                      <q-item-label>{{ APPLICATION_TYPE_LABEL[enrollment.applicationType] || '-' }}</q-item-label>
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
                <q-card-section class="text-subtitle1 text-weight-bold">출결</q-card-section>
                <q-separator />
                <!-- 퍼센트가 셋(진도·출석률·평가 출석률)이라 헷갈렸다 — 출석률 하나만 크게,
                     진도는 작은 보조 글, 평가 출석률은 이수 카드 안에서만 보인다 -->
                <q-card-section>
                  <div v-if="summary">
                    <div class="attend-rate">{{ formatAttendanceRate(summary.attendanceRate) }}</div>
                    <div class="text-caption text-grey-7">출석률</div>
                    <div class="row q-col-gutter-sm q-mt-md">
                      <div class="col-6">출석 {{ summary.presentCount ?? 0 }}</div>
                      <div class="col-6">지각 {{ summary.lateCount ?? 0 }}</div>
                      <div class="col-6">결석 {{ summary.absentCount ?? 0 }}</div>
                      <div class="col-6">공결 {{ summary.excusedCount ?? 0 }}</div>
                    </div>
                  </div>
                  <div v-else class="text-grey-7">출결 요약 정보가 없습니다.</div>
                  <!-- 진도: 출석한 회차 / 전체 회차(취소 제외). 나의 강좌 카드와 같은 기준 -->
                  <p v-if="progress" class="progress-note">
                    학습 진도 {{ progress.progressPercent ?? 0 }}%
                    ({{ progress.attendedCount ?? 0 }} / {{ progress.sessionCount ?? 0 }}회 출석)
                    · 마지막 출석
                    {{ progress.lastStudiedAt ? $formatDateTimeKst(progress.lastStudiedAt) : '없음' }}
                  </p>
                  <!-- 출석 방식(운영 정책): 다대구 사용자는 강사 화면 QR 을 폰 카메라로 찍어 본인 확인 후 출석,
                       나머지는 강사가 기록한다. 코드를 붙여 넣는 출석은 코드가 메신저로 돌면 대리 출석이 돼 없앴다 -->
                  <p v-if="enrollment.status === 'APPROVED'" class="attend-guide">
                    수업 중 강사 화면의 출석 QR을 휴대폰 카메라로 찍으면 다대구 본인 확인 후 출석됩니다.
                    다대구를 쓰지 않으면 강사가 출석을 기록합니다.
                  </p>
                </q-card-section>
              </q-card>
            </div>
          </div>

          <q-card flat bordered class="q-mt-md">
            <q-card-section class="row items-center justify-between">
              <div class="text-subtitle1 text-weight-bold">이수 상태</div>
            </q-card-section>
            <q-separator />
            <q-card-section>
              <div v-if="completion">
                <StatusBadge :status="completion.status" :label="COMPLETION_STATUS_LABEL[completion.status] || ''" />
                <!-- 이수 판정에 쓴 출석률 — 위 출결 카드의 실시간 출석률과 시점이 달라 기준임을 밝힌다 -->
                <div class="q-mt-sm">
                  이수 평가 기준 출석률: {{ formatAttendanceRate(completion.attendanceRate) }}
                </div>
                <div>이수 시간: {{ completion.completedMinutes ? `${completion.completedMinutes}분` : '-' }}</div>
                <div>학점: {{ completion.creditValue || '-' }}</div>
                <!-- 이수 완료 + 이 강좌로 발급된 수료증이 있으면 바로 지갑 상세로 -->
                <q-btn
                  v-if="completion.status === 'COMPLETED' && credentialId"
                  unelevated
                  no-caps
                  color="primary"
                  icon="o_workspace_premium"
                  label="수료증 보기"
                  class="q-mt-md"
                  :to="`/wallet/credentials/${credentialId}`"
                />
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
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import LearnerPageHead from '@/components/LearnerPageHead.vue';
import { confirmDialog } from '@/utils/confirm-dialog';
import {
  canWithdrawEnrollment,
  cancelConfirmOptions,
  hasAttended,
  selfWithdrawDeadline,
  withdrawBlockedNotice,
  withdrawBlockedReason,
  withdrawConfirmOptions,
} from '@/utils/enrollment-actions';
import EnrollmentSteps from '@/components/learning/EnrollmentSteps.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { getAttendanceSummary } from '@/services/attendance-api';
import { getCompletion } from '@/services/completion-api';
import { getCourse } from '@/services/course-api';
import { listCourseSessions } from '@/services/session-api';
import { listMyCredentials } from '@/services/credential-api';
import { courseCredentialId } from '@/utils/course-credential';
import {
  cancelEnrollment,
  getEnrollment,
  getEnrollmentProgress,
  withdrawEnrollment,
} from '@/services/enrollment-api';
import { useAuthStore } from '@/stores/auth-store';
import { APPLICATION_TYPE_LABEL, COMPLETION_STATUS_LABEL } from '@/utils/status';
import { formatAttendanceRate } from '@/utils/attendance-rate';
import { formatUserDisplayName } from '@/utils/user-display';

export default {
  components: { AppErrorBanner, EnrollmentSteps, LearnerPageHead, StatusBadge },
  name: 'EnrollmentDetailPage',
  data() {
    return {
      enrollment: null,
      summary: null,
      progress: null,
      completion: null,
      // 확인 창에서 '다시 신청할 수 있나'를 알리려고 — 모집중일 때만 재신청된다
      courseStatus: '',
      // 본인 철회 마감(Date) — 첫 회차 시작 24시간 전. 모르면 null(서버가 판단)
      withdrawDeadline: null,
      credentialId: '',
      COMPLETION_STATUS_LABEL,
      APPLICATION_TYPE_LABEL,
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
    // 수료했거나 강좌가 끝났으면 철회를 숨긴다 — 수료 뒤 철회하면 나의 강좌에선 빠지고 수료증은 남았다
    // 이수 평가 뒤·출석 뒤·수업 시작 24시간 전이 지나면 본인 철회가 막힌다(백엔드 403/409) — 단추 대신 이유를 보인다
    withdrawRule() {
      return {
        status: this.enrollment?.status,
        courseStatus: this.courseStatus,
        completionStatus: this.completion?.status,
        summary: this.summary,
        attendanceKnown: this.summary !== null,
        deadline: this.withdrawDeadline,
      };
    },
    canWithdraw() {
      return canWithdrawEnrollment(this.withdrawRule);
    },
    withdrawBlocked() {
      return withdrawBlockedNotice(this.withdrawRule);
    },
    applicantLabel() {
      const e = this.enrollment;
      if (!e) return '-';
      // 학습자에게 UUID 를 보이지 않는다 — id 를 빼고 넘기면 이름이 없을 때 '-' 가 된다
      if (e.userName) return formatUserDisplayName({ userName: e.userName, birthDate: e.birthDate });
      // 이름이 안 온 본인 신청은 로그인한 내 이름으로
      const auth = useAuthStore();
      if (e.userId && e.userId === auth.userId) return auth.displayName || '-';
      return '-';
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
    formatAttendanceRate,
    /**
     * 이 강좌로 받은 수료증 id. 과정으로 거르는 조회 API 가 없어 내 수료증 목록에서 courseId 로 찾는다.
     * 재발급되면 옛 버전(SUPERSEDED)이 남으므로 ISSUED 를 먼저, 그다음 최신 버전을 고른다.
     * 못 찾거나 실패해도 버튼만 숨긴다 — 이수 정보 자체는 그대로 보여야 한다
     */
    async findCourseCredentialId(courseId) {
      if (!courseId) return '';
      try {
        const page = await listMyCredentials({ page: 0, size: 100 });
        return courseCredentialId(page.data, courseId);
      } catch {
        return '';
      }
    },
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
      this.summary = null;
      this.progress = null;
      this.completion = null;
      this.credentialId = '';
      this.courseTitle = '';
      this.courseStatus = '';
      this.withdrawDeadline = null;

      try {
        const enrollment = await getEnrollment(enrollmentId);
        this.enrollment = enrollment;
        this.courseTitle = enrollment.courseTitle || enrollment.courseCode || '수강신청 상세';

        if (enrollment.courseId) {
          const [course, sessions] = await Promise.allSettled([
            getCourse(enrollment.courseId),
            listCourseSessions(enrollment.courseId, { size: 100 }),
          ]);
          if (course.status === 'fulfilled') {
            // 실패하면 제목은 enrollment 필드로 대체
            this.courseTitle = course.value?.title || this.courseTitle;
            this.courseStatus = course.value?.status || '';
          }
          this.withdrawDeadline = selfWithdrawDeadline({
            sessions: sessions.status === 'fulfilled' && Array.isArray(sessions.value?.data) ? sessions.value.data : [],
            startDate: course.status === 'fulfilled' ? course.value?.startDate : '',
          });
        }

        // 진도·출결·이수는 서로 독립 — 하나가 실패해도 나머지는 보인다
        const [summary, progress] = await Promise.allSettled([
          getAttendanceSummary(enrollmentId),
          getEnrollmentProgress(enrollmentId),
        ]);
        this.summary = summary.status === 'fulfilled' ? summary.value : null;
        this.progress = progress.status === 'fulfilled' ? progress.value : null;

        try {
          this.completion = await getCompletion(enrollmentId);
        } catch {
          this.completion = null;
        }

        if (this.completion?.status === 'COMPLETED') {
          this.credentialId = await this.findCourseCredentialId(enrollment.courseId);
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
      const ok = await confirmDialog(
        this.$q,
        cancelConfirmOptions({ courseTitle: this.courseTitle, courseStatus: this.courseStatus }),
      );
      if (!ok) return;
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
      // 어느 강좌인지, 철회하면 다시 신청할 수 있는지를 함께 — 예전엔 '기록이 더 쌓이지 않습니다'뿐이었다
      const ok = await confirmDialog(
        this.$q,
        withdrawConfirmOptions({ courseTitle: this.courseTitle, courseStatus: this.courseStatus, deadline: this.withdrawDeadline }),
      );
      if (!ok) return;
      this.acting = true;
      try {
        // 화면을 열어 둔 사이 출석했을 수 있다 — 보내기 직전에 한 번 더 확인한다
        const latest = await getAttendanceSummary(this.enrollment.id);
        this.summary = latest;
        // 확인 창을 띄운 사이 마감이 지났을 수도 있다 — withdrawRule 의 now 는 계산 때 시각이라 여기서 다시 본다
        const blocked = withdrawBlockedReason({ ...this.withdrawRule, now: Date.now() });
        if (hasAttended(latest) || blocked) {
          this.$q.notify({ type: 'warning', message: blocked, position: 'top' });
          return;
        }
        await withdrawEnrollment(this.enrollment.id);
        this.$q.notify({ type: 'info', message: '수강이 철회되었습니다.', position: 'top' });
        await this.loadDetail();
      } catch (error) {
        this.$q.notify({
          type: 'negative',
          message: error.message || '철회에 실패했습니다.',
          position: 'top',
        });
        // 서버가 마감·이수 평가로 막았으면 화면 규칙이 낡은 것 — 다시 읽어 단추를 이유로 바꾼다
        if (error.status === 403 || error.status === 409) await this.loadDetail();
      } finally {
        this.acting = false;
      }
    },
  },
};
</script>

<style scoped>
/* 철회를 막은 이유 — 단추 자리에 두 줄(무슨 일인지 굵게, 할 일은 연하게) */
.withdraw-blocked {
  display: inline-flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 420px;
  line-height: 1.45;
  word-break: keep-all;
}

.withdraw-blocked .q-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--db-text-sub);
}

.withdraw-blocked__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--db-text-title);
}

.withdraw-blocked__detail {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

/* 출석률이 이 화면의 대표 지표 — 하나만 크게 */
.attend-rate {
  font-size: 34px;
  line-height: 40px;
  font-weight: 700;
  color: var(--db-primary);
  font-variant-numeric: tabular-nums;
}

.progress-note {
  margin: 12px 0 0;
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
  word-break: keep-all;
}

.rounded-borders {
  border-radius: 12px;
}
.attend-guide {
  margin: 12px 0 0;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--db-text-body);
  background: var(--db-bg-subtle);
  border-radius: 6px;
  word-break: keep-all;
}
</style>
