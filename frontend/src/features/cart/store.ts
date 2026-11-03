import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { httpClient } from '@/shared/api/http'
import type { HttpClient } from '@/shared/api/http'
import { parseProblem } from '@/shared/presentation/problem'
import type { ProblemView } from '@/shared/presentation/problem'

import { addItem, applyQuantities, fetchCart, mergeCart, removeItem, setQuantity } from './api'
import type { CartQuantityEntry, CartResponse } from './api'

/**
 * T338: cart Vue状态仓库。
 *
 * <p>购物车内容以服务端 DTO 为准：前端不自行计算金额，也不本地推算数量（plan §5.4、§8）。
 * 所有写操作都带上当前版本；版本冲突（409）提示刷新而不是静默覆盖，失败时保留已显示的内容
 * （plan §5.4、CRT-10）。</p>
 *
 * <p>登录合并超限时展示 pendingMerges，用户调整账号购物车后可以显式重试，双方内容都不丢弃
 * （Q08）。</p>
 */



export const useCartStore = defineStore('cart', () => {
  const cart = ref<CartResponse | null>(null)
  const loading = ref(false)
  const error = ref<ProblemView | null>(null)

  const lineCount = computed(() => cart.value?.lineCount ?? 0)
  const totalQuantity = computed(() => cart.value?.totalQuantity ?? 0)
  const mergeConflict = computed(() => cart.value?.mergeConflict ?? false)
  const pendingMerges = computed(() => cart.value?.pendingMerges ?? [])
  /** 版本冲突时提示用户刷新。 */
  const refreshRequired = computed(() => error.value?.refreshRequired === true)

  async function load(client: HttpClient = httpClient): Promise<void> {
    await run(async () => {
      cart.value = await fetchCart(client)
    })
  }

  async function add(itemId: string, client: HttpClient = httpClient): Promise<void> {
    await run(async () => {
      cart.value = await addItem(client, itemId, version())
    })
  }

  async function setItemQuantity(
    itemId: string,
    quantity: number,
    client: HttpClient = httpClient,
  ): Promise<void> {
    await run(async () => {
      cart.value = await setQuantity(client, itemId, quantity, version())
    })
  }

  async function setQuantities(
    items: CartQuantityEntry[],
    client: HttpClient = httpClient,
  ): Promise<void> {
    await run(async () => {
      cart.value = await applyQuantities(client, items, version())
    })
  }

  async function remove(itemId: string, client: HttpClient = httpClient): Promise<void> {
    await run(async () => {
      cart.value = await removeItem(client, itemId, version())
    })
  }

  async function merge(client: HttpClient = httpClient): Promise<void> {
    await run(async () => {
      cart.value = await mergeCart(client, version())
    })
  }

  /** 清空本地状态：退出或页面离开时使用。 */
  function clear(): void {
    cart.value = null
    error.value = null
  }

  /** 执行一次购物车操作：失败时保留原内容，只记录错误。 */
  async function run(action: () => Promise<void>): Promise<void> {
    loading.value = true
    try {
      await action()
      error.value = null
    } catch (failure) {
      error.value = parseProblem((failure as { problem?: unknown })?.problem ?? failure)
    } finally {
      loading.value = false
    }
  }

  function version(): number {
    return cart.value?.version ?? 0
  }

  return {
    cart,
    loading,
    error,
    lineCount,
    totalQuantity,
    mergeConflict,
    pendingMerges,
    refreshRequired,
    load,
    add,
    setItemQuantity,
    setQuantities,
    remove,
    merge,
    clear,
  }
})
