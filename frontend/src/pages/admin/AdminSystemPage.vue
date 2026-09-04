<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">시스템 관리</div>
      <div class="app-page-header__subtitle text-grey-7">GET /system/ping (실제 API)</div>
    </div>

    <div class="row q-col-gutter-md">
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold">헬스체크</div>
            <q-btn class="q-mt-md" color="primary" unelevated label="Ping" :loading="loading" @click="ping" />
            <div v-if="errorMessage" class="text-negative q-mt-md">{{ errorMessage }}</div>
            <q-list v-if="pingResult" class="q-mt-md">
              <q-item v-for="(value, key) in pingResult" :key="key">
                <q-item-section>
                  <q-item-label caption>{{ key }}</q-item-label>
                  <q-item-label>{{ formatValue(value) }}</q-item-label>
                </q-item-section>
              </q-item>
            </q-list>
          </q-card-section>
        </q-card>
      </div>
      <div class="col-12 col-md-6">
        <q-card flat bordered>
          <q-card-section>
            <div class="text-subtitle1 text-weight-bold">환경 정보</div>
            <q-list>
              <q-item>
                <q-item-section>
                  <q-item-label caption>API Base</q-item-label>
                  <q-item-label>{{ apiBase }}</q-item-label>
                </q-item-section>
              </q-item>
              <q-item>
                <q-item-section>
                  <q-item-label caption>Bearer Token</q-item-label>
                  <q-item-label>{{ apiToken }}</q-item-label>
                </q-item-section>
              </q-item>
              <q-item>
                <q-item-section>
                  <q-item-label caption>Default userId</q-item-label>
                  <q-item-label>{{ defaultUserId }}</q-item-label>
                </q-item-section>
              </q-item>
            </q-list>
          </q-card-section>
        </q-card>
      </div>
    </div>
  </q-page>
</template>

<script>
import { API_BASE_URL, API_BEARER_TOKEN, DEFAULT_USER_ID } from '@/config/api';
import { pingSystem } from '@/services/system-api';

export default {
  name: 'AdminSystemPage',
  data() {
    return {
      apiBase: API_BASE_URL,
      apiToken: API_BEARER_TOKEN,
      defaultUserId: DEFAULT_USER_ID,
      pingResult: null,
      errorMessage: '',
      loading: false,
    };
  },
  methods: {
    formatValue(value) {
      if (value === null || value === undefined) return '-';
      if (typeof value === 'object') return JSON.stringify(value);
      return String(value);
    },
    async ping() {
      this.loading = true;
      this.errorMessage = '';
      try {
        const result = await pingSystem();
        this.pingResult =
          result && typeof result === 'object' ? result : { result };
        this.$q.notify({ type: 'positive', message: '시스템 ping 성공', position: 'top' });
      } catch (error) {
        this.pingResult = null;
        this.errorMessage = error.message || 'ping 실패';
        this.$q.notify({ type: 'negative', message: this.errorMessage, position: 'top' });
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
