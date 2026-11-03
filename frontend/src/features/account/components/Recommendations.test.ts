import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { components } from '@/shared/api/schema'

import Recommendations from './Recommendations.vue'

/**
 * T349: Vue组件 recommend。
 *
 * <p>推荐来自偏好分类，不是手动收藏；没有内容时正常显示空状态；文本只作为纯文本呈现，不执行
 * 脚本（PER-01～03、D07、NFR-05）。</p>
 */
describe('Recommendations', () => {
  const products: components['schemas']['Product'][] = [
    { productId: 'FI-SW-01', categoryId: 'FISH', name: 'Angelfish', description: 'Saltwater fish', imagePath: null },
  ]

  it('lists the recommended products', () => {
    const wrapper = mount(Recommendations, { props: { products } })

    expect(wrapper.text()).toContain('Angelfish')
  })

  it('says that the list comes from the favourite category', () => {
    const wrapper = mount(Recommendations, { props: { products } })

    expect(wrapper.text()).toContain('偏好分类')
  })

  it('shows an empty state when there is nothing to recommend', () => {
    const wrapper = mount(Recommendations, { props: { products: [] } })

    expect(wrapper.text()).toContain('暂无')
  })

  it('renders the product text without executing scripts', () => {
    const wrapper = mount(Recommendations, {
      props: {
        products: [
          {
            productId: 'FI-SW-01',
            categoryId: 'FISH',
            name: '<script>alert(1)</script>',
            description: 'd',
            imagePath: null,
          },
        ],
      },
    })

    expect(wrapper.html()).not.toContain('<script>alert(1)</script>')
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
  })
})
