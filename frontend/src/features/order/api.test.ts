import { describe, expect, it, vi } from 'vitest'

import type { components } from '@/shared/api/schema'

import { clearCsrfToken, setCsrfToken } from '@/shared/api/csrf'
import { HttpError, createHttpClient } from '@/shared/api/http'

import { createCheckout, fetchOrder, listOrders, placeOrder } from './api'

/**
 * T327: order 前端API调用。
 *
 * <p>验证确认快照创建、订单提交使用同一个幂等键重试、Location 响应头、本人列表与详情 404
 * （plan §6.2、§8、AT-R02）。</p>
 */
describe('order api', () => {
  it('creates a checkout snapshot from the cart version', async () => {
    clearCsrfToken()
    setCsrfToken('token-value', 'X-CSRF-TOKEN')
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ checkoutId: 'ck-1', cartVersion: 2 }, 201))
    const client = createHttpClient({ fetchImpl })

    const checkout = await createCheckout(client, {
      cartVersion: 2,
      billingAddress: address('Alice'),
      shippingAddress: address('Bob'),
      payment: { cardType: 'Visa', cardNumber: 'FAKE-4111-1111-1111-1111', expiryDate: '12/2026' },
    })

    expect(checkout.checkoutId).toBe('ck-1')
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/checkouts')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body)).cartVersion).toBe(2)
  })

  it('submits the order with the idempotency key and reports the location', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) =>
      jsonResponse({ orderId: '1001' }, 201, { Location: '/api/orders/1001' }),
    )
    const client = createHttpClient({ fetchImpl })

    const result = await placeOrder(client, 'ck-1', 'key-1')

    expect(result.body.orderId).toBe('1001')
    expect(result.location).toBe('/api/orders/1001')
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/orders')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toBe('key-1')
  })

  it('retries the submission with the same key', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ orderId: '1001' }, 201, { Location: '/api/orders/1001' }))
      .mockResolvedValueOnce(
        jsonResponse({ orderId: '1001' }, 201, { Location: '/api/orders/1001', 'Idempotency-Replayed': 'true' }),
      )
    const client = createHttpClient({ fetchImpl })

    await placeOrder(client, 'ck-1', 'key-1')
    const replay = await placeOrder(client, 'ck-1', 'key-1')

    expect(replay.replayed).toBe(true)
    expect(replay.body.orderId).toBe('1001')
    const second = fetchImpl.mock.calls[1] as [string, RequestInit]
    expect((second[1].headers as Record<string, string>)['Idempotency-Key']).toBe('key-1')
  })

  it('lists the own orders', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse([{ orderId: '1001' }]))
    const client = createHttpClient({ fetchImpl })

    const orders = await listOrders(client)

    expect(orders[0]?.orderId).toBe('1001')
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/orders')
  })

  it('reports an unknown order as not found', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) =>
      jsonResponse({ code: 'ORDER_NOT_FOUND', title: 'Not Found', status: 404 }, 404),
    )
    const client = createHttpClient({ fetchImpl })

    const error = await fetchOrder(client, '999').catch((e) => e)

    expect(error).toBeInstanceOf(HttpError)
    expect(error.status).toBe(404)
    expect(error.code).toBe('ORDER_NOT_FOUND')
  })
})

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

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}
