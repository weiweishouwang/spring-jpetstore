<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchProduct } from '@/features/catalog/api'
import type { ProductDetail } from '@/features/catalog/api'
import { formatAmount } from '@/shared/presentation/amount'

/**
 * T356: 页面 ProductPage。
 *
 * <p>列出可售项与规格，每行一个 SKU；描述只作为纯文本展示（D07、NFR-05）。加购入口交给购物车
 * 仓库处理，页面本身不直接写购物车。</p>
 */

const props = defineProps<{ productId: string }>()

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
          <button type="button" :disabled="!item.purchasable">加入购物车</button>
        </li>
      </ul>
    </template>
  </section>
</template>
