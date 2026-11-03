import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearCsrfToken, setCsrfToken } from './csrf'
import { HttpError, createHttpClient } from './http'

/**
 * T317: 统一同源 HTTP 客户端。
 *
 * <p>覆盖 Cookie、API-Version、写请求 CSRF、Problem Details 与 requestId；副作用请求绝不自动
 * 重试，401 与 409 不静默重放（plan §6、§8、AT-09）。</p>
 */
describe('http client', () => {
  beforeEach(() => {
    clearCsrfToken()
  })

  it('sends same origin cookies and the API version header', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ authenticated: false }))
    const client = createHttpClient({ fetchImpl })

    await client.request('/api/auth/session')

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/session')
    expect(init.credentials).toBe('same-origin')
    expect((init.headers as Record<string, string>)['API-Version']).toBe('1.0')
  })

  it('adds the CSRF header to write requests only', async () => {
    setCsrfToken('token-value', 'X-CSRF-TOKEN')
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({}, 200))
    const client = createHttpClient({ fetchImpl })

    await client.request('/api/cart/items', { method: 'POST', body: { itemId: 'EST-1' } })
    await client.request('/api/cart')

    const writeInit = fetchImpl.mock.calls[0][1] as RequestInit
    const readInit = fetchImpl.mock.calls[1][1] as RequestInit
    expect((writeInit.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('token-value')
    expect((readInit.headers as Record<string, string>)['X-CSRF-TOKEN']).toBeUndefined()
  })

  it('never retries a failed side effect', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ code: 'OUT_OF_STOCK' }, 409))
    const client = createHttpClient({ fetchImpl })

    await expect(client.request('/api/orders', { method: 'POST', body: {} })).rejects.toBeInstanceOf(HttpError)
    expect(fetchImpl).toHaveBeenCalledTimes(1)
  })

  it('reports the problem details code, the status and the request id', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) =>
      jsonResponse(
        {
          type: 'about:blank',
          title: 'Conflict',
          status: 409,
          code: 'CART_VERSION_CONFLICT',
          requestId: 'req-1',
          violations: [{ field: 'items', message: '数量不合法' }],
        },
        409,
      ),
    )
    const client = createHttpClient({ fetchImpl })

    const error = await client.request('/api/cart/items', { method: 'POST', body: {} }).catch((e) => e)

    expect(error).toBeInstanceOf(HttpError)
    const problem = error as HttpError
    expect(problem.status).toBe(409)
    expect(problem.code).toBe('CART_VERSION_CONFLICT')
    expect(problem.requestId).toBe('req-1')
    expect(problem.violations).toEqual([{ field: 'items', message: '数量不合法' }])
  })

  it('does not swallow an unauthorized response', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ code: 'AUTHENTICATION_REQUIRED' }, 401))
    const client = createHttpClient({ fetchImpl })

    const error = await client.request('/api/account/me').catch((e) => e)

    expect(error).toBeInstanceOf(HttpError)
    const problem = error as HttpError
    expect(problem.status).toBe(401)
    expect(problem.code).toBe('AUTHENTICATION_REQUIRED')
  })

  it('returns the parsed body and the response metadata on success', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ orderId: '1001' }, 201, { Location: '/api/orders/1001' }))
    const client = createHttpClient({ fetchImpl })

    const response = await client.requestWithResponse<{ orderId: string }>('/api/orders', {
      method: 'POST',
      body: {},
    })

    expect(response.status).toBe(201)
    expect(response.headers.get('Location')).toBe('/api/orders/1001')
    expect(response.body.orderId).toBe('1001')
  })

  it('uses the configured base url', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({}))
    const client = createHttpClient({ fetchImpl, baseUrl: '/backend' })

    await client.request('/api/auth/session')

    expect(fetchImpl.mock.calls[0][0]).toBe('/backend/api/auth/session')
  })
})

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}
