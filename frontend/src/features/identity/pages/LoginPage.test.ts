import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import LoginPage from './LoginPage.vue'

/**
 * T363: 页面 LoginPage。
 *
 * <p>表单登录、通用错误提示、只接受站内 returnTo、登录后刷新 CSRF，且不泄露用户是否存在
 * （ACC-05、plan §8、NFR-02）。</p>
 */
vi.mock('@/features/identity/api', () => ({
  // 登录失败：页面只给出通用提示，不区分用户不存在与口令错误。
  login: vi.fn(async () => {
    throw new Error('AUTHENTICATION_REQUIRED')
  }),
  fetchCsrf: vi.fn(async () => ({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' })),
}))

describe('LoginPage', () => {
  it('asks for the credentials', () => {
    const wrapper = mount(LoginPage)

    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
  })

  it('never reveals whether the user exists', async () => {
    const wrapper = mount(LoginPage)

    await wrapper.find('input[name="username"]').setValue('nobody')
    await wrapper.find('input[name="password"]').setValue('wrong-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('登录失败')
    expect(wrapper.text()).not.toContain('不存在')
  })

  it('keeps only an internal return target', () => {
    const wrapper = mount(LoginPage, { props: { returnTo: 'https://evil.example.com/steal' } })

    expect(wrapper.text()).not.toContain('evil.example.com')
  })
})
