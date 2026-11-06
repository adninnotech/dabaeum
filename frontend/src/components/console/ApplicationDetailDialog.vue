<template>
  <!-- 가입 신청(강사·기관) 상세 — 누가·어디로·왜 를 먼저 읽고, 연락처를 확인한 뒤, 같은 자리에서 승인·거절한다.
       예전엔 항목 이름·값이 같은 무게로 한 줄씩 늘어서 판단 근거(지원 사유)가 목록 속에 묻혔다 -->
  <q-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)">
    <q-card class="app-detail" :aria-busy="loading ? 'true' : 'false'">
      <header class="app-detail__head">
        <div class="app-detail__heading">
          <h2 class="app-detail__title">{{ loading ? '불러오는 중…' : title || '-' }}</h2>
          <p v-if="!loading && meta.length" class="app-detail__meta">
            <template v-for="(item, index) in meta" :key="item">
              <span v-if="index" class="app-detail__dot" aria-hidden="true">·</span>
              <span>{{ item }}</span>
            </template>
          </p>
        </div>
        <StatusBadge v-if="!loading && status" :status="status" class="app-detail__status" />
        <q-btn v-close-popup flat round icon="close" aria-label="닫기" class="app-detail__close" />
      </header>

      <div v-if="loading" class="app-detail__loading">
        <q-spinner color="primary" size="28px" />
      </div>

      <div v-else class="app-detail__body">
        <section v-if="message" class="app-detail__message">
          <h3 class="app-detail__label">{{ message.label }}</h3>
          <p class="app-detail__message-text">{{ message.text || '적은 내용이 없습니다.' }}</p>
        </section>

        <dl v-if="rows.length" class="app-detail__facts">
          <div v-for="row in rows" :key="row.label" class="app-detail__fact">
            <dt class="app-detail__label">{{ row.label }}</dt>
            <dd class="app-detail__value">
              <a v-if="row.href" :href="row.href" class="app-detail__contact">
                <q-icon v-if="row.icon" :name="row.icon" size="18px" aria-hidden="true" />
                <span>{{ row.value }}</span>
              </a>
              <template v-else>{{ row.value || '-' }}</template>
            </dd>
          </div>
        </dl>

        <router-link v-if="link" :to="link.to" class="app-detail__link">
          {{ link.label }}
          <q-icon name="chevron_right" size="18px" aria-hidden="true" />
        </router-link>
      </div>

      <footer class="app-detail__foot" :class="{ 'app-detail__foot--decide': canDecide }">
        <template v-if="canDecide">
          <p v-if="pendingNote" class="app-detail__note">{{ pendingNote }}</p>
          <div class="app-detail__actions">
            <q-btn outline no-caps color="negative" label="거절" class="app-detail__btn" :disable="busy" @click="$emit('reject')" />
            <q-btn unelevated no-caps color="primary" label="승인" class="app-detail__btn" :loading="approving" :disable="busy" @click="$emit('approve')" />
          </div>
        </template>
        <q-btn v-else v-close-popup outline no-caps color="primary" label="닫기" class="app-detail__btn" />
      </footer>
    </q-card>
  </q-dialog>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';

export default {
  name: 'ApplicationDetailDialog',
  components: { StatusBadge },
  props: {
    modelValue: { type: Boolean, default: false },
    /** 머리 큰 글자 — 신청자 이름·기관명 */
    title: { type: String, default: '' },
    status: { type: String, default: '' },
    /** 머리 아래 한 줄 — ['강사 신청', '에이디앤기관3', '2026.10.13 신청'] */
    meta: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    /** 판단 근거 글 — { label, text }. 없으면 칸을 그리지 않는다 */
    message: { type: Object, default: null },
    /** 연락처(링크) — [{ label, value, href, icon }] */
    contacts: { type: Array, default: () => [] },
    /** 그 밖의 항목 — [{ label, value }] */
    facts: { type: Array, default: () => [] },
    /** 대기 중이라 승인·거절할 수 있다 */
    pending: { type: Boolean, default: false },
    /** 다른 처리가 도는 중 — 두 단추를 막는다 */
    busy: { type: Boolean, default: false },
    approving: { type: Boolean, default: false },
    /** 승인하면 무엇이 생기는지 — 단추 옆 한 줄 */
    pendingNote: { type: String, default: '' },
    /** 처리 뒤 이어 갈 곳 — { to, label } */
    link: { type: Object, default: null },
  },
  emits: ['update:modelValue', 'approve', 'reject'],
  computed: {
    rows() {
      return [...this.contacts, ...this.facts];
    },
    canDecide() {
      return this.pending && !this.loading;
    },
  },
};
</script>

