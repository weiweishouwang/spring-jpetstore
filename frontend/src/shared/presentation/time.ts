/**
 * T332: 展示转换 timeformat。
 *
 * <p>存储与传输都是 UTC（Q11），界面按 Asia/Shanghai 展示。旧实现的历史记录只有日期精度，
 * 这类值不补造时间部分，只显示日期（D12、Q11）。</p>
 */

/** 展示时区（Q11）。 */
export const DISPLAY_TIME_ZONE = 'Asia/Shanghai'

const DATE_TIME = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/

/** 值是否带时间部分。 */
export function hasTimeComponent(value: string): boolean {
  return DATE_TIME.test(value.trim())
}

/** 按展示时区显示日期与时间；没有时间部分时只显示日期。 */
export function formatDateTime(value: string): string {
  const trimmed = value.trim()
  if (!hasTimeComponent(trimmed)) {
    return formatDate(trimmed)
  }
  const parts = formatToParts(trimmed, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
}

/** 按展示时区显示日期。 */
export function formatDate(value: string): string {
  const parts = formatToParts(value.trim(), { year: 'numeric', month: '2-digit', day: '2-digit' })
  return `${parts.year}-${parts.month}-${parts.day}`
}

function formatToParts(value: string, options: Intl.DateTimeFormatOptions): Record<string, string> {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return { year: '', month: '', day: '', hour: '', minute: '' }
  }
  const formatter = new Intl.DateTimeFormat('sv-SE', { timeZone: DISPLAY_TIME_ZONE, ...options })
  const parts: Record<string, string> = {}
  for (const part of formatter.formatToParts(date)) {
    parts[part.type] = part.value
  }
  return parts
}
