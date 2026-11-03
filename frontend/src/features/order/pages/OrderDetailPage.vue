<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchOrder } from '@/features/order/api'
import type { OrderDetail } from '@/features/order/api'
import { formatAmount } from '@/shared/presentation/amount'
import { formatDateTime } from '@/shared/presentation/time'

/**
 * T376: 页面 OrderDetailPage。
 *
 * <p>成交金额与商品文本取自订单快照：商品之后被删除或改价都不影响历史订单的显示金额
 * （ORD-19、Q15、D05）。模拟卡只展示卡型与尾号（NFR-04）。他人订单与不存在订单统一按
 * 不可见处理（ORD-18、NFR-02）。</p>
 */

const props = defineProps<{ orderId: string }>()

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const missing = ref(false)

onMounted(async () => {
  try {
    order.value = await fetchOrder(httpClient, props.orderId)
  } catch {
    missing.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="order-detail">
    <h1>订单详情</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="missing" class="order-detail__missing">订单不存在或无权查看。</p>
    <template v-else-if="order">
      <p class="order-detail__id">订单号：{{ order.orderId }}</p>
      <p class="order-detail__time">{{ formatDateTime(order.createdAt) }}</p>
      <p class="order-detail__status">{{ order.statusLabel }}</p>
      <table>
        <thead>
          <tr>
            <th scope="col">商品</th>
            <th scope="col">数量</th>
            <th scope="col">成交单价</th>
            <th scope="col">小计</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in order.items" :key="line.itemId">
            <td>{{ line.productName }}</td>
            <td>{{ line.quantity }}</td>
            <td>{{ formatAmount(line.unitPrice) }}</td>
            <td>{{ formatAmount(line.lineAmount) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="order-detail__total">合计：{{ formatAmount(order.totalAmount) }}</p>
      <p class="order-detail__payment">
        支付方式：{{ order.payment.cardType }} ****{{ order.payment.lastFour }}
      </p>
    </template>
  </section>
</template>
