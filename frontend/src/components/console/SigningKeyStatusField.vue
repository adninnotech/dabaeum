<template>
  <!-- 관리자 기관 상세 > 서명키 — 대표 상태 + (없음·오류일 때) [서명키 만들기]/[다시 시도].
       "확인 필요"는 판정 API 대상이라 지금은 안내만 보인다 -->
  <div class="sigkey" aria-live="polite">
    <div class="sigkey__row">
      <span v-if="loading && !summary" class="sigkey__muted">불러오는 중…</span>
      <span v-else-if="loadError" class="sigkey__muted">조회 실패</span>
      <StatusBadge v-else-if="summary" :label="summary.label" :color="summary.color" soft />
      <q-btn
        v-if="summary?.canCreate"
        flat
        dense
        no-caps
        class="sigkey__action"
        :loading="creating"
        :label="summary.key === 'ERROR' ? '다시 시도' : '서명키 만들기'"
        @click="confirmCreate"
      />
      <q-btn
        flat
        dense
        round
        size="sm"
        icon="o_refresh"
        aria-label="서명키 상태 새로고침"
        :disable="loading"
        @click="load"
      />
    </div>
    <p v-if="hint" class="sigkey__hint">{{ hint }}</p>
  </div>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import {
  createInstitutionSigningKeys,
  getInstitutionSigningKeys,
} from '@/services/signing-key-api';
import { SIGNING_KEY_HINT, summarizeSigningKeys } from '@/utils/signing-key-status';

export default {
  name: 'SigningKeyStatusField',
  components: { StatusBadge },
  props: {
    institutionId: {
      type: String,
      required: true,
    },
    /** 기관 createdAt(승인 때 기관 행이 새로 생겨 곧 승인 시각) — 60초 안이면 키 없음을 "생성 대기"로 보인다 */
    approvedAt: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      overview: null,
      loading: false,
      creating: false,
      loadError: '',
    };
  },
  computed: {
    summary() {
      return this.overview ? summarizeSigningKeys(this.overview, this.approvedAt) : null;
    },
    hint() {
      if (this.loadError) return this.loadError;
      return this.summary ? SIGNING_KEY_HINT[this.summary.key] : '';
    },
  },
  watch: {
    institutionId: {
      immediate: true,
      handler() {
        this.overview = null;
        this.load();
      },
    },
  },
  methods: {
    async load() {
      const requestedId = this.institutionId;
      if (!requestedId) return;
      this.loading = true;
      this.loadError = '';
      try {
        const overview = await getInstitutionSigningKeys(requestedId);
        // 응답 사이 다른 기관으로 바뀌었으면 버린다
        if (requestedId === this.institutionId) this.overview = overview;
      } catch (error) {
        if (requestedId === this.institutionId) {
          this.loadError = error?.message || '서명키 상태를 불러오지 못했습니다.';
        }
      } finally {
        this.loading = false;
      }
    },
    confirmCreate() {
      this.$q
        .dialog({
          title: '서명키 만들기',
          message:
            '이 기관의 수료증·상태 목록 서명키를 OpenBao에 만듭니다. 진행할까요?',
          cancel: true,
        })
        .onOk(() => this.create());
    },
    async create() {
      this.creating = true;
      try {
        this.overview = await createInstitutionSigningKeys(this.institutionId);
        this.$q.notify({ type: 'positive', message: '서명키를 만들었습니다.', position: 'top' });
      } catch (error) {
        this.$q.notify({ ...this.createErrorNotice(error), position: 'top' });
        await this.load();
      } finally {
        this.creating = false;
      }
    },
    createErrorNotice(error) {
      if (error?.payload?.code === 'SIGNING_KEY_ALREADY_EXISTS') {
        return { type: 'info', message: '이미 서명키가 있습니다.' };
      }
      if (error?.status === 503) {
        return {
          type: 'warning',
          message: '서명키 생성 결과를 확인하지 못했습니다. 운영자 확인이 필요합니다.',
          timeout: 0,
          actions: [{ label: '닫기', color: 'white' }],
        };
      }
      return { type: 'negative', message: error?.message || '서명키를 만들지 못했습니다.' };
    },
  },
};
</script>

<style scoped>
.sigkey {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.sigkey__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.sigkey__action {
  color: var(--db-accent);
  white-space: nowrap;
}

.sigkey__muted {
  color: var(--db-text-sub);
}

/* 상세 필드 칸(.page-info__value)은 한 줄 말줄임이라 안내 문구만 줄바꿈을 다시 켠다 */
.sigkey__hint {
  margin: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 18px;
  font-size: 13px;
  color: var(--db-text-sub);
}
</style>
