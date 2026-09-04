<template>
  <q-page padding>
    <div class="page-wrap">
      <div class="app-page-header q-mb-lg">
        <div class="app-page-header__title text-h5 text-weight-bold">강의 등록</div>
      </div>
      <q-banner class="bg-blue-1 q-mb-md">POST /courses · 모집 전 과정 생성</q-banner>
      <q-stepper v-model="step" color="primary" animated flat bordered>
        <q-step :name="1" title="기본정보" icon="edit" :done="step > 1">
          <q-input v-model="form.courseCode" outlined label="과정 코드 *" class="q-mb-md" />
          <q-input v-model="form.title" outlined label="강의명 *" class="q-mb-md" />
          <q-input v-model="form.category" outlined label="카테고리" class="q-mb-md" />
          <q-select
            v-model="form.educationType"
            outlined
            emit-value
            map-options
            :options="eduOptions"
            label="교육 유형 *"
          />
          <q-stepper-navigation>
            <q-btn color="primary" label="다음" @click="nextFromBasic" />
          </q-stepper-navigation>
        </q-step>
        <q-step :name="2" title="일정" icon="event" :done="step > 2">
          <div class="row q-col-gutter-md">
            <q-input v-model="form.recruitStartDate" outlined type="date" label="모집 시작" class="col-12 col-sm-6" />
            <q-input v-model="form.recruitEndDate" outlined type="date" label="모집 종료" class="col-12 col-sm-6" />
            <q-input v-model="form.startDate" outlined type="date" label="교육 시작 *" class="col-12 col-sm-6" />
            <q-input v-model="form.endDate" outlined type="date" label="교육 종료 *" class="col-12 col-sm-6" />
          </div>
          <q-input v-model="form.location" outlined label="장소" class="q-mt-md" />
          <q-stepper-navigation>
            <q-btn color="primary" label="다음" @click="step = 3" />
            <q-btn flat label="이전" @click="step = 1" />
          </q-stepper-navigation>
        </q-step>
        <q-step :name="3" title="추가정보" icon="info" :done="step > 3">
          <q-input v-model="form.description" type="textarea" outlined label="강의 소개" />
          <q-input v-model.number="form.capacity" type="number" outlined label="모집 정원 *" class="q-mt-md" />
          <q-input v-model="form.onlineUrl" outlined label="온라인 URL" class="q-mt-md" />
          <q-stepper-navigation>
            <q-btn color="primary" label="미리보기" @click="step = 4" />
            <q-btn flat label="이전" @click="step = 2" />
          </q-stepper-navigation>
        </q-step>
        <q-step :name="4" title="미리보기" icon="preview">
          <q-card flat bordered>
            <q-card-section>
              <div class="text-h5 text-weight-bold">{{ form.title || '강의명 미입력' }}</div>
              <div class="row items-center q-gutter-sm q-mt-sm">
                <span class="text-grey-7">{{ form.category }}</span>
                <EducationTypeBadge :type="form.educationType" />
              </div>
              <p class="q-mt-lg">{{ form.description || '강의 소개가 없습니다.' }}</p>
              <div>{{ $formatDatePeriodKst(form.startDate, form.endDate) }} · 정원 {{ form.capacity }}명</div>
            </q-card-section>
          </q-card>
          <q-stepper-navigation>
            <q-btn color="primary" :loading="saving" label="등록하기" @click="submit" />
            <q-btn flat label="이전" @click="step = 3" />
          </q-stepper-navigation>
        </q-step>
      </q-stepper>
    </div>
  </q-page>
</template>

<script>
import { mapState } from 'pinia';
import EducationTypeBadge from '@/components/EducationTypeBadge.vue';
import { DEFAULT_INSTITUTION_ID } from '@/config/api';
import { createCourse } from '@/services/course-api';
import { useAuthStore } from '@/stores/auth-store';
import { EDUCATION_TYPE_OPTIONS } from '@/utils/status';

function addDays(n) {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d.toISOString().slice(0, 10);
}

export default {
  name: 'InstructorCourseCreatePage',
  components: { EducationTypeBadge },
  data() {
    return {
      step: 1,
      saving: false,
      form: {
        courseCode: '',
        title: '',
        category: 'IT·디지털',
        educationType: 'ONLINE',
        recruitStartDate: addDays(0),
        recruitEndDate: addDays(14),
        startDate: addDays(21),
        endDate: addDays(50),
        location: '',
        description: '',
        capacity: 20,
        onlineUrl: '',
      },
      eduOptions: EDUCATION_TYPE_OPTIONS,
    };
  },
  computed: {
    ...mapState(useAuthStore, ['primaryInstitutionId']),
  },
  methods: {
    nextFromBasic() {
      if (!this.form.courseCode || !this.form.title) {
        this.$q.notify({ type: 'warning', message: '과정 코드와 강의명을 입력해 주세요.' });
        return;
      }
      this.step = 2;
    },
    async submit() {
      if (!this.form.startDate || !this.form.endDate || !this.form.capacity) {
        this.$q.notify({ type: 'warning', message: '교육 기간과 정원을 입력해 주세요.' });
        return;
      }
      this.saving = true;
      try {
        await createCourse({
          institutionId: this.primaryInstitutionId || DEFAULT_INSTITUTION_ID,
          courseCode: this.form.courseCode.trim(),
          title: this.form.title.trim(),
          description: this.form.description || null,
          category: this.form.category || null,
          educationType: this.form.educationType,
          startDate: this.form.startDate,
          endDate: this.form.endDate,
          recruitStartDate: this.form.recruitStartDate || null,
          recruitEndDate: this.form.recruitEndDate || null,
          capacity: Number(this.form.capacity) || 20,
          location: this.form.educationType === 'ONLINE' ? null : this.form.location || '미정',
          onlineUrl: this.form.onlineUrl || null,
          creditBankEligible: false,
          creditValue: null,
        });
        this.$q.notify({ type: 'positive', message: '강의가 등록되었습니다.', position: 'top' });
        this.$router.push('/instructor/courses');
      } catch (error) {
        this.$q.notify({ type: 'negative', message: error.message || '등록에 실패했습니다.', position: 'top' });
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.page-wrap {
  max-width: 1000px;
  margin: 0 auto;
}
</style>
