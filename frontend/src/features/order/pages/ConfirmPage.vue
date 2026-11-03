<script setup lang="ts">
import { useOrderStore } from '@/features/order/store'
import { formatAmount } from '@/shared/presentation/amount'
import type { components } from '@/shared/api/schema'

/**
 * T372: 页面 ConfirmPage。
 *
 * <p>显示服务端确认快照的内容与总额；提交复用同一个幂等键，重复点击不会变成第二张订单；价格
 * 或内容变化时服务端返回 409，页面明确要求重新确认（ORD-06/07、Q03、NFR-03）。</p>
 */

type CheckoutResponse = components['schemas']['CheckoutResponse']

defineProps<{ checkout?: CheckoutResponse | null }>()

const order = useOrderStore()
</script>

<template>
  <section class="confirm">
    <h1>确认订单</h1>
    <p v-if="order.error" class="confirm__error">{{ order.error.title }}</p>
    <p v-if="order.error?.code === 'CHECKOUT_STALE'" class="confirm__stale">
      商品价格或购物车内容已变化，请重新确认。
    </p>
    <template v-if="checkout">
      <p class="confirm__total">合计：{{ formatAmount(checkout.totalAmount) }}</p>
      <ul>
        <li v-for="line in checkout.items" :key="line.itemId">
          {{ line.productName }} × {{ line.quantity }} — {{ formatAmount(line.lineAmount) }}
        </li>
      </ul>
      <p class="confirm__payment">支付方式：{{ checkout.payment.cardType }} ****{{ checkout.payment.lastFour }}</p>
      <form @submit.prevent="order.placeOrder()">
        <button type="submit" :disabled="order.placing">提交订单</button>
      </form>
    </template>
    <p v-else class="confirm__missing">没有待确认的快照，请先在结算页生成确认快照。</p>
  </section>
</template>
