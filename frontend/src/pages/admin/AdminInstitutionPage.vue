<template>
  <q-page padding>
    <div class="app-page-header row items-center justify-between q-mb-lg">
      <div>
        <div class="app-page-header__title text-h5 text-weight-bold">기관 정보 관리</div>
        <div class="app-page-header__subtitle text-grey-7">GET/PUT /institutions · 실제 API 연동</div>
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
              <q-item-label caption>{{ item.institutionCode }} · {{ item.status }}</q-item-label>
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
            <span v-if="!createMode && selectedId" class="text-caption text-grey-7 q-ml-sm">{{ selectedId }}</span>
          </q-card-section>
          <q-card-section class="q-gutter-md">
            <q-input v-model="form.institutionCode" outlined dense label="institutionCode *" :disable="!createMode && !!selectedId" />
            <q-input v-model="form.name" outlined dense label="name *" />
            <q-input v-model="form.businessNumber" outlined dense label="businessNumber" />
            <q-input v-model="form.representativeName" outlined dense label="representativeName" />
            <q-input v-model="form.address" outlined dense label="address" />
            <q-input v-model="form.contactPhone" outlined dense label="contactPhone" />
            <q-input v-model="form.contactEmail" outlined dense label="contactEmail" />
            <q-select
              :model-value="form.status"
              outlined
              dense
              emit-value
              map-options
              :options="statusOptions"
              label="status"
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
import AppErrorBanner from '@/components/AppErrorBanner.vue';
import { DEFAULT_INSTITUTION_ID } from '@/config/api';
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
      statusOptions: [
        { label: 'ACTIVE', value: 'ACTIVE' },
        { label: 'INACTIVE', value: 'INACTIVE' },
        { label: 'SUSPENDED', value: 'SUSPENDED' },
      ],
    };
  },
  mounted() {
    this.loadInstitutions();
  },
  methods: {
    setStatus(value) {
      this.form.status = value;
    },
    async loadInstitutions() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await listInstitutions({ page: 0, size: 50, sort: 'createdAt,desc' });
        this.institutions = Array.isArray(result.data) ? result.data : [];
        const preferred =
          this.institutions.find((i) => i.id === DEFAULT_INSTITUTION_ID) || this.institutions[0];
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
      } catch (error) {
        this.errorMessage = error.message || '기관 상세 조회 실패';
      } finally {
        this.loading = false;
      }
    },
    openCreate() {
      this.createMode = true;
      this.selectedId = null;
      this.form = emptyForm();
    },
    cancelCreate() {
      this.createMode = false;
      const preferred =
        this.institutions.find((i) => i.id === DEFAULT_INSTITUTION_ID) || this.institutions[0];
      if (preferred) this.selectInstitution(preferred.id);
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
