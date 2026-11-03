<script setup lang="ts">
import { computed } from 'vue'

import type { components } from '@/shared/api/schema'

/**
 * T350: Vue组件 recommend。
 *
 * <p>推荐来自偏好分类，是系统给出的参考而不是手动收藏（PER-01～03）。商品名与描述只作为纯
 * 文本呈现，Vue 的插值不会执行脚本（D07、NFR-05）。</p>
 */

type Product = components['schemas']['Product']

const props = defineProps<{ products: Product[] }>()

const isEmpty = computed(() => props.products.length === 0)
</script>

<template>
  <section class="recommendations">
    <h2>偏好分类推荐</h2>
    <p v-if="isEmpty" class="recommendations__empty">暂无推荐商品。</p>
    <ul v-else>
      <li v-for="product in products" :key="product.productId">
        <span class="recommendations__name">{{ product.name }}</span>
        <span v-if="product.description" class="recommendations__description">{{ product.description }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.recommendations ul {
  padding-left: 1rem;
}
</style>
