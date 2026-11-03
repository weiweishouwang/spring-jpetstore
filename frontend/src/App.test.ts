import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App.vue'

/**
 * T379: 应用布局与导航。
 *
 * <p>登录与退出入口、公共商品导航、欢迎语与标题、错误反馈，且不执行任何未知脚本
 * （D07/D10、plan §8、NFR-05）。</p>
 */
vi.mock('@/features/identity/api', () => ({
  fetchSession: vi.fn(async () => ({ authenticated: false })),
  fetchCsrf: vi.fn(async () => ({ token: 'token-value', headerName: 'X-CSRF-TOKEN' })),
  logout: vi.fn(async () => undefined),
}))

describe('App', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows the site title', async () => {
    const wrapper = mount(App, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, RouterView: true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('JPetStore')
  })

  it('offers the public catalog navigation', async () => {
    const wrapper = mount(App, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, RouterView: true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('商品')
  })

  it('offers the login entry for an anonymous visitor', async () => {
    const wrapper = mount(App, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, RouterView: true } },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('登录')
  })

  it('never renders an unknown script', async () => {
    const wrapper = mount(App, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, RouterView: true } },
    })

    await flushPromises()

    expect(wrapper.html()).not.toContain('<script>alert')
  })
})
