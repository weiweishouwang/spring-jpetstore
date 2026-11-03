import { describe, expect, it } from 'vitest'

import { formatAmount, formatMoney } from './amount'

/**
 * T329: 展示转换 amountformat。
 *
 * <p>金额是十进制字符串：显示时补足两位小数，绝不经过 Number，避免精度丢失（plan §8、Q06、
 * D05）。</p>
 */
describe('amount format', () => {
  it('keeps a two decimal amount unchanged', () => {
    expect(formatAmount('16.50')).toBe('16.50')
  })

  it('completes the missing fraction', () => {
    expect(formatAmount('16.5')).toBe('16.50')
    expect(formatAmount('16')).toBe('16.00')
  })

  it('never loses precision of a large amount', () => {
    expect(formatAmount('99999999.99')).toBe('99999999.99')
  })

  it('does not re-round a value that already has two decimals', () => {
    expect(formatAmount('0.10')).toBe('0.10')
  })

  it('returns an unexpected value unchanged instead of guessing', () => {
    expect(formatAmount('not-a-number')).toBe('not-a-number')
  })

  it('names the currency explicitly', () => {
    expect(formatMoney('16.50')).toBe('CNY 16.50')
    expect(formatMoney('16.5')).toBe('CNY 16.50')
  })
})
