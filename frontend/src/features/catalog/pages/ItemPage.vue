<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'
import { useCartStore } from '@/features/cart/store'

import { fetchItem } from '@/features/catalog/api'
import type { Item } from '@/features/catalog/api'
import { formatAmount } from '@/shared/presentation/amount'

/**
 * T358: 页面 ItemPage。
 *
 * <p>售价与库存来自服务端。缺货仍然可以加入购物车，但成交时会被拒绝——提示只是参考，不是库存
 * 预占（CRT-04、Q02、plan §8）。加购走购物车仓库：每次加一、带当前版本（CRT-02）。</p>
 *
 * <p>加购的结果必须可见：写请求有先后（进行中／成功／失败），页面把当前状态讲清楚，用户
 * 才能知道商品有没有真的进车（D10）。</p>
 */

const props = defineProps<{ itemId: string }>()

const cart = useCartStore()

const item = ref<Item | null>(null)
const loading = ref(true)
const failed = ref(false)
/** 加购写请求进行中。 */
const adding = ref(false)
/** 加购已经落地。 */
const added = ref(false)

onMounted(async () => {
  try {
    item.value = await fetchItem(httpClient, props.itemId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function addToCart(): Promise<void> {
  if (!item.value || adding.value) {
    return
  }
  adding.value = true
  added.value = false
  try {
    await cart.add(item.value.itemId)
    added.value = cart.error === null
  } finally {
    adding.value = false
  }
}
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
      <button type="button" :disabled="adding" @click="addToCart">加入购物车</button>
      <p v-if="adding" class="item__adding">正在加入购物车…</p>
      <p v-else-if="added" class="item__added">已加入购物车。</p>
      <p v-if="cart.error" class="item__error">{{ cart.error.title }}</p>
    </template>
  </section>
</template>
