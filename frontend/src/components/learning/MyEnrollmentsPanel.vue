<template>
  <!-- 학습관리 "수강 신청" 탭 — 신청·대기·승인·취소 등 신청 단계 전체 (옛 /enrollment 화면) -->
  <div class="my-enrollments">
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat color="negative" label="다시 시도" @click="loadEnrollments" />
      </template>
    </AppErrorBanner>

    <AppEmptyState
      v-if="!loading && !rows.length && !errorMessage"
      description="신청 내역이 없습니다."
      action-label="강좌 찾기"
      action-to="/courses"
    />

    <template v-else-if="rows.length">
      <!-- 진행 중인 신청(신청·대기·승인)을 위에 — 끝난 신청이 섞여 있었다 -->
      <ul v-if="activeRows.length" class="my-enrollments__list">
        <li v-for="row in activeRows" :key="row.id" class="my-enrollments__row">
          <EnrollmentRowBody :row="row" />
        </li>
      </ul>
      <p v-else class="my-enrollments__none">진행 중인 신청이 없습니다.</p>

      <template v-if="pastRows.length">
        <button
          type="button"
          class="my-enrollments__toggle"
          :aria-expanded="pastOpen ? 'true' : 'false'"
          @click="pastOpen = !pastOpen"
        >
          지난 신청 {{ pastRows.length }}건
          <q-icon :name="pastOpen ? 'expand_less' : 'expand_more'" size="18px" aria-hidden="true" />
        </button>
        <ul v-if="pastOpen" class="my-enrollments__list my-enrollments__list--past">
          <li v-for="row in pastRows" :key="row.id" class="my-enrollments__row">
            <EnrollmentRowBody :row="row" />
          </li>
        </ul>
      </template>
    </template>

    <q-inner-loading :showing="loading && !rows.length" />
  </div>
</template>

<script>
import { mapState } from 'pinia';
import AppEmptyState from '@/components/AppEmptyState.vue';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import EnrollmentRowBody from '@/components/learning/EnrollmentRowBody.vue';
import { getCompletion } from '@/services/completion-api';
import { listMyCredentials } from '@/services/credential-api';
import { listUserEnrollments } from '@/services/enrollment-api';
import { useAuthStore } from '@/stores/auth-store';
import { courseCredentialId } from '@/utils/course-credential';

const ACTIVE_STATUSES = ['APPLIED', 'WAITLISTED', 'APPROVED'];

/** 이수 기록 — 404 는 아직 평가 전(오류 아님). 그 밖의 실패도 '모름'(null)으로 둔다 */
async function completionStatusOf(enrollmentId) {
  try {
    const completion = await getCompletion(enrollmentId);
    return completion?.status || null;
  } catch {
    return null;
  }
}

/** 내 수료증 — 실패하면 null(모름). 그때는 '수료증 보기'를 지갑 목록으로 보낸다 */
async function myCredentials() {
  try {
    const page = await listMyCredentials({ page: 0, size: 100 });
    return Array.isArray(page.data) ? page.data : [];
  } catch {
    return null;
  }
}

export default {
  name: 'MyEnrollmentsPanel',
  components: { AppEmptyState, AppErrorBanner, EnrollmentRowBody },
  data() {
    return {
      rows: [],
      loading: false,
      errorMessage: '',
      pastOpen: false,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['isLoggedIn', 'userId']),
    activeRows() {
      return this.rows.filter((row) => ACTIVE_STATUSES.includes(row.status));
    },
    pastRows() {
      return this.rows.filter((row) => !ACTIVE_STATUSES.includes(row.status));
    },
  },
  mounted() {
    // 새로고침 아이콘은 뺐다 — 탭을 열 때마다 새로 받고, 실패하면 [다시 시도]가 뜬다
    this.loadEnrollments();
  },
  methods: {
    async loadEnrollments() {
      if (!this.isLoggedIn) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const [list, credentials] = await Promise.all([listUserEnrollments(this.userId), myCredentials()]);
        const rows = Array.isArray(list) ? list : [];
        // 승인된 신청만 이수 기록을 본다 — 수료했으면 '수료'로 보이고 철회를 막는다
        this.rows = await Promise.all(
          rows.map(async (row) => ({
            ...row,
            completionStatus: row.status === 'APPROVED' ? await completionStatusOf(row.id) : null,
            // 수료했어도 수료증이 아직 없을 수 있다(발급 전) — 그때 '수료증 보기'를 띄우지 않는다(QA ADM-07 비고)
            credentialId: credentials ? courseCredentialId(credentials, row.courseId) : null,
          })),
        );
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '수강신청 목록을 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.my-enrollments {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
}

.my-enrollments__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.my-enrollments__row {
  position: relative;
  background: var(--db-bg-white);
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.my-enrollments__row:hover {
  border-color: var(--db-accent);
}

.my-enrollments__list--past .my-enrollments__row {
  background: var(--db-bg-subtle);
}

.my-enrollments__none {
  margin: 0;
  padding: 16px 0;
  font-size: 14px;
  text-align: center;
  color: var(--db-text-sub);
}

.my-enrollments__toggle {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 2px;
  min-height: 40px;
  padding: 0 4px;
  border: 0;
  background: none;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  color: var(--db-text-sub);
  cursor: pointer;
}

.my-enrollments__toggle:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}
</style>
