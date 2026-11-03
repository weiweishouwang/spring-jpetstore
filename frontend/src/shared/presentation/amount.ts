/**
 * T330: 展示转换 amountformat。
 *
 * <p>金额是十进制字符串：显示时只做字符串补位，绝不经过 Number，避免大额金额的精度丢失
 * （plan §8、Q06、D05）。服务端已经按 HALF_UP 舍入并定标到两位小数，这里不再二次舍入。</p>
 */

/** 业务唯一币种（Q06）。 */
export const CURRENCY = 'CNY'

const DECIMAL = /^(\d+)(?:\.(\d+))?$/

/** 把金额字符串显示成两位小数；无法识别时原样返回，绝不猜测。 */
export function formatAmount(value: string): string {
  const trimmed = value.trim()
  const match = DECIMAL.exec(trimmed)
  if (!match) {
    return trimmed
  }
  const integer = match[1] ?? '0'
  const fraction = (match[2] ?? '').padEnd(2, '0').slice(0, 2)
  return `${integer}.${fraction}`
}

/** 带币种的金额展示；币种显式写出，避免符号歧义。 */
export function formatMoney(value: string, currency: string = CURRENCY): string {
  return `${currency} ${formatAmount(value)}`
}
