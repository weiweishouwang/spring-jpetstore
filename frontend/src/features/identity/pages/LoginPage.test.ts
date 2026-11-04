import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import LoginPage from './LoginPage.vue'

/**
 * T363: 页面 LoginPage。
 *
 * <p>表单登录走 identity 状态仓库：成功后导航并刷新 CSRF；失败只给通用提示，不区分用户不存在
 * 与口令错误（ACC-05、plan §8、NFR-02）。</p>
 */
vi.mock('@/features/identity/api', () => ({
  // 登录失败：页面只给出通用提示，不区分用户不存在与口令错误。
  login: vi.fn(async () => {
    throw new Error('AUTHENTICATION_REQUIRED')
  }),
  fetchCsrf: vi.fn(async () => ({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' })),
  fetchSession: vi.fn(async () => ({ authenticated: false })),
  logout: vi.fn(async () => undefined),
}))

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/login', component: LoginPage },
    ],
  })
}

describe('LoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('asks for the credentials', () => {
    const wrapper = mount(LoginPage, { global: { plugins: [testRouter()] } })

    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
  })

  it('never reveals whether the user exists', async () => {
    const wrapper = mount(LoginPage, { global: { plugins: [testRouter()] } })

    await wrapper.find('input[name="username"]').setValue('nobody')
    await wrapper.find('input[name="password"]').setValue('wrong-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('登录失败')
    expect(wrapper.text()).not.toContain('不存在')
  })

  it('keeps only an internal return target', () => {
    const wrapper = mount(LoginPage, {
      props: { returnTo: 'https://evil.example.com/steal' },
      global: { plugins: [testRouter()] },
    })

    expect(wrapper.text()).not.toContain('evil.example.com')
  })
})
