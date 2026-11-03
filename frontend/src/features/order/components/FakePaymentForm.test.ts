import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { components } from '@/shared/api/schema'

import FakePaymentForm from './FakePaymentForm.vue'

/**
 * T345: Vue组件 cardform。
 *
 * <p>只提供三种模拟卡型、明确提示只接受演示假卡号、有效期按 MM/YYYY 校验；卡信息不写入任何
 * 存储（plan §8、Q05、Q15、NFR-04）。</p>
 */
describe('FakePaymentForm', () => {
  const payment: components['schemas']['FakePaymentRequest'] = {
    cardType: 'Visa',
    cardNumber: 'FAKE-4111-1111-1111-1111',
    expiryDate: '12/2026',
  }

  it('offers the three simulated card types', () => {
    const wrapper = mount(FakePaymentForm, { props: { modelValue: payment } })

    const options = wrapper.findAll('select[name="cardType"] option')
    expect(options.map((option) => option.text())).toEqual(['Visa', 'MasterCard', 'American Express'])
  })

  it('states that only demo card numbers are accepted', () => {
    const wrapper = mount(FakePaymentForm, { props: { modelValue: payment } })

    expect(wrapper.text()).toContain('FAKE-')
    expect(wrapper.text()).toContain('演示')
  })

  it('emits the updated payment when a field changes', async () => {
    const wrapper = mount(FakePaymentForm, { props: { modelValue: payment } })

    await wrapper.find('input[name="expiryDate"]').setValue('11/2027')

    const emitted = wrapper.emitted('update:modelValue')
    const value = (emitted?.[0]?.[0] ?? {}) as components['schemas']['FakePaymentRequest']
    expect(value.expiryDate).toBe('11/2027')
    expect(value.cardType).toBe('Visa')
  })

  it('marks the expiry as MM/YYYY', () => {
    const wrapper = mount(FakePaymentForm, { props: { modelValue: payment } })

    expect(wrapper.find('input[name="expiryDate"]').attributes('placeholder')).toBe('MM/YYYY')
  })

  it('keeps the card number out of the DOM attributes', () => {
    const wrapper = mount(FakePaymentForm, { props: { modelValue: payment } })

    expect(wrapper.html()).not.toContain('autocomplete="cc-number"')
  })
})
