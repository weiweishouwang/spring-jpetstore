import { describe, expect, it, vi } from 'vitest'

import { createHttpClient } from '@/shared/api/http'

import { fetchBanner, fetchMe, fetchRecommendations, registerAccount, updateMe } from './api'

/**
 * T321: account 前端API调用。
 *
 * <p>只验证注册、me、PATCH、推荐与横幅的调用方式；凭据只在请求体里出现一次，不落任何存储
 * （plan §6.2、§8、NFR-04）。</p>
 */
describe('account api', () => {
  it('registers an account with the profile in the request body', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ username: 'alice' }, 201))
    const client = createHttpClient({ fetchImpl })

    const response = await registerAccount(client, {
      username: 'alice',
      password: 'alice-pass-123',
      repeatedPassword: 'alice-pass-123',
      email: 'alice@example.com',
      phone: '090-1111-2222',
      address: {
        firstName: 'Alice',
        lastName: 'Ishikawa',
        address1: '1-2-3 Sakuragaoka',
        address2: null,
        city: 'Shibuya',
        state: 'Tokyo',
        zip: '150-0001',
        country: 'JP',
      },
      preferences: {
        languagePreference: 'english',
        favouriteCategoryId: 'FISH',
        listOption: true,
        bannerOption: true,
      },
    })

    expect(response.username).toBe('alice')
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/accounts')
    expect(init.method).toBe('POST')
    expect(String(init.body)).toContain('alice')
    expect(String(init.body)).toContain('FISH')
  })

  it('reads the own profile', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ username: 'alice' }))
    const client = createHttpClient({ fetchImpl })

    const profile = await fetchMe(client)

    expect(profile.username).toBe('alice')
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/account/me')
  })

  it('updates the own profile with PATCH and without a username field', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ username: 'alice' }))
    const client = createHttpClient({ fetchImpl })

    await updateMe(client, {
      email: 'alice@example.com',
      phone: '090-1111-2222',
      address: {
        firstName: 'Alice',
        lastName: 'Ishikawa',
        address1: '1-2-3 Sakuragaoka',
        address2: null,
        city: 'Shibuya',
        state: 'Tokyo',
        zip: '150-0001',
        country: 'JP',
      },
      preferences: {
        languagePreference: 'english',
        favouriteCategoryId: 'FISH',
        listOption: true,
        bannerOption: true,
      },
      password: '',
      repeatedPassword: '',
    })

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/account/me')
    expect(init.method).toBe('PATCH')
    expect(String(init.body)).not.toContain('username')
  })

  it('reads the recommendations and the banner', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse([{ productId: 'FI-SW-01' }]))
      .mockResolvedValueOnce(jsonResponse({ imagePath: '/banner.gif', description: 'Fish' }))
    const client = createHttpClient({ fetchImpl })

    const recommendations = await fetchRecommendations(client)
    const banner = await fetchBanner(client)

    expect(recommendations).toHaveLength(1)
    expect(banner?.imagePath).toBe('/banner.gif')
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/account/me/recommendations')
    expect(fetchImpl.mock.calls[1][0]).toBe('/api/account/me/banner')
  })

  it('reports a missing banner as null', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => new Response('null', { status: 200 }))
    const client = createHttpClient({ fetchImpl })

    expect(await fetchBanner(client)).toBeNull()
  })
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
