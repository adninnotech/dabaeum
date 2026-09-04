<template>
  <q-card flat bordered class="filter-panel">
    <q-card-section class="row items-center justify-between q-pb-none">
      <div class="text-subtitle1 text-weight-bold">필터</div>
      <q-btn flat dense color="grey-7" icon="refresh" label="초기화" @click="reset" />
    </q-card-section>

    <q-card-section class="q-gutter-md">
      <q-select
        :model-value="region"
        outlined
        dense
        emit-value
        map-options
        :options="regionOptions"
        label="지역"
        @update:model-value="setRegion"
      />
      <q-select
        :model-value="field"
        outlined
        dense
        emit-value
        map-options
        :options="fieldOptions"
        label="분야"
        @update:model-value="setField"
      />
      <q-select
        :model-value="completionType"
        outlined
        dense
        emit-value
        map-options
        :options="completionOptions"
        label="이수유형"
        @update:model-value="setCompletionType"
      />
      <q-select
        :model-value="recruitStatus"
        outlined
        dense
        emit-value
        map-options
        :options="recruitOptions"
        label="모집상태"
        @update:model-value="setRecruitStatus"
      />

      <div>
        <div class="text-body2 text-weight-medium q-mb-sm">학습비</div>
        <q-option-group
          :model-value="fee"
          :options="feeOptions"
          color="primary"
          type="radio"
          dense
          @update:model-value="setFee"
        />
      </div>

      <div>
        <div class="text-body2 text-weight-medium q-mb-sm">학습기간</div>
        <div class="row q-col-gutter-sm items-center">
          <div class="col">
            <q-input
              :model-value="startDate"
              outlined
              dense
              label="시작일"
              mask="####-##-##"
              @update:model-value="setStartDate"
            >
              <template #append>
                <q-icon name="event" class="cursor-pointer">
                  <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                    <q-date
                      :model-value="startDate"
                      mask="YYYY-MM-DD"
                      @update:model-value="setStartDate"
                    >
                      <div class="row items-center justify-end">
                        <q-btn v-close-popup label="닫기" color="primary" flat />
                      </div>
                    </q-date>
                  </q-popup-proxy>
                </q-icon>
              </template>
            </q-input>
          </div>
          <div class="col-auto text-grey-6">~</div>
          <div class="col">
            <q-input
              :model-value="endDate"
              outlined
              dense
              label="종료일"
              mask="####-##-##"
              @update:model-value="setEndDate"
            >
              <template #append>
                <q-icon name="event" class="cursor-pointer">
                  <q-popup-proxy cover transition-show="scale" transition-hide="scale">
                    <q-date
                      :model-value="endDate"
                      mask="YYYY-MM-DD"
                      @update:model-value="setEndDate"
                    >
                      <div class="row items-center justify-end">
                        <q-btn v-close-popup label="닫기" color="primary" flat />
                      </div>
                    </q-date>
                  </q-popup-proxy>
                </q-icon>
              </template>
            </q-input>
          </div>
        </div>
      </div>

      <q-btn color="primary" class="full-width" unelevated label="검색하기" @click="emitSearch" />
    </q-card-section>
  </q-card>
</template>

<script>
export default {
  name: 'CourseFilterPanel',
  props: {
    modelValue: {
      type: Object,
      default: () => ({
        region: 'all',
        field: 'all',
        completionType: 'all',
        recruitStatus: 'all',
        fee: 'all',
        startDate: '',
        endDate: '',
      }),
    },
  },
  emits: ['update:modelValue', 'search'],
  data() {
    return {
      region: 'all',
      field: 'all',
      completionType: 'all',
      recruitStatus: 'all',
      fee: 'all',
      startDate: '',
      endDate: '',
      regionOptions: [
        { label: '전체', value: 'all' },
        { label: '서울', value: 'seoul' },
        { label: '부산', value: 'busan' },
        { label: '대구', value: 'daegu' },
        { label: '인천', value: 'incheon' },
      ],
      fieldOptions: [
        { label: '전체', value: 'all' },
        { label: 'IT/디지털', value: 'it' },
        { label: '인문교양', value: 'humanities' },
        { label: '문화/예술', value: 'arts' },
      ],
      completionOptions: [
        { label: '전체', value: 'all' },
        { label: '학점은행제', value: 'credit' },
        { label: '일반이수', value: 'general' },
      ],
      recruitOptions: [
        { label: '전체', value: 'all' },
        { label: '모집중', value: 'open' },
        { label: '마감임박', value: 'closing' },
        { label: '모집마감', value: 'closed' },
      ],
      feeOptions: [
        { label: '전체', value: 'all' },
        { label: '무료', value: 'free' },
        { label: '유료', value: 'paid' },
      ],
    };
  },
  watch: {
    modelValue: {
      deep: true,
      immediate: true,
      handler(value) {
        if (!value) return;
        this.region = value.region;
        this.field = value.field;
        this.completionType = value.completionType;
        this.recruitStatus = value.recruitStatus;
        this.fee = value.fee;
        this.startDate = value.startDate;
        this.endDate = value.endDate;
      },
    },
  },
  methods: {
    currentFilter() {
      return {
        region: this.region,
        field: this.field,
        completionType: this.completionType,
        recruitStatus: this.recruitStatus,
        fee: this.fee,
        startDate: this.startDate,
        endDate: this.endDate,
      };
    },
    setRegion(value) {
      this.region = value;
    },
    setField(value) {
      this.field = value;
    },
    setCompletionType(value) {
      this.completionType = value;
    },
    setRecruitStatus(value) {
      this.recruitStatus = value;
    },
    setFee(value) {
      this.fee = value;
    },
    setStartDate(value) {
      this.startDate = value;
    },
    setEndDate(value) {
      this.endDate = value;
    },
    emitSearch() {
      this.$emit('update:modelValue', this.currentFilter());
      this.$emit('search');
    },
    reset() {
      this.region = 'all';
      this.field = 'all';
      this.completionType = 'all';
      this.recruitStatus = 'all';
      this.fee = 'all';
      this.startDate = '';
      this.endDate = '';
      this.emitSearch();
    },
  },
};
</script>

<style scoped lang="scss">
.filter-panel {
  border-radius: 14px;
  position: sticky;
  top: 88px;
}
</style>
