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
 */

const props = defineProps<{ productId: string }>()

const cart = useCartStore()

const detail = ref<ProductDetail | null>(null)
const loading = ref(true)
const failed = ref(false)

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
  await cart.add(itemId)
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
          <button type="button" :disabled="!item.purchasable" @click="addToCart(item.itemId)">加入购物车</button>
        </li>
      </ul>
    </template>
  </section>
</template>
