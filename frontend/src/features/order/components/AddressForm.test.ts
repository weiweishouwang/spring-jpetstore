import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { components } from '@/shared/api/schema'

import AddressForm from './AddressForm.vue'

/**
 * T343: Vue组件 address。
 *
 * <p>账单与收货是两套独立模型：改账单不会覆盖收货（plan §5.5）。姓名与地址必填，第二行选填
 * （Q01、Q15）。</p>
 */
describe('AddressForm', () => {
  const billing: components['schemas']['Address'] = {
    firstName: 'Alice',
    lastName: 'Ishikawa',
    address1: '1-2-3 Sakuragaoka',
    address2: null,
    city: 'Shibuya',
    state: 'Tokyo',
    zip: '150-0001',
    country: 'JP',
  }

  it('shows the address of the model', () => {
    const wrapper = mount(AddressForm, { props: { modelValue: billing, label: '账单地址' } })

    expect(wrapper.text()).toContain('账单地址')
    expect((wrapper.find('input[name="firstName"]').element as HTMLInputElement).value).toBe('Alice')
    expect((wrapper.find('input[name="address1"]').element as HTMLInputElement).value).toBe('1-2-3 Sakuragaoka')
  })

  it('emits the updated model when a field changes', async () => {
    const wrapper = mount(AddressForm, { props: { modelValue: billing, label: '账单地址' } })

    await wrapper.find('input[name="city"]').setValue('Minato')

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const value = (emitted?.[0]?.[0] ?? {}) as components['schemas']['Address']
    expect(value.city).toBe('Minato')
    expect(value.firstName).toBe('Alice')
  })

  it('treats the second address line as optional', () => {
    const wrapper = mount(AddressForm, { props: { modelValue: billing, label: '账单地址' } })

    const optional = wrapper.find('input[name="address2"]')
    expect(optional.exists()).toBe(true)
    expect(optional.attributes('required')).toBeUndefined()
  })

  it('marks the mandatory fields as required', () => {
    const wrapper = mount(AddressForm, { props: { modelValue: billing, label: '账单地址' } })

    expect(wrapper.find('input[name="firstName"]').attributes('required')).toBeDefined()
    expect(wrapper.find('input[name="zip"]').attributes('required')).toBeDefined()
  })

  it('keeps the two address models independent', async () => {
    const shipping: components['schemas']['Address'] = { ...billing, firstName: 'Bob', city: 'Minato' }
    const billingWrapper = mount(AddressForm, { props: { modelValue: billing, label: '账单地址' } })
    const shippingWrapper = mount(AddressForm, { props: { modelValue: shipping, label: '收货地址' } })

    await billingWrapper.find('input[name="city"]').setValue('Koto')

    expect((shippingWrapper.find('input[name="city"]').element as HTMLInputElement).value).toBe('Minato')
  })
})
