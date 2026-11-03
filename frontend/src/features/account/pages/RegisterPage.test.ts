import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import RegisterPage from './RegisterPage.vue'

/**
 * T361: 页面 RegisterPage。
 *
 * <p>注册表单的全部字段、两次密码一致、用户名冲突 409、成功后引导登录且不自动登录
 * （ACC-03/04、plan §8）。</p>
 */
vi.mock('@/features/account/api', () => ({
  registerAccount: vi.fn(async (_client: unknown, request: { username: string }) => {
    if (request.username === 'taken') {
      throw new Error('USERNAME_CONFLICT')
    }
    return { username: request.username }
  }),
}))

describe('RegisterPage', () => {
  it('asks for the registration fields', () => {
    const wrapper = mount(RegisterPage)

    expect(wrapper.find('input[name="username"]').exists()).toBe(true)
    expect(wrapper.find('input[name="password"]').exists()).toBe(true)
    expect(wrapper.find('input[name="repeatedPassword"]').exists()).toBe(true)
    expect(wrapper.find('input[name="email"]').exists()).toBe(true)
  })

  it('refuses two different passwords', async () => {
    const wrapper = mount(RegisterPage)

    await wrapper.find('input[name="password"]').setValue('secret-pass')
    await wrapper.find('input[name="repeatedPassword"]').setValue('other-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('两次密码')
  })

  it('shows the conflict when the username is taken', async () => {
    const wrapper = mount(RegisterPage)

    await wrapper.find('input[name="username"]').setValue('taken')
    await wrapper.find('input[name="password"]').setValue('secret-pass')
    await wrapper.find('input[name="repeatedPassword"]').setValue('secret-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('已被使用')
  })

  it('does not log the user in after a successful registration', async () => {
    const wrapper = mount(RegisterPage)

    await wrapper.find('input[name="username"]').setValue('alice')
    await wrapper.find('input[name="password"]').setValue('secret-pass')
    await wrapper.find('input[name="repeatedPassword"]').setValue('secret-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('注册成功')
    expect(wrapper.text()).toContain('登录')
  })
})
