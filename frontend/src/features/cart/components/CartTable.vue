<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { components } from '@/shared/api/schema'
import { formatAmount } from '@/shared/presentation/amount'

/**
 * T348: Vue组件 carttable。
 *
 * <p>显示服务端返回的购物车：行数与商品数量总和分别展示，小计来自服务端（plan §5.4、
 * CRT-09）。数量改为 0 或负数时按已确认决策发出移除请求，组件自己不修改数据（D04a）。</p>
 */

type CartResponse = components['schemas']['CartResponse']

const props = withDefaults(defineProps<{ cart: CartResponse; busy?: boolean }>(), { busy: false })

const emit = defineEmits<{
  'set-quantity': [{ itemId: string; quantity: number }]
  remove: [string]
}>()

const quantities = ref<Record<string, number>>({})

watch(
  () => props.cart.items,
  (items) => {
    const next: Record<string, number> = {}
    for (const line of items) {
      next[line.item.itemId] = line.quantity
    }
    quantities.value = next
  },
  { immediate: true, deep: true },
)

const isEmpty = computed(() => props.cart.items.length === 0)

function commit(itemId: string): void {
  const quantity = Number(quantities.value[itemId] ?? 0)
  if (!Number.isFinite(quantity)) {
    return
  }
  if (quantity < 1) {
    emit('remove', itemId)
    return
  }
  emit('set-quantity', { itemId, quantity })
}
</script>

<template>
  <section class="cart-table">
    <p v-if="busy" class="cart-table__busy">加载中…</p>
    <p v-if="isEmpty && !busy" class="cart-table__empty">购物车是空的。</p>
    <table v-else>
      <caption>购物车（{{ cart.lineCount }} 行，共 {{ cart.totalQuantity }} 件）</caption>
      <thead>
        <tr>
          <th scope="col">商品</th>
          <th scope="col">单价</th>
          <th scope="col">数量</th>
          <th scope="col">小计</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in cart.items" :key="line.item.itemId">
          <td>{{ line.item.productName }}</td>
          <td>{{ formatAmount(line.item.unitPrice) }}</td>
          <td>
            <input
              :name="`quantity-${line.item.itemId}`"
              type="number"
              min="0"
              :value="quantities[line.item.itemId]"
              @input="quantities[line.item.itemId] = Number(($event.target as HTMLInputElement).value)"
              @change="commit(line.item.itemId)"
            />
          </td>
          <td>{{ formatAmount(line.lineAmount) }}</td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <th scope="row" colspan="3">合计</th>
          <td>{{ formatAmount(cart.subtotal) }}</td>
        </tr>
      </tfoot>
    </table>
  </section>
</template>

<style scoped>
.cart-table table {
  width: 100%;
  border-collapse: collapse;
}
</style>
