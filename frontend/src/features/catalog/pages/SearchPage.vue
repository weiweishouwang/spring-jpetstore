<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { searchProducts } from '@/features/catalog/api'
import type { Product } from '@/features/catalog/api'

/**
 * T360: 页面 SearchPage。
 *
 * <p>只按产品名称整体包含匹配、大小写不敏感；空词与纯空白都按非法处理，特殊字符由服务端按
 * 字面转义处理（Q14、plan §8）。</p>
 */

const props = defineProps<{ initialKeyword: string }>()

const keyword = ref(props.initialKeyword)
const products = ref<Product[]>([])
const loading = ref(true)
const invalid = ref(false)

onMounted(async () => {
  const trimmed = keyword.value.trim()
  if (trimmed.length === 0) {
    invalid.value = true
    loading.value = false
    return
  }
  try {
    products.value = await searchProducts(httpClient, trimmed)
  } catch {
    products.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="search">
    <h1>搜索商品</h1>
    <form @submit.prevent>
      <label>
        关键词
        <input v-model="keyword" type="search" name="keyword" />
      </label>
    </form>
    <p v-if="loading">加载中…</p>
    <p v-else-if="invalid">搜索关键词不能为空。</p>
    <p v-else-if="products.length === 0">没有找到匹配的商品。</p>
    <ul v-else>
      <li v-for="product in products" :key="product.productId">
        <RouterLink :to="`/product/${product.productId}`">{{ product.name }}</RouterLink>
      </li>
    </ul>
  </section>
</template>
