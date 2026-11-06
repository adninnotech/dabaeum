<template>
  <!-- 다른 관리자 화면과 같은 머리·여백(AppPageHeader) — 위가 좁고 폰 좌우가 8px 이었다 -->
  <q-page class="system-page">
    <AppPageHeader title="시스템 관리" subtitle="서버가 응답하는지 확인합니다." />

    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold">서버 상태</div>
            <!-- 열 때 한 번 확인한다 — 눌러야만 상태가 보여 처음엔 비어 있었다 -->
            <q-btn
              class="q-mt-md"
              color="primary"
              outline
              no-caps
              icon="refresh"
              :label="checkedAt ? '다시 확인' : '상태 확인'"
              :loading="loading"
              @click="ping"
            />
            <div v-if="checkedAt" class="system-state q-mt-md" :class="healthy ? 'system-state--ok' : 'system-state--bad'" role="status">
              <q-icon :name="healthy ? 'check_circle' : 'error_outline'" size="20px" aria-hidden="true" />
              <span>{{ healthy ? '정상 응답' : errorMessage || '응답 없음' }}</span>
              <span class="system-state__time">{{ checkedAt }} 확인</span>
            </div>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold">연결 정보</div>
            <!-- 인증 토큰·기본 사용자 ID 는 화면에 두지 않는다(데모 빌드에선 토큰 값이 그대로 보였다) -->
            <!-- 목록 항목 기본 여백(16px)만큼 제목보다 들어가 있었다 — 제목과 같은 선 -->
            <dl class="system-info">
              <dt>API 주소</dt>
              <dd>{{ apiBase }}</dd>
            </dl>
          </q-card-section>
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import { API_BASE_URL } from '@/config/api';
import { pingSystem } from '@/services/system-api';
import { formatDateTimeKst } from '@/utils/datetime';

export default {
  name: 'AdminSystemPage',
  components: { AppPageHeader },
  data() {
    return {
      apiBase: API_BASE_URL,
      healthy: false,
      checkedAt: '',
      errorMessage: '',
      loading: false,
    };
  },
  created() {
    this.ping();
  },
  methods: {
    async ping() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await pingSystem();
        this.healthy = (result?.status ?? result) === 'OK';
      } catch (error) {
        this.healthy = false;
        this.errorMessage = error?.message || '응답 없음';
      } finally {
        this.checkedAt = formatDateTimeKst(new Date().toISOString());
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
.system-page {
  padding: 48px 40px 32px;
}

@media (max-width: 1023px) {
  .system-page {
    padding: 24px 16px;
  }
}

.system-info {
  margin: 12px 0 0;
}

.system-info dt {
  font-size: 12px;
  color: var(--db-text-sub);
}

.system-info dd {
  margin: 2px 0 0;
  font-size: 14px;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.system-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
}

.system-state--ok {
  background: color-mix(in srgb, var(--db-status-green) 10%, transparent);
  color: var(--db-status-green-text);
}

.system-state--bad {
  background: color-mix(in srgb, var(--db-error) 8%, transparent);
  color: var(--db-error);
}

.system-state__time {
  font-size: 12px;
  font-weight: 400;
  color: var(--db-text-sub);
}
</style>
