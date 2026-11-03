import { flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { bootstrap } from './main'

/**
 * T398: Vue依赖注入与启动入口。
 *
 * <p>装配 Pinia、路由与 HTTP 客户端，启动时引导会话状态（已登录时刷新 CSRF），并给出全局异常
 * 反馈；不创建第二套业务服务（plan §8）。</p>
 */
vi.mock('@/features/identity/api', () => ({
  fetchSession: vi.fn(async () => ({ authenticated: true, username: 'alice' })),
  fetchCsrf: vi.fn(async () => ({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' })),
  login: vi.fn(async () => ({ authenticated: true, username: 'alice' })),
  logout: vi.fn(async () => undefined),
}))

describe('application bootstrap', () => {
  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>'
  })

  it('mounts the application', async () => {
    await bootstrap('#app')
    await flushPromises()

    expect(document.querySelector('#app')?.textContent).toContain('JPetStore')
  })

  it('reads the session and refreshes the CSRF token on start', async () => {
    const { fetchSession, fetchCsrf } = await import('@/features/identity/api')

    await bootstrap('#app')
    await flushPromises()

    expect(fetchSession).toHaveBeenCalled()
    expect(fetchCsrf).toHaveBeenCalled()
  })
})