<style scoped>
/* Hallmark · component: dialog (application detail) · genre: modern-minimal · theme: project tokens (--db-*)
 * states: default · hover · focus · active · disabled · loading · error(호출 쪽 알림) · success(닫힘·목록 갱신)
 */
.app-detail {
  display: flex;
  flex-direction: column;
  width: 560px;
  max-width: 94vw;
  max-height: 88vh;
  border-radius: 12px;
  background: var(--db-bg-white);
}

.app-detail__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 24px 16px 20px 28px;
  border-bottom: 1px solid var(--db-border);
}

.app-detail__heading {
  flex: 1 1 auto;
  min-width: 0;
}

.app-detail__title {
  margin: 0;
  font-size: 22px;
  line-height: 30px;
  font-weight: 700;
  letter-spacing: normal;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.app-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
  margin: 6px 0 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
}

.app-detail__dot {
  color: var(--db-border);
}

.app-detail__status {
  flex-shrink: 0;
  margin-top: 3px;
}

.app-detail__close {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  margin-top: -8px;
  color: var(--db-text-sub);
}

.app-detail__loading {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.app-detail__body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px 28px;
  overflow-y: auto;
}

.app-detail__label {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  letter-spacing: normal;
  color: var(--db-text-sub);
}

/* 판단 근거 — 목록과 다른 바탕 위 본문 크기로 */
.app-detail__message {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.app-detail__message-text {
  margin: 0;
  font-size: 16px;
  line-height: 26px;
  color: var(--db-text-title);
  white-space: pre-line;
  word-break: keep-all;
  overflow-wrap: anywhere;
}

.app-detail__facts {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  margin: 0;
  border-top: 1px solid var(--db-border);
}

.app-detail__fact {
  display: contents;
}

.app-detail__fact > dt,
.app-detail__fact > dd {
  display: flex;
  align-items: center;
  min-height: 48px;
  border-bottom: 1px solid var(--db-border);
}

.app-detail__value {
  margin: 0;
  font-size: 15px;
  line-height: 22px;
  color: var(--db-text-body);
  overflow-wrap: anywhere;
}

.app-detail__contact {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  color: var(--db-accent);
  text-decoration: none;
}

.app-detail__link {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 2px;
  min-height: 44px;
  font-size: 15px;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.app-detail__contact:focus-visible,
.app-detail__link:focus-visible {
  outline: 2px solid var(--db-accent);
  outline-offset: 2px;
  border-radius: 4px;
}

.app-detail__foot {
  display: flex;
  justify-content: flex-end;
  padding: 16px 28px 24px;
  border-top: 1px solid var(--db-border);
}

.app-detail__foot--decide {
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.app-detail__note {
  margin: 0;
  font-size: 14px;
  line-height: 20px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.app-detail__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.app-detail__btn {
  min-width: 88px;
  min-height: 44px;
  font-size: 15px;
}

@media (hover: hover) {
  .app-detail__contact:hover span,
  .app-detail__link:hover {
    text-decoration: underline;
  }
}

/* 폰 — 단추는 아래 한 줄을 반씩, 안내는 그 위 */
@media (max-width: 599px) {
  .app-detail__head {
    padding: 20px 8px 16px 20px;
  }

  .app-detail__title {
    font-size: 20px;
    line-height: 28px;
  }

  .app-detail__body {
    padding: 20px;
  }

  .app-detail__facts {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .app-detail__foot {
    padding: 14px 20px 20px;
  }

  .app-detail__foot--decide {
    flex-direction: column;
    align-items: stretch;
  }

  .app-detail__actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .app-detail__foot > .app-detail__btn {
    width: 100%;
  }
}
</style>
