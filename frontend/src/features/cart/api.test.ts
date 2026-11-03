import { describe, expect, it, vi } from 'vitest'

import { clearCsrfToken, setCsrfToken } from '@/shared/api/csrf'
import { createHttpClient } from '@/shared/api/http'

import { addItem, applyQuantities, fetchCart, mergeCart, removeItem, setQuantity } from './api'

/**
 * T325: cart 前端API调用。
 *
 * <p>验证 GET 不产生写操作、加购是增量语义、批量与单项数量都携带版本、删除带版本，以及登录合并
 * 冲突时的展示数据与显式重试（plan §6.2、§8、Q08）。</p>
 */
describe('cart api', () => {
  it('reads the cart without a write request', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse(cartResponse()))
    const client = createHttpClient({ fetchImpl })

    const cart = await fetchCart(client)

    expect(cart.version).toBe(2)
    const [, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(init.method ?? 'GET').toBe('GET')
  })

  it('adds an item with the expected version', async () => {
    clearCsrfToken()
    setCsrfToken('token-value', 'X-CSRF-TOKEN')
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse(cartResponse()))
    const client = createHttpClient({ fetchImpl })

    await addItem(client, 'EST-1', 2)

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/cart/items')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ itemId: 'EST-1', expectedVersion: 2 })
    expect((init.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('token-value')
  })

  it('sets a single quantity and applies a batch', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse(cartResponse()))
    const client = createHttpClient({ fetchImpl })

    await setQuantity(client, 'EST-1', 3, 2)
    await applyQuantities(
      client,
      [
        { itemId: 'EST-1', quantity: 3 },
        { itemId: 'EST-2', quantity: 1 },
      ],
      2,
    )

    const first = fetchImpl.mock.calls[0] as [string, RequestInit]
    const second = fetchImpl.mock.calls[1] as [string, RequestInit]
    expect(first[0]).toBe('/api/cart/items/EST-1')
    expect(first[1].method).toBe('PUT')
    expect(JSON.parse(String(first[1].body))).toEqual({ quantity: 3, expectedVersion: 2 })
    expect(second[0]).toBe('/api/cart/items')
    expect(JSON.parse(String(second[1].body)).items).toHaveLength(2)
  })

  it('removes an item with the expected version in the query', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse(cartResponse()))
    const client = createHttpClient({ fetchImpl })

    await removeItem(client, 'EST-1', 2)

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/cart/items/EST-1?expectedVersion=2')
    expect(init.method).toBe('DELETE')
  })

  it('exposes the pending merges of a merge conflict', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ ...cartResponse(), mergeConflict: true, pendingMerges: [{ mergeId: '9', items: [] }] }))
    const client = createHttpClient({ fetchImpl })

    const cart = await mergeCart(client, 2)

    expect(cart.mergeConflict).toBe(true)
    expect(cart.pendingMerges[0]?.mergeId).toBe('9')
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/cart/merge')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ expectedVersion: 2 })
  })
})

function cartResponse(): Record<string, unknown> {
  return {
    version: 2,
    lineCount: 1,
    totalQuantity: 3,
    items: [
      {
        item: {
          itemId: 'EST-1',
          productId: 'FI-SW-01',
          productName: 'Angelfish',
          attributes: ['Large'],
          unitPrice: '16.50',
          inStock: true,
          availableQuantity: 10,
          purchasable: true,
        },
        quantity: 3,
        lineAmount: '49.50',
      },
    ],
    subtotal: '49.50',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
