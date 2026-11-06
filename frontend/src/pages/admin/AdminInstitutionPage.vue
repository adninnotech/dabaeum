<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">기관 정보 관리</div>
        <div class="app-page-header__subtitle text-grey-7">교육기관을 등록하고 정보를 고칩니다.</div>
      </div>
      <div class="row q-gutter-sm">
        <q-btn outline color="primary" icon="refresh" label="새로고침" :loading="loading" @click="loadInstitutions" />
        <q-btn color="primary" unelevated icon="add" label="기관 등록" @click="openCreate" />
      </div>
    </div>

    <AppErrorBanner :message="errorMessage" />

    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-4">
        <q-list bordered class="rounded-borders">
          <q-item
            v-for="item in institutions"
            :key="item.id"
            clickable
            :active="selectedId === item.id"
            active-class="bg-blue-1"
            @click="selectInstitution(item.id)"
          >
            <q-item-section>
              <q-item-label>{{ item.name }}</q-item-label>
              <q-item-label caption>{{ item.institutionCode }} · {{ STATUS_LABEL[item.status] || item.status }}</q-item-label>
            </q-item-section>
          </q-item>
          <q-item v-if="!loading && !institutions.length">
            <q-item-section class="text-grey-7">등록된 기관이 없습니다.</q-item-section>
          </q-item>
        </q-list>
      </div>

      <div class="col-12 col-md-8">
        <q-card flat bordered>
          <q-card-section class="text-subtitle1 text-weight-bold">
            {{ createMode ? '기관 등록' : '기관 수정' }}
          </q-card-section>
          <q-card-section class="q-gutter-md">
            <q-input v-model="form.institutionCode" outlined dense label="기관 코드 *" :disable="!createMode && !!selectedId" />
            <q-input v-model="form.name" outlined dense label="기관명 *" />
            <q-input v-model="form.businessNumber" outlined dense label="사업자등록번호" />
            <q-input v-model="form.representativeName" outlined dense label="대표자명" />
            <q-input v-model="form.address" outlined dense label="주소" />
            <q-input v-model="form.contactPhone" outlined dense label="대표 연락처" />
            <q-input v-model="form.contactEmail" outlined dense label="대표 이메일" />
            <q-select
              :model-value="form.status"
              outlined
              dense
              emit-value
              map-options
              :options="statusOptions"
              label="상태"
              @update:model-value="setStatus"
            />
            <div class="row q-gutter-sm">
              <q-btn color="primary" unelevated :loading="saving" :label="createMode ? '등록' : '저장'" @click="save" />
              <q-btn v-if="createMode" flat label="취소" @click="cancelCreate" />
            </div>
          </q-card-section>
          <q-inner-loading :showing="loading && !createMode" />
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script>
import { STATUS_LABEL } from '@/utils/status';
import { confirmDialog } from '@/utils/confirm-dialog';
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import {
  createInstitution,
  getInstitution,
  listInstitutions,
  updateInstitution,
} from '@/services/institution-api';

function emptyForm() {
  return {
    institutionCode: '',
    name: '',
    businessNumber: '',
    representativeName: '',
    address: '',
    contactPhone: '',
    contactEmail: '',
    status: 'ACTIVE',
  };
}

