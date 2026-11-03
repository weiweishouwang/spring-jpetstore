<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchItem } from '@/features/catalog/api'
import type { Item } from '@/features/catalog/api'
import { formatAmount } from '@/shared/presentation/amount'

/**
 * T358: 页面 ItemPage。
 *
 * <p>售价与库存来自服务端。缺货仍然可以加入购物车，但成交时会被拒绝——提示只是参考，不是库存
 * 预占（CRT-04、Q02、plan §8）。</p>
 */

const props = defineProps<{ itemId: string }>()

const item = ref<Item | null>(null)
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    item.value = await fetchItem(httpClient, props.itemId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="item">
    <p v-if="loading">加载中…</p>
    <p v-else-if="failed">可售项不存在或加载失败。</p>
    <template v-else-if="item">
      <h1>{{ item.productName }}</h1>
      <p class="item__price">{{ formatAmount(item.unitPrice) }}</p>
      <p class="item__stock">库存：{{ item.availableQuantity }}</p>
      <p v-if="!item.inStock" class="item__hint">当前缺货：可以加入购物车，但结算时会被拒绝，不代表已预留。</p>
      <button type="button">加入购物车</button>
    </template>
  </section>
</template>
