<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { useCartStore } from '@/features/cart/store'
import { useOrderStore } from '@/features/order/store'
import AddressForm from '@/features/order/components/AddressForm.vue'
import FakePaymentForm from '@/features/order/components/FakePaymentForm.vue'
import type { components } from '@/shared/api/schema'

/**
 * T370: 页面 CheckoutPage。
 *
 * <p>提交两组地址与模拟支付，由服务端生成确认快照；页面不自行计算金额，也不显示隐藏字段作为
 * 成交依据（plan §5.5、ORD-06/13）。快照失效时要求重新确认（Q03）。</p>
 */

type Address = components['schemas']['Address']
type FakePaymentRequest = components['schemas']['FakePaymentRequest']

const cart = useCartStore()
const order = useOrderStore()

const billingAddress = ref<Address>({
  firstName: '',
  lastName: '',
  address1: '',
  address2: null,
  city: '',
  state: '',
  zip: '',
  country: '',
})
const shippingAddress = ref<Address>({ ...billingAddress.value })
const payment = ref<FakePaymentRequest>({ cardType: 'Visa', cardNumber: '', expiryDate: '' })

onMounted(async () => {
  await cart.load()
})

async function submit(): Promise<void> {
  await order.createCheckout({
    cartVersion: cart.cart?.version ?? 0,
    billingAddress: billingAddress.value,
    shippingAddress: shippingAddress.value,
    payment: payment.value,
  })
}
</script>

<template>
  <section class="checkout">
    <h1>结算</h1>
    <p v-if="order.error" class="checkout__error">{{ order.error.title }}</p>
    <AddressForm v-model="billingAddress" label="账单地址" />
    <AddressForm v-model="shippingAddress" label="收货地址" />
    <FakePaymentForm v-model="payment" />
    <form @submit.prevent="submit">
      <button type="submit" :disabled="order.placing">生成确认快照</button>
    </form>
    <p v-if="order.checkout" class="checkout__confirmed">
      确认快照已生成：{{ order.checkout.checkoutId }}，有效期至
      {{ order.checkout.expiresAt }}。
    </p>
  </section>
</template>