export default {
  components: { AppErrorBanner },
  name: 'AdminInstitutionPage',
  data() {
    return {
      institutions: [],
      selectedId: null,
      createMode: false,
      form: emptyForm(),
      loading: false,
      saving: false,
      errorMessage: '',
      STATUS_LABEL,
      previousSelectedId: null,
      originalStatus: 'ACTIVE',
      statusOptions: ['ACTIVE', 'INACTIVE', 'SUSPENDED'].map((value) => ({ label: STATUS_LABEL[value], value })),
    };
  },
  mounted() {
    this.loadInstitutions();
  },
  methods: {
    /** 보던 기관이 목록에 남아 있으면 그대로, 아니면 첫 기관 — 개발용 기본 기관을 먼저 고르지 않는다 */
    preferredInstitution() {
      return this.institutions.find((item) => item.id === this.selectedId) || this.institutions[0];
    },
    setStatus(value) {
      this.form.status = value;
    },
    async loadInstitutions() {
      this.loading = true;
      this.errorMessage = '';
      try {
        // sort: 측정 결과 기본 정렬이 이미 createdAt desc다(00-common §3) — 지운다
        const result = await listInstitutions({ page: 0, size: 50 });
        this.institutions = Array.isArray(result.data) ? result.data : [];
        const preferred = this.preferredInstitution();
        if (preferred) {
          await this.selectInstitution(preferred.id);
        } else {
          this.selectedId = null;
          this.form = emptyForm();
        }
      } catch (error) {
        this.errorMessage = error.message || '기관 목록 조회 실패';
        this.institutions = [];
      } finally {
        this.loading = false;
      }
    },
    async selectInstitution(id) {
      this.createMode = false;
      this.selectedId = id;
      this.loading = true;
      this.errorMessage = '';
      try {
        const data = await getInstitution(id);
        this.form = {
          institutionCode: data.institutionCode || '',
          name: data.name || '',
          businessNumber: data.businessNumber || '',
          representativeName: data.representativeName || '',
          address: data.address || '',
          contactPhone: data.contactPhone || '',
          contactEmail: data.contactEmail || '',
          status: data.status || 'ACTIVE',
        };
        this.originalStatus = this.form.status;
      } catch (error) {
        this.errorMessage = error.message || '기관 정보를 불러오지 못했습니다.';
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.createMode = true;
      this.previousSelectedId = this.selectedId;
      this.selectedId = null;
      this.form = emptyForm();
    },
    cancelCreate() {
      this.createMode = false;
      // 새 기관 입력을 취소하면 보던 기관으로 돌아간다
      this.selectedId = this.previousSelectedId;
      const preferred = this.preferredInstitution();
      if (preferred) this.selectInstitution(preferred.id);
    },
    /** 기관을 정지·비활성으로 바꾸면 소속 강사·강의가 영향을 받는다 — 저장 한 번에 바뀌던 것을 묻는다 */
    async confirmStatusChange() {
      const next = this.form.status;
      if (next === this.originalStatus || next === 'ACTIVE') return true;
      const label = STATUS_LABEL[next] || next;
      return confirmDialog(this.$q, {
        title: `기관 ${label}`,
        message: `'${this.form.name.trim()}' 기관을 '${label}' 상태로 바꿉니다. 소속 강사의 콘솔 이용과 강의 운영이 제한될 수 있습니다.`,
        ok: '바꾸기',
        color: 'negative',
        cancel: '닫기',
      });
    },
    async save() {
      if (!this.form.name || !String(this.form.name).trim()) {
        this.$q.notify({ type: 'warning', message: '기관명은 필수입니다.', position: 'top' });
        return;
      }
      if (this.createMode && (!this.form.institutionCode || !String(this.form.institutionCode).trim())) {
        this.$q.notify({ type: 'warning', message: '기관 코드는 필수입니다.', position: 'top' });
        return;
      }
      if (!this.createMode && !(await this.confirmStatusChange())) return;
      this.saving = true;
      try {
        if (this.createMode) {
          const created = await createInstitution({
            institutionCode: this.form.institutionCode.trim(),
            name: this.form.name.trim(),
            businessNumber: this.form.businessNumber || null,
            representativeName: this.form.representativeName || null,
            address: this.form.address || null,
            contactPhone: this.form.contactPhone || null,
            contactEmail: this.form.contactEmail || null,
            status: this.form.status || 'ACTIVE',
          });
          this.$q.notify({ type: 'positive', message: '기관이 등록되었습니다.', position: 'top' });
          await this.loadInstitutions();
          if (created?.id) await this.selectInstitution(created.id);
        } else {
          await updateInstitution(this.selectedId, {
            name: this.form.name.trim(),
            businessNumber: this.form.businessNumber || null,
            representativeName: this.form.representativeName || null,
            address: this.form.address || null,
            contactPhone: this.form.contactPhone || null,
            contactEmail: this.form.contactEmail || null,
            status: this.form.status || 'ACTIVE',
          });
          this.$q.notify({ type: 'positive', message: '기관 정보가 저장되었습니다.', position: 'top' });
          await this.loadInstitutions();
        }
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message, position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.rounded-borders {
  border-radius: 8px;
}
</style>
