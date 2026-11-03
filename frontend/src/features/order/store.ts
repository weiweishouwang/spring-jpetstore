import { defineStore } from 'pinia'
import { ref } from 'vue'

import { httpClient } from '@/shared/api/http'
import type { HttpClient } from '@/shared/api/http'
import { parseProblem } from '@/shared/presentation/problem'
import type { ProblemView } from '@/shared/presentation/problem'

import { createCheckout as createCheckoutRequest, placeOrder as submitOrder } from './api'
import type { CheckoutResponse, CreateCheckoutRequest, OrderCreatedResponse } from './api'

/**
 * T340: order Vue状态仓库。
 *
 * <p>保存当前尝试的确认快照与幂等键：重复点击提交时复用同一个键，绝不重新生成——否则重试会变成
 * 第二张订单（plan §5.7、NFR-03、AT-R02）。提交成功才结束本次尝试；失败时保留键以便重试。</p>
 *
 * <p>离开页面时清除快照与键，不保留任何支付输入（NFR-04）。</p>
 */



export const useOrderStore = defineStore('order', () => {
  const checkout = ref<CheckoutResponse | null>(null)
  const idempotencyKey = ref<string | null>(null)
  const placing = ref(false)
  const error = ref<ProblemView | null>(null)
  const lastOrder = ref<OrderCreatedResponse | null>(null)

  /** 创建确认快照，并为本次尝试生成幂等键。 */
  async function createCheckout(
    request: CreateCheckoutRequest,
    client: HttpClient = httpClient,
  ): Promise<void> {
    placing.value = true
    try {
      checkout.value = await createCheckoutRequest(client, request)
      idempotencyKey.value = newIdempotencyKey()
      error.value = null
    } catch (failure) {
      error.value = parseProblem(failure)
    } finally {
      placing.value = false
    }
  }

  /** 提交订单：复用同一个幂等键，成功后结束本次尝试。 */
  async function placeOrder(client: HttpClient = httpClient): Promise<void> {
    if (!checkout.value || !idempotencyKey.value) {
      return
    }
    placing.value = true
    try {
      const result = await submitOrder(client, checkout.value.checkoutId, idempotencyKey.value)
      lastOrder.value = result.body
      checkout.value = null
      idempotencyKey.value = null
      error.value = null
    } catch (failure) {
      error.value = parseProblem(failure)
    } finally {
      placing.value = false
    }
  }

  /** 清除当前尝试：快照、幂等键与错误都清空。 */
  function clear(): void {
    checkout.value = null
    idempotencyKey.value = null
    error.value = null
  }

  return {
    checkout,
    idempotencyKey,
    placing,
    error,
    lastOrder,
    createCheckout,
    placeOrder,
    clear,
  }
})

/** 生成幂等键：随机 UUID 字符串，最长 128 字符（plan §5.7）。 */
function newIdempotencyKey(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  return `key-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
