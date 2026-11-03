import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { components } from '@/shared/api/schema'

import CartTable from './CartTable.vue'

/**
 * T347: Vue组件 carttable。
 *
 * <p>显示行数、商品数量总和与小计，数量为 0 或负数时按决策移除；空车与加载状态各有明确展示
 * （plan §5.4、CRT-06、CRT-09）。</p>
 */
describe('CartTable', () => {
  const cart: components['schemas']['CartResponse'] = {
    version: 3,
    lineCount: 1,
    totalQuantity: 2,
    items: [
      {
        item: {
          itemId: 'EST-1',
          productId: 'FI-SW-01',
          productName: 'Angelfish',
          attributes: ['Large'],
          unitPrice: '16.50',
          inStock: true,
          availableQuantity: 10,
          purchasable: true,
        },
        quantity: 2,
        lineAmount: '33.00',
      },
    ],
    subtotal: '33.00',
    currency: 'CNY',
    mergeConflict: false,
    pendingMerges: [],
  }

  it('shows the line count, the total quantity and the subtotal', () => {
    const wrapper = mount(CartTable, { props: { cart } })

    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('33.00')
  })

  it('shows the product name as plain text', () => {
    const wrapper = mount(CartTable, { props: { cart } })

    expect(wrapper.text()).toContain('Angelfish')
    expect(wrapper.html()).not.toContain('<script')
  })

  it('asks for a removal when the quantity drops below one', async () => {
    const wrapper = mount(CartTable, { props: { cart } })

    await wrapper.find('input[name="quantity-EST-1"]').setValue('0')

    const removed = wrapper.emitted('remove')
    expect(removed).toBeTruthy()
    expect(removed?.[0]?.[0]).toBe('EST-1')
  })

  it('asks for a quantity change', async () => {
    const wrapper = mount(CartTable, { props: { cart } })

    await wrapper.find('input[name="quantity-EST-1"]').setValue('5')
    await wrapper.find('input[name="quantity-EST-1"]').trigger('change')

    const changed = wrapper.emitted('set-quantity')
    expect(changed).toBeTruthy()
    expect(changed?.[0]?.[0]).toEqual({ itemId: 'EST-1', quantity: 5 })
  })

  it('shows an empty cart', () => {
    const wrapper = mount(CartTable, { props: { cart: { ...cart, items: [], lineCount: 0, totalQuantity: 0 } } })

    expect(wrapper.text()).toContain('空')
  })

  it('shows the loading state', () => {
    const wrapper = mount(CartTable, { props: { cart, busy: true } })

    expect(wrapper.text()).toContain('加载')
  })
})
