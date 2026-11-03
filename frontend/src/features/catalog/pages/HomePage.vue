<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchCategories } from '@/features/catalog/api'
import type { Category } from '@/features/catalog/api'

/**
 * T352: 页面 HomePage。
 *
 * <p>五个分类入口；读取失败或没有内容时给出明确状态，不显示伪造数据（plan §8、spec §4）。</p>
 */

const categories = ref<Category[]>([])
const loading = ref(true)
const failed = ref(false)

onMounted(async () => {
  try {
    categories.value = await fetchCategories(httpClient)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="home">
    <h1>JPetStore</h1>
    <p v-if="loading">加载中…</p>
    <p v-else-if="failed">分类加载失败，请稍后重试。</p>
    <p v-else-if="categories.length === 0">暂无分类。</p>
    <ul v-else>
      <li v-for="category in categories" :key="category.categoryId">
        <RouterLink :to="`/category/${category.categoryId}`">{{ category.name }}</RouterLink>
      </li>
    </ul>
  </section>
</template>
