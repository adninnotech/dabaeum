<template>
  <section class="tab-section">
    <header class="tab-section__head">
      <h2 class="tab-section__title">
        {{ title }}<template v-if="count !== null"> ({{ count }})</template>
      </h2>
      <div class="tab-section__actions"><slot name="actions" /></div>
    </header>
    <slot />
  </section>
</template>

<script>
export default {
  name: 'ConsoleTabSection',
  props: {
    title: { type: String, required: true },
    count: { type: Number, default: null },
  },
};
</script>

<style scoped>
.tab-section {
  display: flex;
  flex-direction: column;
}

.tab-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 52px;
  padding: 0 24px;
}

.tab-section__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--db-text-title);
}

.tab-section__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
