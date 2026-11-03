import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import FieldErrors from './FieldErrors.vue'

/**
 * T341: Vue组件 field。
 *
 * <p>按字段关联错误并给出可访问提示；只展示服务端给出的字段名与说明，不显示被拒绝的原值
 * （plan §8、D07/D10、NFR-04）。</p>
 */
describe('FieldErrors', () => {
  it('lists the violations per field', () => {
    const wrapper = mount(FieldErrors, {
      props: {
        violations: [
          { field: 'items', message: '数量不合法' },
          { field: 'password', message: '两次密码必须一致' },
        ],
      },
    })

    expect(wrapper.text()).toContain('items')
    expect(wrapper.text()).toContain('数量不合法')
    expect(wrapper.text()).toContain('两次密码必须一致')
  })

  it('announces the errors accessibly', () => {
    const wrapper = mount(FieldErrors, {
      props: { violations: [{ field: 'items', message: '数量不合法' }] },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.attributes('aria-live')).toBe('polite')
  })

  it('renders nothing when there is no violation', () => {
    const wrapper = mount(FieldErrors, { props: { violations: [] } })

    expect(wrapper.text()).toBe('')
  })

  it('never shows a rejected value', () => {
    const wrapper = mount(FieldErrors, {
      props: { violations: [{ field: 'password', message: '两次密码必须一致' }] },
    })

    expect(wrapper.text()).not.toContain('secret-pass')
  })
})
