<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { fetchRecommendations } from '@/features/account/api'
import { httpClient } from '@/shared/api/http'
import type { Product } from '@/features/account/api'
import { useCartStore } from '@/features/cart/store'
import Recommendations from '@/features/account/components/Recommendations.vue'
import CartTable from '@/features/cart/components/CartTable.vue'

/**
 * T368: 页面 CartPage。
 *
 * <p>游客与登录用户看到的是各自的购物车；空车不显示结算入口；版本冲突提示刷新而不是静默覆盖
 * （plan §5.4、§8、Q08）。</p>
 */

const cart = useCartStore()
const recommendations = ref<Product[]>([])

onMounted(async () => {
  await cart.load()
  try {
    recommendations.value = await fetchRecommendations(httpClient)
  } catch {
    recommendations.value = []
  }
})

async function onSetQuantity(payload: { itemId: string; quantity: number }): Promise<void> {
  await cart.setItemQuantity(payload.itemId, payload.quantity)
}

async function onRemove(itemId: string): Promise<void> {
  await cart.remove(itemId)
}
</script>

<template>
  <section class="cart">
    <h1>购物车</h1>
    <p v-if="cart.error" class="cart__error">{{ cart.error.title }}</p>
    <p v-if="cart.refreshRequired" class="cart__refresh">购物车已被其他操作修改，请刷新后重试。</p>
    <CartTable v-if="cart.cart" :cart="cart.cart" :busy="cart.loading" @set-quantity="onSetQuantity"
               @remove="onRemove" />
    <p v-if="cart.cart && cart.cart.items.length === 0" class="cart__empty">购物车是空的，暂无商品可结算。</p>
    <RouterLink v-if="cart.cart && cart.cart.items.length > 0" class="cart__checkout" to="/checkout">
      去结算
    </RouterLink>
    <Recommendations :products="recommendations" />
  </section>
</template>
