<template>
  <ConsoleListPage
    v-model:page="page"
    v-model:page-size="pageSize"
    title="문의 관리"
    :list-title="listTitle"
    unit="건"
    tall-rows
    empty-text="등록된 문의가 없습니다."
    search-placeholder="문의 제목, 강의명으로 검색"
    :stats="stats"
    :columns="columns"
    :rows="pagedRows"
    :loading="loading"
    :error="errorMessage"
    :total="filteredRows.length"
    :filters="filters"
    :initial-filters="INITIAL_FILTERS"
    :stat-columns="3"
    row-clickable
    @search="onSearch"
    @row-click="openReply"
  >
    <template #cell-course="{ row }">
      <CourseInfoCell v-if="row.course" :row="row.course" :to="`/admin/courses/${row.courseId}`" />
      <span v-else class="cell-note cell-note--sub">일반 문의</span>
    </template>

    <template #cell-author="{ row }">
      <div class="cell-user">
        <div class="cell-user__avatar">
          <img v-if="row.profileImageId" :src="storedFileContentUrl(row.profileImageId)" alt="" />
          <template v-else>{{ initial(row.userName) }}</template>
        </div>
        <div class="cell-lines">
          <span class="cell-title">{{ row.userName || '-' }}</span>
          <!-- 연락처는 마스킹 대상 -- null이면 미렌더(00-common §2-4), '-'를 찍지 않는다 -->
          <span v-if="row.phone" class="cell-note cell-note--sub">{{ maskPhone(row.phone) }}</span>
        </div>
      </div>
    </template>

    <template #cell-inquiry="{ row }">
      <div class="cell-lines">
        <span class="cell-title">{{ row.title || '-' }}</span>
        <span class="cell-note cell-note--sub">{{ row.content || '-' }}</span>
      </div>
    </template>

    <template #cell-createdAt="{ row }">
      {{ formatDateTimeFields(row, 'createdAt', 'inquiredAt') }}
    </template>

    <template #cell-answered="{ row }">
      <StatusBadge
        :status="row.status"
        :label="row.status === 'ANSWERED' ? '답변 완료' : '답변 대기'"
      />
    </template>

    <!-- 답변이 ⋮ 메뉴 안에 숨어 있었다 — 답할 문의는 채운 [답변], 답한 문의는 [보기]로 바로.
         행 아무 곳이나 눌러도 같은 창이 열린다(강의명 링크는 강의로 간다). 키보드는 이 단추로 -->
    <template #cell-reply="{ row }">
      <q-btn
        v-if="row.status === 'ANSWERED'"
        flat
        dense
        no-caps
        color="primary"
        label="보기"
        @click="openReply(row)"
      />
      <q-btn v-else unelevated dense no-caps color="primary" label="답변" class="reply-btn" @click="openReply(row)" />
    </template>

    <template #default>
      <InquiryReplyDialog v-model="showReply" :inquiry="replyTarget" @replied="onReplied" />
    </template>
  </ConsoleListPage>
</template>

<script>
import { maskPhone } from '@/utils/user-display';
import ConsoleListPage from '@/components/console/ConsoleListPage.vue';
import CourseInfoCell from '@/components/console/CourseInfoCell.vue';
import InquiryReplyDialog from '@/components/console/InquiryReplyDialog.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import consoleList from '@/mixins/console-list';
import { listAdminInquiries } from '@/services/admin-api';
import { listCourses } from '@/services/course-api';
import { listInstitutions } from '@/services/institution-api';
import { toCourseCard } from '@/utils/course-fields';
import { formatDateTimeFields } from '@/utils/datetime';
import { EDUCATION_TYPE_OPTIONS_ALL, INQUIRY_TYPE_LABEL, formatCourseOption } from '@/utils/status';
import { toInstitutionOptions } from '@/utils/institution-option';
import { storedFileContentUrl } from '@/services/file-api';

const COLUMNS = [
  // 강의명·작성자 이름이 'QA일…'처럼 잘렸다 — 두 칸을 넓힌다
  { key: 'course', label: '강의 / 강사', width: 300 },
  { key: 'author', label: '작성자', width: 200 },
  { key: 'inquiry', label: '문의 정보', minWidth: 240 },
  { key: 'createdAt', label: '문의 일시', width: 140, align: 'center' },
  { key: 'answered', label: '답변 상태', width: 100, align: 'center' },
  { key: 'reply', label: '처리', width: 90, align: 'center' },
];

// 미답변이 답변한 문의 사이에 묻혔다 — 처음엔 '답변 대기'만
const INITIAL_FILTERS = { status: 'PENDING' };

/** 검색·필터 상태를 /admin/inquiries 쿼리로 조립한다 (서버가 전부 거른다 — 클라 재필터 없음) */
export function buildInquiryQuery(search, page = 0, size = 100) {
  const filters = search?.filters || {};
  return {
    page,
    size,
    status: filters.status || undefined,
    inquiryType: filters.inquiryType || undefined,
    educationType: filters.educationType || undefined,
    institutionId: filters.institutionId || undefined,
    courseId: filters.courseId || undefined,
    q: search?.q || undefined,
  };
}

