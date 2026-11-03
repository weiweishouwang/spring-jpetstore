import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearCsrfToken, getCsrfToken } from '@/shared/api/csrf'
import type { HttpClient } from '@/shared/api/http'

import { useIdentityStore } from './store'

/**
 * T335: identity Vue状态仓库。
 *
 * <p>覆盖会话引导、登录后刷新 CSRF、退出清身份、只保存站内返回目标，以及绝不保存口令
 * （plan §8、Q03/Q04/Q08、NFR-04）。</p>
 */
describe('identity store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    clearCsrfToken()
  })

  it('reads the session on bootstrap', async () => {
    const client = stubClient({ session: { authenticated: true, username: 'alice' } })
    const store = useIdentityStore()

    await store.bootstrap(client)

    expect(store.authenticated).toBe(true)
    expect(store.username).toBe('alice')
  })

  it('refreshes the CSRF token after a successful login', async () => {
    const client = stubClient({
      session: { authenticated: true, username: 'alice' },
      csrf: { token: 'fresh-token', headerName: 'X-CSRF-TOKEN' },
    })
    const store = useIdentityStore()

    await store.signIn('alice', 'alice-pass-123', client)

    expect(store.authenticated).toBe(true)
    expect(getCsrfToken()?.token).toBe('fresh-token')
  })

  it('clears the identity on sign out', async () => {
    const client = stubClient({
      session: { authenticated: true, username: 'alice' },
      csrf: { token: 'fresh-token', headerName: 'X-CSRF-TOKEN' },
    })
    const store = useIdentityStore()
    await store.signIn('alice', 'alice-pass-123', client)

    await store.signOut(client)

    expect(store.authenticated).toBe(false)
    expect(store.username).toBeNull()
    expect(getCsrfToken()).toBeNull()
  })

  it('never keeps the password', async () => {
    const client = stubClient({
      session: { authenticated: true, username: 'alice' },
      csrf: { token: 'fresh-token', headerName: 'X-CSRF-TOKEN' },
    })
    const store = useIdentityStore()

    await store.signIn('alice', 'alice-pass-123', client)

    expect(JSON.stringify(store.$state)).not.toContain('alice-pass-123')
  })

  it('keeps only internal return targets', () => {
    const store = useIdentityStore()

    store.rememberReturnTo('/cart')
    expect(store.returnTo).toBe('/cart')

    store.rememberReturnTo('https://evil.example.com/steal')
    expect(store.returnTo).toBeNull()

    store.rememberReturnTo('//evil.example.com')
    expect(store.returnTo).toBeNull()
  })

  it('consumes the return target once', () => {
    const store = useIdentityStore()
    store.rememberReturnTo('/cart')

    expect(store.consumeReturnTo()).toBe('/cart')
    expect(store.consumeReturnTo()).toBeNull()
  })
})

interface StubResponses {
  session?: { authenticated: boolean; username?: string }
  csrf?: { token: string; headerName: string }
}

function stubClient(responses: StubResponses): HttpClient {
  const request = vi.fn(async (path: string) => {
    if (path === '/api/auth/session') {
      return responses.session ?? { authenticated: false }
    }
    if (path === '/api/auth/csrf') {
      return responses.csrf ?? { token: 'token-value', headerName: 'X-CSRF-TOKEN' }
    }
    return undefined
  })
  return {
    request: request as unknown as HttpClient['request'],
    requestWithResponse: request as unknown as HttpClient['requestWithResponse'],
  }
}
