<template>
  <CourseDetailSection tab="Q&A" title="Q&A">
    <template #action>
      <!-- 남색 채운 버튼이 [수강신청]과 같은 무게로 읽혔다 — 테두리로 낮춘다 -->
      <q-btn outline no-caps color="primary" label="문의하기" @click="openForm" />
    </template>

    <AppEmptyState v-if="!isLoggedIn" compact description="로그인하면 문의 내역을 볼 수 있습니다." />
    <AppEmptyState
      v-else-if="!loading && !inquiries.length"
      compact
      description="등록된 문의가 없습니다."
    />
    <div v-for="item in inquiries" :key="item.id" class="detail-inquiry">
      <div class="detail-inquiry__head">
        <span class="detail-inquiry__type">{{ typeLabel(item.inquiryType) }}</span>
        <span class="detail-inquiry__title">{{ item.title }}</span>
        <StatusBadge :status="item.status" size="sm" />
      </div>
      <div class="detail-inquiry__meta">
        <!-- 학습자 시야에서는 이름이 마스킹되고(홍*동) email·phone은 null이다 -->
        <span>{{ item.userName || '학습자' }}</span>
        <span v-if="item.email">{{ item.email }}</span>
        <span v-if="item.phone">{{ item.phone }}</span>
        <span>{{ formatDateKst(item.createdAt) }}</span>
      </div>
    </div>

    <q-dialog v-model="open">
      <q-card class="detail-dialog">
        <q-card-section class="text-subtitle1 text-weight-bold">문의하기</q-card-section>
        <q-card-section class="q-gutter-md">
          <q-input v-model="form.title" outlined dense label="제목" />
          <q-input v-model="form.content" outlined type="textarea" label="내용" />
        </q-card-section>
        <q-card-actions align="right">
          <q-btn v-close-popup flat no-caps label="취소" />
          <q-btn unelevated no-caps color="primary" label="등록" :loading="saving" @click="submit" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </CourseDetailSection>
</template>

<script>
import AppEmptyState from '@/components/AppEmptyState.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import CourseDetailSection from '@/components/course/detail/CourseDetailSection.vue';
import { createInquiry } from '@/services/inquiry-api';
import { formatDateKst } from '@/utils/datetime';
import { loginRequiredRoute } from '@/utils/auth-redirect';

const TYPE_LABEL = { COURSE: '강좌 문의', QNA: 'Q&A' };

export default {
  name: 'CourseQnaSection',
  components: { AppEmptyState, CourseDetailSection, StatusBadge },
  props: {
    courseId: { type: String, required: true },
    inquiries: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    isLoggedIn: { type: Boolean, default: false },
  },
  emits: ['created'],
  data() {
    return { open: false, saving: false, form: { title: '', content: '' } };
  },
  methods: {
    formatDateKst,
    typeLabel(type) {
      return TYPE_LABEL[type] || type || '-';
    },
    openForm() {
      if (!this.isLoggedIn) {
        this.$router.push(loginRequiredRoute(this.$route.fullPath));
        return;
      }
      this.form = { title: '', content: '' };
      this.open = true;
    },
    async submit() {
      if (!this.form.title.trim() || !this.form.content.trim()) {
        this.$q.notify({ type: 'warning', message: '제목과 내용을 입력해 주세요.' });
        return;
      }
      this.saving = true;
      try {
        await createInquiry({
          title: this.form.title.trim(),
          content: this.form.content.trim(),
          courseId: this.courseId,
        });
        this.open = false;
        this.$q.notify({
          type: 'positive',
          message: '문의를 등록했습니다. 학습관리 > 나의 문의에서 확인할 수 있습니다.',
          position: 'top',
        });
        this.$emit('created');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error?.message || '문의 등록에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.detail-dialog {
  width: 480px;
  max-width: 90vw;
}

/* Q&A — 리뷰 카드와 같은 테두리·간격 규격을 따른다 */
.detail-inquiry {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 13px 24px;
  border: 1px solid var(--db-border);
  border-radius: 8px;
}

.detail-inquiry + .detail-inquiry {
  margin-top: 10px;
}

.detail-inquiry__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-inquiry__type {
  font-size: 13px;
  font-weight: 700;
  color: var(--db-text-sub);
}

.detail-inquiry__title {
  flex: 1;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.detail-inquiry__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--db-text-sub);
}
</style>