export default {
  name: 'AdminInquiriesPage',
  components: { ConsoleListPage, CourseInfoCell, InquiryReplyDialog, StatusBadge },
  mixins: [consoleList],
  data() {
    return {
      columns: COLUMNS,
      INITIAL_FILTERS,
      search: { q: '', filters: { ...INITIAL_FILTERS } },
      // 숫자 카드 — 지금 거른 목록이 아니라 전체 기준으로 따로 센다(null = 못 셈)
      counts: { pending: null, answered: null },
      serverSearch: true,
      showReply: false,
      replyTarget: null,
      institutionOptions: [],
      courseOptions: [],
    };
  },
  computed: {
    // 서버가 검색·필터를 전부 처리한다 — 클라이언트 재필터를 두지 않는다(00-common §3, plan Task4)
    filteredRows() {
      return this.rows;
    },
    pagedRows() {
      const start = (this.page - 1) * this.pageSize;
      return this.rows.slice(start, start + this.pageSize);
    },
    filters() {
      return [
        {
          key: 'status',
          label: '답변 상태',
          options: [
            { label: '답변 대기', value: 'PENDING' },
            { label: '답변 완료', value: 'ANSWERED' },
          ],
        },
        {
          key: 'inquiryType',
          label: '문의 유형',
          options: Object.entries(INQUIRY_TYPE_LABEL).map(([value, label]) => ({ label, value })),
        },
        { key: 'educationType', label: '수업 방식', options: EDUCATION_TYPE_OPTIONS_ALL },
        {
          key: 'institutionId',
          label: '기관',
          options: this.institutionOptions,
        },
        {
          key: 'courseId',
          label: '강의',
          options: this.courseOptions,
        },
      ];
    },
    /**
     * 숫자 카드 — 목록(최대 100건, 필터 적용)으로 세면 필터를 바꿀 때마다 숫자가 바뀌고 100건에서 잘렸다.
     * 상태별 총계를 서버에서 따로 받는다. '이번 주 신규'는 총계 API 가 없어 뺐다
     */
    // 100건까지만 받는다 — 더 있으면 목록 제목에 밝힌다
    listTitle() {
      return this.serverTotal > this.rows.length ? `문의 목록 (최근 ${this.rows.length}건)` : '문의 목록';
    },
    stats() {
      const { pending, answered } = this.counts;
      const show = (value) => (value === null ? '-' : value);
      const total = pending === null || answered === null ? null : pending + answered;
      return [
        { label: '전체 문의', value: show(total), unit: total === null ? '' : '건' },
        { label: '답변 대기', value: show(pending), unit: pending === null ? '' : '건' },
        { label: '답변 완료', value: show(answered), unit: answered === null ? '' : '건' },
      ];
    },
  },
  created() {
    this.load();
    this.loadCounts();
    this.loadFilterOptions();
  },
  methods: {
    storedFileContentUrl,
    formatDateTimeFields,
    maskPhone,
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase();
    },
    openReply(row) {
      this.replyTarget = row;
      this.showReply = true;
    },
    onSearch(payload) {
      this.search = payload;
      this.applyServerSearch();
    },
    async loadFilterOptions() {
      const [institutionPage, coursePage] = await Promise.all([
        listInstitutions({ page: 0, size: 100 }).catch(() => ({ data: [] })),
        listCourses({ page: 0, size: 100 }).catch(() => ({ data: [] })),
      ]);
      this.institutionOptions = toInstitutionOptions(institutionPage.data);
      this.courseOptions = (coursePage.data || []).map((item) => ({
        label: formatCourseOption(item),
        value: item.id,
      }));
    },
    onReplied() {
      this.load();
      this.loadCounts();
    },
    async loadCounts() {
      const count = (status) =>
        listAdminInquiries({ page: 0, size: 1, status })
          .then((page) => (Number.isFinite(page?.page?.totalElements) ? page.page.totalElements : null))
          .catch(() => null);
      const [pending, answered] = await Promise.all([count('PENDING'), count('ANSWERED')]);
      this.counts = { pending, answered };
    },
    async load() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listAdminInquiries(buildInquiryQuery(this.search));
        this.rememberTotal(result);
        this.rows = (result.data || []).map((row) => ({
          ...row,
          course: row.courseId ? { ...toCourseCard(row), title: row.courseTitle } : null,
        }));
      } catch (error) {
        this.handleLoadError(error, '문의 목록 조회 실패');
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>

<style scoped>
/* dense 버튼은 글자에 테두리가 붙어 네모로 보였다 */
.reply-btn {
  min-width: 56px;
  padding: 0 12px;
}
</style>
