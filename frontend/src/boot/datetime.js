import { defineBoot } from '#q-app';
import { formatDateKst, formatDatePeriodKst, formatDateRangeKst, formatDateTimeFields, formatDateTimeKst } from '@/utils/datetime';

export default defineBoot(({ app }) => {
  app.config.globalProperties.$formatDateKst = formatDateKst;
  app.config.globalProperties.$formatDateTimeKst = formatDateTimeKst;
  app.config.globalProperties.$formatDatePeriodKst = formatDatePeriodKst;
  app.config.globalProperties.$formatDateRangeKst = formatDateRangeKst;

  app.mixin({
    methods: {
      formatDateKst,
      formatDateTimeKst,
      formatDatePeriodKst,
      formatDateRangeKst,
      formatDateTimeFields,
    },
  });
});
