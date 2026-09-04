<template>
  <q-page padding>
    <div class="app-page-header q-mb-lg">
      <div class="app-page-header__title text-h5 text-weight-bold">수강 현황 관리</div>
      <div class="app-page-header__subtitle text-grey-7">수강생의 진도와 출결 현황을 확인합니다.</div>
    </div>
    <q-table flat bordered :rows="rows" :columns="columns" row-key="id">
      <template #body-cell-progress="props">
        <q-td :props="props">
          <q-linear-progress rounded size="10px" :value="props.value / 100" color="primary" />
          <span class="text-caption">{{ props.value }}%</span>
        </q-td>
      </template>
    </q-table>
  </q-page>
</template>

<script>
export default {
  name: 'InstructorEnrollmentStatusPage',
  data() {
    return {
      rows: [
        { id: 1, name: '김다은', course: '비즈니스 모델 설계', attendance: '7/8', progress: 88, status: '수강중' },
        { id: 2, name: '박하늘', course: '생활 속 데이터 리터러시', attendance: '8/8', progress: 100, status: '수료' },
      ],
      columns: [
        { name: 'name', label: '수강생', field: 'name', align: 'left' },
        { name: 'course', label: '강의명', field: 'course', align: 'left' },
        { name: 'attendance', label: '출석', field: 'attendance' },
        { name: 'progress', label: '진도율', field: 'progress' },
        { name: 'status', label: '상태', field: 'status' },
      ],
    };
  },
};
</script>
