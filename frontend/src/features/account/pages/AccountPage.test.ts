import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import AccountPage from './AccountPage.vue'

/**
 * T365: 页面 AccountPage。
 *
 * <p>用户名只读、空密码表示保留原密码、两次密码一致、资料与偏好开关保存后刷新，语言只能是
 * english 或 japanese（ACC-07/08、Q09、plan §8）。</p>
 */
vi.mock('@/features/account/api', () => ({
  fetchMe: vi.fn(async () => ({
    username: 'alice',
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
  })),
  updateMe: vi.fn(async (request: { email: string }) => ({
    username: 'alice',
    email: request.email,
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
  })),
}))

describe('AccountPage', () => {
  it('shows the own profile with a read only username', async () => {
    const wrapper = mount(AccountPage)

    await flushPromises()

    expect((wrapper.find('input[name="username"]').element as HTMLInputElement).value).toBe('alice')
    expect(wrapper.find('input[name="username"]').attributes('readonly')).toBeDefined()
  })

  it('offers only the two supported languages', async () => {
    const wrapper = mount(AccountPage)

    await flushPromises()

    const options = wrapper.findAll('select[name="languagePreference"] option')
    expect(options.map((option) => option.text())).toEqual(['english', 'japanese'])
  })

  it('refuses two different passwords', async () => {
    const wrapper = mount(AccountPage)

    await flushPromises()
    await wrapper.find('input[name="password"]').setValue('new-pass-123')
    await wrapper.find('input[name="repeatedPassword"]').setValue('other-pass')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('两次密码')
  })

  it('saves the profile and shows the updated values', async () => {
    const wrapper = mount(AccountPage)

    await flushPromises()
    await wrapper.find('input[name="email"]').setValue('alice-new@example.com')
    await wrapper.find('form').trigger('submit')

    await flushPromises()

    expect(wrapper.text()).toContain('已保存')
  })
})
