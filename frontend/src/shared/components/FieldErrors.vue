<script setup lang="ts">
import { computed } from 'vue'

import type { FieldViolation } from '@/shared/presentation/problem'

/**
 * T342: Vue组件 field。
 *
 * <p>按字段列出服务端返回的校验说明，并通过 role="alert" 与 aria-live 让辅助技术读出。只展示
 * 字段名与说明，绝不展示被拒绝的原值（NFR-04、D10）。</p>
 */

const props = defineProps<{ violations: readonly FieldViolation[] }>()

const hasViolations = computed(() => props.violations.length > 0)
</script>

<template>
  <ul v-if="hasViolations" class="field-errors" role="alert" aria-live="polite">
    <li v-for="violation in violations" :key="`${violation.field}:${violation.message}`">
      <span class="field-errors__field">{{ violation.field }}</span>
      <span class="field-errors__message">{{ violation.message }}</span>
    </li>
  </ul>
</template>

<style scoped>
.field-errors {
  margin: 0.25rem 0;
  padding-left: 1rem;
  color: #a4262c;
  font-size: 0.875rem;
}

.field-errors__field {
  font-weight: 600;
}
</style>
