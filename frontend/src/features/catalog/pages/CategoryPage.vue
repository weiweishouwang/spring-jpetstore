<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchProductsByCategory } from '@/features/catalog/api'
import type { Product } from '@/features/catalog/api'

/**
 * T354: 页面 CategoryPage。
 *
 * <p>分类不存在时显示明确状态，而不是伪造一个空分类页（plan §8、spec §4）。</p>
 */

const props = defineProps<{ categoryId: string }>()

const products = ref<Product[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    products.value = await fetchProductsByCategory(httpClient, props.categoryId)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="category">
    <h1>分类 {{ categoryId }}</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="failed">该分类不存在或加载失败。</p>
    <p v-else-if="products.length === 0">该分类下暂无商品。</p>
    <ul v-else>
      <li v-for="product in products" :key="product.productId">
        <RouterLink :to="`/product/${product.productId}`">{{ product.name }}</RouterLink>
      </li>
    </ul>
  </section>
</template>
