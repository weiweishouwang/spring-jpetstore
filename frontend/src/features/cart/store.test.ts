import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { HttpError } from '@/shared/api/http'
import type { HttpClient } from '@/shared/api/http'

import { useCartStore } from './store'

/**
 * T337: cart Vue状态仓库。
 *
 * <p>覆盖以服务端 DTO 为准、版本冲突提示刷新、失败不丢内容、合并超限展示 pendingMerges
 * （plan §8、Q03/Q04/Q08）。</p>
 */
describe('cart store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps the server response as the source of truth', async () => {
    const client = stubClient({ cart: cartResponse({ version: 3, lineCount: 1, totalQuantity: 2 }) })
    const store = useCartStore()

    await store.load(client)

    expect(store.cart?.version).toBe(3)
    expect(store.lineCount).toBe(1)
    expect(store.totalQuantity).toBe(2)
  })

  it('sends the current version with a write request', async () => {
    const client = stubClient({ cart: cartResponse({ version: 3 }) })
    const store = useCartStore()
    await store.load(client)

    await store.add('EST-1', client)

    const write = client.calls.find((call) => call.init?.body !== undefined)
    expect(write?.init?.body).toEqual({ itemId: 'EST-1', expectedVersion: 3 })
  })

  it('asks for a refresh on a version conflict and keeps the content', async () => {
    const client = stubClient({
      cart: cartResponse({ version: 3, lineCount: 1, totalQuantity: 2 }),
      failure: { status: 409, code: 'CART_VERSION_CONFLICT', title: 'Conflict' },
      failOnWrite: true,
    })
    const store = useCartStore()
    await store.load(client)

    await store.add('EST-1', client)

    expect(store.refreshRequired).toBe(true)
    expect(store.error?.code).toBe('CART_VERSION_CONFLICT')
    expect(store.cart?.lineCount).toBe(1)
  })

  it('exposes the pending merges of a merge conflict', async () => {
    const client = stubClient({
      cart: cartResponse({
        version: 2,
        mergeConflict: true,
        pendingMerges: [{ mergeId: '9', items: [] }],
      }),
    })
    const store = useCartStore()

    await store.merge(client)

    expect(store.mergeConflict).toBe(true)
    expect(store.pendingMerges[0]?.mergeId).toBe('9')
  })

  it('clears the error after a successful write', async () => {
    const client = stubClient({ cart: cartResponse({ version: 1 }) })
    const store = useCartStore()

    await store.load(client)
    await store.add('EST-1', client)

    expect(store.error).toBeNull()
    expect(store.refreshRequired).toBe(false)
  })
})

function cartResponse(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    version: 0,
    lineCount: 0,
    totalQuantity: 0,
    items: [],
    subtotal: '0.00',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
    ...overrides,
  }
}

interface StubOptions {
  cart?: Record<string, unknown>
  failure?: { status: number; code: string; title: string }
  /** 只在写请求上失败：读取仍然成功，冲突时才能保留已显示的内容。 */
  failOnWrite?: boolean
}

function stubClient(options: StubOptions): HttpClient & { calls: { path: string; init?: RequestInit }[] } {
  const calls: { path: string; init?: RequestInit }[] = []
  const request = vi.fn(async (path: string, init?: RequestInit) => {
    calls.push({ path, init })
    if (options.failure && (!options.failOnWrite || init?.body !== undefined)) {
      throw new HttpError({
        status: options.failure.status,
        code: options.failure.code,
        title: options.failure.title,
        violations: [],
      })
    }
    return options.cart ?? cartResponse()
  })
  return {
    calls,
    request: request as unknown as HttpClient['request'],
    requestWithResponse: request as unknown as HttpClient['requestWithResponse'],
  }
}
