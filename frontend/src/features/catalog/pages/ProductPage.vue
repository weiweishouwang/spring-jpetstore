<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'
import { useCartStore } from '@/features/cart/store'

import { fetchProduct } from '@/features/catalog/api'
import type { ProductDetail } from '@/features/catalog/api'
import { formatAmount } from '@/shared/presentation/amount'

/**
 * T356: 页面 ProductPage。
 *
 * <p>列出可售项与规格，每行一个 SKU；描述只作为纯文本展示（D07、NFR-05）。加购走购物车仓库：
 * 每次加一、带当前版本，冲突由仓库提示刷新（CRT-02、CRT-10）。</p>
 *
 * <p>加购的结果必须可见：写请求有先后（进行中／成功／失败），页面把当前状态讲清楚，用户
 * 才能知道商品有没有真的进车（D10）。</p>
 */

const props = defineProps<{ productId: string }>()

const cart = useCartStore()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
const failed = ref(false)
/** 正在加购的可售项；同时只允许一个写请求。 */
const pendingItemId = ref<string | null>(null)
/** 已经加购成功的可售项。 */
const addedItemId = ref<string | null>(null)

onMounted(async () => {
  try {
    detail.value = await fetchProduct(httpClient, props.productId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function addToCart(itemId: string): Promise<void> {
  if (pendingItemId.value !== null) {
    return
  }
  pendingItemId.value = itemId
  addedItemId.value = null
  try {
    await cart.add(itemId)
    addedItemId.value = cart.error === null ? itemId : null
  } finally {
    pendingItemId.value = null
  }
}
</script>

<template>
  <section class="product">
    <p v-if="loading">加载中…</p>
    <p v-else-if="failed">产品不存在或加载失败。</p>
    <template v-else-if="detail">
      <h1>{{ detail.product.name }}</h1>
      <p v-if="detail.product.description" class="product__description">{{ detail.product.description }}</p>
      <ul>
        <li v-for="item in detail.items" :key="item.itemId">
          <span class="product__name">{{ item.productName }}</span>
          <span v-for="attribute in item.attributes" :key="attribute" class="product__attribute">
            {{ attribute }}
          </span>
          <span class="product__price">{{ formatAmount(item.unitPrice) }}</span>
          <button type="button" :disabled="!item.purchasable || pendingItemId !== null"
                  @click="addToCart(item.itemId)">
            加入购物车
          </button>
        </li>
      </ul>
      <p v-if="pendingItemId" class="product__adding">正在加入购物车…</p>
      <p v-else-if="addedItemId" class="product__added">已加入购物车。</p>
      <p v-if="cart.error" class="product__error">{{ cart.error.title }}</p>
    </template>
  </section>
</template>
