<template>
  <!-- 수강 신청 한 줄 — 줄 전체가 상세로 가는 링크(늘린 제목 링크), 안내 줄의 링크는 그 위에 떠서 따로 눌린다.
       취소·철회는 목록에 두지 않는다: [상세]와 같은 글자 링크라 실수로 누르기 쉬웠다 — 상세 화면에서 한다 -->
  <div class="enroll-row">
    <div class="enroll-row__main">
      <router-link :to="`/enrollment/${row.id}`" class="enroll-row__title">{{ row.courseTitle || '-' }}</router-link>
      <span class="enroll-row__meta">{{ metaLine }}</span>
      <!-- 뱃지만으로는 지금 무슨 상황인지 몰랐다 — 상태별 한 줄 -->
      <span v-if="hint.text" class="enroll-row__hint">
        {{ hint.text }}
        <router-link v-if="hint.link" :to="hint.link.to" class="enroll-row__hint-link">{{ hint.link.label }}</router-link>
      </span>
    </div>
    <StatusBadge :status="badge.status" :label="badge.label" class="enroll-row__status" />
    <q-icon name="chevron_right" size="20px" class="enroll-row__chevron" aria-hidden="true" />
  </div>
</template>

<script>
import StatusBadge from '@/components/StatusBadge.vue';
import { formatDateMinuteKst } from '@/utils/datetime';
import { enrollmentBadgeLabel, enrollmentCancellationReason } from '@/utils/enrollment-decision';
import { APPLICATION_TYPE_LABEL } from '@/utils/status';

const STATUS_HINT = {
  APPLIED: { text: '기관 승인을 기다리고 있어요.' },
  WAITLISTED: { text: '정원이 차서 대기 중이에요. 자리가 나면 순서대로 승인돼요.' },
  APPROVED: { text: '수강이 확정됐어요.', link: { to: '/learning?tab=courses', label: '나의 강좌에서 보기' } },
  REJECTED: { text: '기관이 신청을 거절했어요. 사유는 상세에서 볼 수 있어요.' },
  CANCELLED: { text: '취소한 신청이에요.' },
  WITHDRAWN: { text: '철회한 수강이에요.' },
};

export default {
  name: 'EnrollmentRowBody',
  components: { StatusBadge },
  props: {
    /** 신청 목록 행 + completionStatus(승인 행만, 없으면 null) + credentialId('' 발급 전, null 모름) */
    row: { type: Object, required: true },
  },
  computed: {
    completed() {
      return this.row.status === 'APPROVED' && this.row.completionStatus === 'COMPLETED';
    },
    metaLine() {
      const type = APPLICATION_TYPE_LABEL[this.row.applicationType];
      // 분까지 — 초까지 붙어 다른 화면과 달랐다
      const applied = this.row.appliedAt ? `신청 ${formatDateMinuteKst(this.row.appliedAt)}` : '';
      return [this.row.institutionName, type, applied].filter(Boolean).join(' · ') || '-';
    },
    badge() {
      // 승인 뒤 수료했으면 '승인'보다 '수료'가 지금 상태다
      if (this.completed) return { status: 'COMPLETED', label: '수료' };
      // 강사·기관 화면과 같은 이름('수강 확정'·'대기 N번') — '승인'으로 나와 안내 문구('수강이 확정됐어요')와 달랐다
      return { status: this.row.status, label: enrollmentBadgeLabel(this.row) };
    },
    hint() {
      if (this.completed) {
        // credentialId: 수료증 id · '' = 아직 발급 전 · null = 조회 실패(모름 — 지갑 목록으로 보낸다)
        const { credentialId } = this.row;
        if (credentialId) return { text: '수료했어요.', link: { to: `/wallet/credentials/${credentialId}`, label: '수료증 보기' } };
        if (credentialId === '') return { text: '수료했어요. 수료증은 기관이 발급하면 수료증 지갑에 보여요.' };
        return { text: '수료했어요.', link: { to: '/wallet', label: '수료증 지갑' } };
      }
      if (this.row.status === 'CANCELLED') {
        const reason = enrollmentCancellationReason(this.row);
        return { text: reason ? `취소한 신청이에요. 사유: ${reason}` : STATUS_HINT.CANCELLED.text };
      }
      if (this.row.status === 'WITHDRAWN') {
        const reason = enrollmentCancellationReason(this.row);
        return { text: reason ? `철회한 수강이에요. 사유: ${reason}` : STATUS_HINT.WITHDRAWN.text };
      }
      return STATUS_HINT[this.row.status] || { text: '' };
    },
  },
};
</script>

<style scoped>
.enroll-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
}

.enroll-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.enroll-row__title {
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--db-text-title);
  text-decoration: none;
  outline: none;
}

/* 줄 전체를 누르는 영역 — 부모 li 가 position: relative */
.enroll-row__title::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 8px;
}

.enroll-row__title:focus-visible::after {
  outline: 2px solid var(--db-accent);
  outline-offset: -2px;
}

.enroll-row__meta {
  font-size: 14px;
  color: var(--db-text-sub);
  font-variant-numeric: tabular-nums;
}

.enroll-row__hint {
  font-size: 14px;
  color: var(--db-text-body);
  word-break: keep-all;
}

/* 안내 링크는 늘린 영역 위 */
.enroll-row__hint-link {
  position: relative;
  z-index: 1;
  margin-left: 4px;
  font-weight: 600;
  color: var(--db-accent);
  text-decoration: none;
}

.enroll-row__hint-link:hover,
.enroll-row__hint-link:focus-visible {
  text-decoration: underline;
}

.enroll-row__status,
.enroll-row__chevron {
  flex-shrink: 0;
}

.enroll-row__chevron {
  color: var(--db-text-sub);
}

@media (max-width: 599px) {
  .enroll-row {
    align-items: flex-start;
    gap: 8px;
    padding: 14px 16px;
  }

  .enroll-row__chevron {
    display: none;
  }
}
</style>
