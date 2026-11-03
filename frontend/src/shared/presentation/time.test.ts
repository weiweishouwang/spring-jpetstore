import { describe, expect, it } from 'vitest'

import { formatDate, formatDateTime, hasTimeComponent } from './time'

/**
 * T331: 展示转换 timeformat。
 *
 * <p>存储与传输都是 UTC，展示按 Asia/Shanghai（Q11）；旧 DATE 精度缺失时不编造时间
 * （D12、Q11）。</p>
 */
describe('time format', () => {
  it('shows a UTC timestamp in the display zone', () => {
    expect(formatDateTime('2026-11-03T08:00:00Z')).toBe('2026-11-03 16:00')
  })

  it('shows the date of a UTC timestamp in the display zone', () => {
    expect(formatDate('2026-11-03T20:00:00Z')).toBe('2026-11-04')
  })

  it('keeps the microsecond precision of the stored value', () => {
    expect(formatDateTime('2026-11-03T08:00:00.123456Z')).toBe('2026-11-03 16:00')
  })

  it('does not invent a time for a legacy date without one', () => {
    expect(hasTimeComponent('2026-10-01')).toBe(false)
    expect(formatDateTime('2026-10-01')).toBe('2026-10-01')
  })

  it('detects a timestamp with a time part', () => {
    expect(hasTimeComponent('2026-11-03T08:00:00Z')).toBe(true)
  })
})
