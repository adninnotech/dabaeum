<template>
  <!-- 소속 강사 — 목록(폰 카드)과 정보 수정 -->
  <div>
    <AppErrorBanner :message="errorMessage">
      <template #action>
        <q-btn flat dense no-caps color="negative" label="다시 시도" @click="load" />
      </template>
    </AppErrorBanner>
    <p v-if="capped" class="capped-note">강사 {{ PAGE_SIZE }}명까지만 보여 줍니다.</p>
    <q-table
      flat
      bordered
      :rows="rows"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :grid="$q.screen.xs"
      no-data-label="소속 강사가 없습니다. '강사 신청' 탭에서 신청을 승인하면 여기에 나타납니다."
      :pagination="{ rowsPerPage: 10 }"
      :rows-per-page-options="[10, 20, 50]"
      rows-per-page-label="한 번에 보기"
      :pagination-label="paginationLabel"
      class="clickable-table"
      @row-click="onRowClick"
    >
      <!-- 표 기본 '데이터 없음'은 경고(⚠) 아이콘이라 처리할 일이 없는 것도 오류처럼 보였다 -->
      <template #no-data="{ message }">
        <div class="table-empty">{{ message }}</div>
      </template>
      <!-- 행 어디를 눌러도 [수정] 창 — 표 행은 포커스를 받지 못해 이름을 단추로 두어 키보드(Tab·Enter)로도 연다 -->
      <template #body-cell-name="props">
        <q-td :props="props">
          <button type="button" class="row-open" @click="openEdit(props.row)">{{ props.value || '-' }}</button>
        </q-td>
      </template>
      <template #body-cell-status="props">
        <q-td :props="props"><StatusBadge :status="props.value" /></q-td>
      </template>
      <template #body-cell-action="props">
        <q-td :props="props">
          <q-btn flat dense no-caps color="primary" label="수정" @click="openEdit(props.row)" />
        </q-td>
      </template>
      <template #item="props">
        <div class="col-12">
          <!-- 카드 어디를 눌러도 [수정] 창. 이름 줄이 단추(키보드), 따로 둔 [수정] 버튼은 뺐다 -->
          <div class="item-card clickable-card" @click="onRowClick($event, props.row)">
            <button
              type="button"
              class="item-card__head item-card__open"
              :aria-label="`${props.row.name || '강사'} 메모 수정`"
              @click="openEdit(props.row)"
            >
              <span class="item-card__name">{{ props.row.name || '-' }}</span>
              <span class="item-card__side">
                <StatusBadge :status="props.row.status" />
                <q-icon name="chevron_right" size="20px" class="item-card__chevron" aria-hidden="true" />
              </span>
            </button>
            <div class="item-card__meta">
              {{ [props.row.email, props.row.phone && maskPhone(props.row.phone)].filter(Boolean).join(' · ') || '-' }}
            </div>
            <div v-if="props.row.memo" class="item-card__meta">메모: {{ props.row.memo }}</div>
          </div>
        </div>
      </template>
    </q-table>

    <q-dialog v-model="editOpen" :persistent="saving">
      <q-card class="panel-dialog memo-dialog">
        <!-- 누구 메모인지 먼저 — 예전엔 '강사 메모 수정' 아래 이름·이메일이 연락처와 같은 줄 크기라 대상이 묻혔다 -->
        <q-card-section class="memo-dialog__head">
          <div class="memo-dialog__who">
            <div class="memo-dialog__eyebrow">강사 메모 수정</div>
            <h2 class="memo-dialog__name">{{ editTarget.name || '-' }}</h2>
            <div class="memo-dialog__email">{{ editTarget.email || '-' }}</div>
          </div>
          <q-btn v-close-popup flat round dense icon="close" aria-label="닫기" :disable="saving" />
        </q-card-section>

        <!-- q-gutter-md 는 자식에 margin-left 를 줘 margin: 0 인 요약·안내만 왼쪽에 붙었다(QA #46) — 세로 간격만 gap 으로 -->
        <q-card-section class="edit-body">
          <!-- 기관 관리자가 쓸 수 있는 건 메모뿐 — PUT /users/{id}·PATCH /users/{id}/status 는 플랫폼 관리자 전용이라 403 이었다.
               읽기 전용 정보는 옅은 상자에 묶어, 고칠 수 있는 메모 칸과 갈라 보인다 -->
          <section class="edit-info" aria-label="강사 정보(읽기 전용)">
            <dl class="edit-summary">
              <div><dt>연락처</dt><dd>{{ editTarget.phone || '-' }}</dd></div>
              <div><dt>생년월일</dt><dd>{{ editTarget.birthDate || '-' }}</dd></div>
              <div><dt>상태</dt><dd><StatusBadge :status="editTarget.status" soft size="sm" /></dd></div>
            </dl>
            <p class="edit-note">
              <q-icon name="info" size="16px" aria-hidden="true" />
              이름·연락처·상태는 플랫폼 관리자가 바꿉니다.
            </p>
          </section>

          <div class="edit-memo">
            <label class="edit-memo__label" for="instructor-memo">기관 메모</label>
            <q-input
              v-model="editMemo"
              for="instructor-memo"
              outlined
              type="textarea"
              :input-style="{ minHeight: '96px' }"
              autogrow
              :maxlength="MEMO_MAX"
              counter
              placeholder="예: 주말 강의 가능, 2026년 하반기 재계약"
              hint="기관 관리자만 보는 메모입니다."
            />
          </div>
        </q-card-section>
        <q-card-actions align="right" class="memo-dialog__actions">
          <q-btn v-close-popup flat no-caps label="닫기" :disable="saving" />
          <q-btn color="primary" unelevated no-caps :loading="saving" label="저장" @click="saveEdit" />
        </q-card-actions>
      </q-card>
    </q-dialog>
  </div>
</template>

<script>
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import { listInstitutionInstructors, updateInstitutionInstructorMemo } from '@/services/institution-api';
import { maskPhone } from '@/utils/user-display';

const PAGE_SIZE = 100;
// InstitutionInstructor.memo maxLength
const MEMO_MAX = 1000;

export default {
  name: 'InstructorRosterPanel',
  components: { AppErrorBanner, StatusBadge },
  props: {
    institutionId: { type: String, default: null },
  },
  data() {
    return {
      PAGE_SIZE,
      rows: [],
      capped: false,
      loading: false,
      errorMessage: '',
      saving: false,
      MEMO_MAX,
      editOpen: false,
      editTarget: {},
      editMemo: '',
      columns: [
        { name: 'name', label: '이름', field: 'name', align: 'left' },
        { name: 'email', label: '이메일', field: 'email', align: 'left' },
        // 강사 신청 탭처럼 가린다 — 전체 번호는 [수정] 창에서. 글자 칸은 왼쪽, 뱃지는 가운데
        { name: 'phone', label: '연락처', field: 'phone', align: 'left', format: maskPhone },
        // 비어 있으면 빠진 건지 없는 건지 몰랐다
        { name: 'memo', label: '메모', field: 'memo', align: 'left', format: (value) => value || '-', style: 'max-width: 200px; white-space: normal' },
        { name: 'status', label: '상태', field: 'status', align: 'center' },
        { name: 'action', label: '처리', field: 'id', align: 'right' },
      ],
    };
  },
  watch: {
    institutionId: { immediate: true, handler: 'load' },
  },
  methods: {
    maskPhone,
    paginationLabel(first, last, total) {
      return `총 ${total}건 중 ${first}-${last}`;
    },
    async load() {
      if (!this.institutionId) {
        this.rows = [];
        return;
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const page = await listInstitutionInstructors(this.institutionId, { page: 0, size: PAGE_SIZE, sort: 'name,asc' });
        this.rows = (page.data || []).map((row) => ({ ...row, id: row.userId }));
        this.capped = (page.page?.totalElements || 0) > PAGE_SIZE;
      } catch (error) {
        this.rows = [];
        this.errorMessage = error.message || '소속 강사를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    // [수정] 버튼을 누른 건 버튼이 연다 — 행 클릭으로 한 번 더 열지 않는다
    onRowClick(event, row) {
      if (event?.target?.closest?.('a, button')) return;
      this.openEdit(row);
    },
    openEdit(row) {
      if (!row?.id) return;
      this.editTarget = { ...row, birthDate: row.birthDate ? String(row.birthDate).slice(0, 10) : '' };
      this.editMemo = row.memo || '';
      this.editOpen = true;
    },
    async saveEdit() {
      const memo = String(this.editMemo || '').trim();
      if (memo === String(this.editTarget.memo || '').trim()) {
        this.editOpen = false;
        return;
      }
      this.saving = true;
      try {
        // 빈 메모는 null 로 보내 지운다
        await updateInstitutionInstructorMemo(this.institutionId, this.editTarget.id, memo || null);
        this.editOpen = false;
        this.$q.notify({ type: 'positive', message: `${this.editTarget.name || '강사'} 님의 메모를 저장했습니다.`, position: 'top' });
        await this.load();
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '메모를 저장하지 못했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped src="./institution-panel.css"></style>
<style scoped>
.item-card__chevron {
  color: var(--db-text-sub);
}

.memo-dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
}

.memo-dialog__who {
  min-width: 0;
}

.memo-dialog__eyebrow {
  font-size: 13px;
  color: var(--db-text-sub);
}

.memo-dialog__name {
  margin: 2px 0 0;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--db-text-title);
  overflow-wrap: anywhere;
}

.memo-dialog__email {
  margin-top: 2px;
  font-size: 14px;
  color: var(--db-text-sub);
  overflow-wrap: anywhere;
}

.edit-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 0;
}

.edit-info {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--db-bg-subtle);
}

.edit-summary {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: center;
  gap: 10px 16px;
  margin: 0;
  font-size: 14px;
}

.edit-summary > div {
  display: contents;
}

.edit-summary dt {
  color: var(--db-text-sub);
}

.edit-summary dd {
  margin: 0;
  color: var(--db-text-body);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.edit-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--db-border);
  font-size: 13px;
  color: var(--db-text-sub);
  word-break: keep-all;
}

.edit-memo__label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--db-text-title);
}

.memo-dialog__actions {
  padding: 8px 16px 16px;
}
</style>
