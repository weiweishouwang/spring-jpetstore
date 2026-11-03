<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { listOrders } from '@/features/order/api'
import type { OrderSummary } from '@/features/order/api'
import { formatAmount } from '@/shared/presentation/amount'
import { formatDateTime } from '@/shared/presentation/time'

/**
 * T374: 页面 OrdersPage。
 *
 * <p>只显示本人的订单摘要；服务端已按最新优先排序，首期不分页（Q11）；未登录时引导登录
 * （NFR-02）。</p>
 */

const orders = ref<OrderSummary[]>([])
const loading = ref(true)
const unauthorized = ref(false)

onMounted(async () => {
  try {
    orders.value = await listOrders(httpClient)
  } catch (failure) {
    const status = (failure as { status?: number })?.status
    unauthorized.value = status === 401
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="orders">
    <h1>我的订单</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="unauthorized" class="orders__unauthorized">请先登录后查看订单。</p>
    <p v-else-if="orders.length === 0" class="orders__empty">还没有订单。</p>
    <table v-else>
      <thead>
        <tr>
          <th scope="col">订单号</th>
          <th scope="col">时间</th>
          <th scope="col">状态</th>
          <th scope="col">金额</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="order in orders" :key="order.orderId">
          <td><RouterLink :to="`/orders/${order.orderId}`">{{ order.orderId }}</RouterLink></td>
          <td>{{ formatDateTime(order.createdAt) }}</td>
          <td>{{ order.statusLabel }}</td>
          <td>{{ formatAmount(order.totalAmount) }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
