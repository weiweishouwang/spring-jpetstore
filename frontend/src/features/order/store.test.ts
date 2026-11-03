import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { components } from '@/shared/api/schema'

import { HttpError } from '@/shared/api/http'
import type { HttpClient } from '@/shared/api/http'

import { useOrderStore } from './store'

/**
 * T339: order Vue状态仓库。
 *
 * <p>覆盖保存同一次确认快照与幂等键、重复点击不换键、成功才结束尝试，以及离开时清除敏感输入
 * （plan §8、Q03/Q04/Q08、NFR-03）。</p>
 */
describe('order store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('keeps the checkout snapshot of the current attempt', async () => {
    const client = stubClient({ checkout: { checkoutId: 'ck-1', cartVersion: 2 } })
    const store = useOrderStore()

    await store.createCheckout(checkoutRequest(), client)

    expect(store.checkout?.checkoutId).toBe('ck-1')
    expect(store.idempotencyKey).not.toBeNull()
  })

  it('reuses the same idempotency key when the submission is repeated', async () => {
    // 第一次提交失败（响应丢失或冲突），用户再次点击：必须复用同一个键。
    const client = stubClient({
      checkout: { checkoutId: 'ck-1', cartVersion: 2 },
      placement: { orderId: '1001' },
      failFirstOrder: true,
    })
    const store = useOrderStore()
    await store.createCheckout(checkoutRequest(), client)
    const key = store.idempotencyKey

    await store.placeOrder(client)
    await store.placeOrder(client)

    const keys = client.calls
      .filter((call) => call.path === '/api/orders')
      .map((call) => (call.init?.headers as Record<string, string>)['Idempotency-Key'])
    expect(keys.filter(Boolean)).toHaveLength(2)
    expect(new Set(keys).size).toBe(1)
    expect(keys[0]).toBe(key)
  })

  it('keeps the key when the submission fails so it can be retried', async () => {
    const client = stubClient({
      checkout: { checkoutId: 'ck-1', cartVersion: 2 },
      failure: { status: 409, code: 'CHECKOUT_STALE', title: 'Conflict' },
    })
    const store = useOrderStore()
    await store.createCheckout(checkoutRequest(), client)
    const key = store.idempotencyKey

    await store.placeOrder(client)

    expect(store.idempotencyKey).toBe(key)
    expect(store.error?.code).toBe('CHECKOUT_STALE')
  })

  it('ends the attempt after a successful order', async () => {
    const client = stubClient({
      checkout: { checkoutId: 'ck-1', cartVersion: 2 },
      placement: { orderId: '1001' },
    })
    const store = useOrderStore()
    await store.createCheckout(checkoutRequest(), client)

    await store.placeOrder(client)

    expect(store.checkout).toBeNull()
    expect(store.idempotencyKey).toBeNull()
  })

  it('clears the sensitive input when the page is left', async () => {
    const client = stubClient({ checkout: { checkoutId: 'ck-1', cartVersion: 2 } })
    const store = useOrderStore()
    await store.createCheckout(checkoutRequest(), client)

    store.clear()

    expect(store.checkout).toBeNull()
    expect(store.idempotencyKey).toBeNull()
    expect(store.error).toBeNull()
  })
})

function checkoutRequest(): components['schemas']['CreateCheckoutRequest'] {
  return {
    cartVersion: 2,
    billingAddress: address('Alice'),
    shippingAddress: address('Bob'),
    payment: { cardType: 'Visa', cardNumber: 'FAKE-4111-1111-1111-1111', expiryDate: '12/2026' },
  }
}

function address(firstName: string): components['schemas']['Address'] {
  return {
    firstName,
    lastName: 'Last',
    address1: '1-2-3 Street',
    address2: null,
    city: 'City',
    state: 'State',
    zip: '12345',
    country: 'JP',
  }
}

interface StubOptions {
  checkout?: Record<string, unknown>
  placement?: Record<string, unknown>
  failure?: { status: number; code: string; title: string }
  /** 只有第一次提交订单失败：模拟响应丢失后的重试。 */
  failFirstOrder?: boolean
}

function stubClient(options: StubOptions): HttpClient & { calls: { path: string; init?: RequestInit }[] } {
  const calls: { path: string; init?: RequestInit }[] = []
  let orderAttempts = 0
  const request = vi.fn(async (path: string, init?: RequestInit) => {
    calls.push({ path, init })
    if (options.failFirstOrder && path === '/api/orders') {
      orderAttempts += 1
      if (orderAttempts === 1) {
        throw new HttpError({
          status: 503,
          code: 'ORDER_RETRYABLE',
          title: '订单正在处理中',
          violations: [],
        })
      }
    }
    if (options.failure) {
      throw new HttpError({
        status: options.failure.status,
        code: options.failure.code,
        title: options.failure.title,
        violations: [],
      })
    }
    if (path === '/api/orders') {
      return {
        status: 201,
        headers: new Headers({ Location: '/api/orders/1001' }),
        body: options.placement ?? { orderId: '1001' },
      }
    }
    return options.checkout ?? {}
  })
  return {
    calls,
    request: request as unknown as HttpClient['request'],
    requestWithResponse: request as unknown as HttpClient['requestWithResponse'],
  }
}
