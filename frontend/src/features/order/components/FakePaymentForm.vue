<script setup lang="ts">
import { computed } from 'vue'

import type { components } from '@/shared/api/schema'

/**
 * T346: Vue组件 cardform。
 *
 * <p>只提供三种模拟卡型，明确提示只接受 FAKE- 开头的演示卡号，有效期按 MM/YYYY 填写；不校验
 * 未来日期，也不做 Luhn 或真实卡号处理（Q05、Q15）。卡信息不写入任何存储，组件也不设置自动
 * 填充（NFR-04）。</p>
 */

type FakePaymentRequest = components['schemas']['FakePaymentRequest']

const props = defineProps<{ modelValue: FakePaymentRequest }>()

const emit = defineEmits<{ 'update:modelValue': [FakePaymentRequest] }>()

const cardTypes = ['Visa', 'MasterCard', 'American Express']

const payment = computed(() => props.modelValue)

function update(field: keyof FakePaymentRequest, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}
</script>

<template>
  <fieldset class="fake-payment">
    <legend>模拟支付</legend>
    <p class="fake-payment__hint">只接受演示用假卡号（FAKE- 开头），不会发生真实扣款。</p>
    <label>
      卡类型
      <select name="cardType" :value="payment.cardType" @change="update('cardType', ($event.target as HTMLSelectElement).value)">
        <option v-for="cardType in cardTypes" :key="cardType" :value="cardType">{{ cardType }}</option>
      </select>
    </label>
    <label>
      演示卡号
      <input name="cardNumber" type="text" required autocomplete="off" :value="payment.cardNumber"
             @input="update('cardNumber', ($event.target as HTMLInputElement).value)" />
    </label>
    <label>
      有效期
      <input name="expiryDate" type="text" required placeholder="MM/YYYY" :value="payment.expiryDate"
             @input="update('expiryDate', ($event.target as HTMLInputElement).value)" />
    </label>
  </fieldset>
</template>

<style scoped>
.fake-payment {
  display: grid;
  gap: 0.5rem;
  border: 1px solid #d6d6d6;
  padding: 0.75rem;
}

.fake-payment__hint {
  margin: 0;
  color: #605e5c;
  font-size: 0.875rem;
}
</style>
