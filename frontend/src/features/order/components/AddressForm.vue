<script setup lang="ts">
import { computed } from 'vue'

import type { components } from '@/shared/api/schema'

/**
 * T344: Vue组件 address。
 *
 * <p>账单与收货各自持有一套模型：组件只发出自己这一套的更新，不触碰另一套（plan §5.5）。
 * 姓名与地址必填，第二行选填（Q01、Q15）。</p>
 */

type Address = components['schemas']['Address']

const props = defineProps<{ modelValue: Address; label: string }>()

const emit = defineEmits<{ 'update:modelValue': [Address] }>()

const fields = computed(() => props.modelValue)

function update(field: keyof Address, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}
</script>

<template>
  <fieldset class="address-form">
    <legend>{{ label }}</legend>
    <label>
      名字
      <input name="firstName" type="text" required :value="fields.firstName"
             @input="update('firstName', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      姓氏
      <input name="lastName" type="text" required :value="fields.lastName"
             @input="update('lastName', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      地址
      <input name="address1" type="text" required :value="fields.address1"
             @input="update('address1', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      地址第二行（选填）
      <input name="address2" type="text" :value="fields.address2 ?? ''"
             @input="update('address2', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      城市
      <input name="city" type="text" required :value="fields.city"
             @input="update('city', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      省/州
      <input name="state" type="text" required :value="fields.state"
             @input="update('state', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      邮编
      <input name="zip" type="text" required :value="fields.zip"
             @input="update('zip', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      国家
      <input name="country" type="text" required :value="fields.country"
             @input="update('country', ($event.target as HTMLInputElement).value)" />
    </label>
  </fieldset>
</template>

<style scoped>
.address-form {
  display: grid;
  gap: 0.5rem;
  border: 1px solid #d6d6d6;
  padding: 0.75rem;
}
</style>
