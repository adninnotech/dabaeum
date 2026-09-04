<template>
  <q-page padding>
    <AppPageHeader title="공지사항 관리" subtitle="플랫폼 공지사항을 관리합니다.">
      <template #actions>
        <q-btn color="primary" icon="add" label="공지 등록" />
      </template>
    </AppPageHeader>
    <q-table flat bordered :rows="notices" :columns="columns" row-key="id">
      <template #body-cell-action="props">
        <q-td :props="props">
          <q-btn flat dense color="primary" label="수정" />
          <q-btn flat dense color="negative" label="삭제" @click="remove(props.row.id)" />
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
import AppPageHeader from '@/components/AppPageHeader.vue';
import { MOCK_NOTICES } from '@/data/ui-mock.js';

export default {
  name: 'AdminNoticesPage',
  components: { AppPageHeader },
  data() {
    return {
      notices: [...MOCK_NOTICES],
      columns: [
        { name: 'title', label: '제목', field: 'title', align: 'left' },
        { name: 'date', label: '등록일', field: 'date' },
        { name: 'action', label: '관리', field: 'action' },
      ],
    };
  },
  methods: {
    remove(id) {
      this.notices = this.notices.filter((item) => item.id !== id);
    },
  },
};
</script>
