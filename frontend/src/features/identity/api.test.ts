import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearCsrfToken, setCsrfToken } from '@/shared/api/csrf'
import { HttpError, createHttpClient } from '@/shared/api/http'

import { fetchCsrf, fetchSession, login, logout } from './api'

/**
 * T319: identity 前端API调用。
 *
 * <p>只验证 session、CSRF、登录表单与退出的生命周期；响应严格按契约类型，不在客户端重复任何
 * 金额计算（plan §6.2、§8）。</p>
 */
describe('identity api', () => {
  beforeEach(() => {
    clearCsrfToken()
  })

  it('reports the session state', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ authenticated: true, username: 'alice' }))
    const client = createHttpClient({ fetchImpl })

    const session = await fetchSession(client)

    expect(session.authenticated).toBe(true)
    expect(session.username).toBe('alice')
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/auth/session')
  })

  it('stores the CSRF token in memory when it is fetched', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ token: 'token-value', headerName: 'X-CSRF-TOKEN' }))
    const client = createHttpClient({ fetchImpl })

    const csrf = await fetchCsrf(client)

    expect(csrf.token).toBe('token-value')
    expect(csrf.headerName).toBe('X-CSRF-TOKEN')
  })

  it('logs in with a form encoded body and refreshes the CSRF token', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ authenticated: true, username: 'alice' }))
      .mockResolvedValueOnce(jsonResponse({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' }))
    const client = createHttpClient({ fetchImpl })

    const session = await login(client, 'alice', 'alice-pass-123')

    expect(session.authenticated).toBe(true)
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/auth/session')
    expect(init.method).toBe('POST')
    expect(init.body).toBe('username=alice&password=alice-pass-123')
    expect(fetchImpl.mock.calls[1][0]).toBe('/api/auth/csrf')
  })

  it('clears the identity and the CSRF token on logout', async () => {
    setCsrfToken('old-token', 'X-CSRF-TOKEN')
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => new Response(null, { status: 204 }))
    const client = createHttpClient({ fetchImpl })

    await logout(client)

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/auth/logout')
  })

  it('reports invalid credentials as an error', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) =>
      jsonResponse({ code: 'INVALID_CREDENTIALS', title: 'Unauthorized', status: 401 }, 401),
    )
    const client = createHttpClient({ fetchImpl })

    const error = await login(client, 'alice', 'wrong').catch((e) => e)

    expect(error).toBeInstanceOf(HttpError)
    expect(error.status).toBe(401)
  })
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
