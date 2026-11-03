/**
 * T334: 展示转换 errorformat。
 *
 * <p>把服务端的问题详情整理成可展示的结构：字段错误按字段列出、409 版本冲突提示刷新、
 * requestId 供排查使用。展示内容只来自服务端给定的字段，绝不拼接 SQL、异常栈或凭据原文
 * （plan §8、NFR-04、NFR-05）。</p>
 */

export interface FieldViolation {
  readonly field: string
  readonly message: string
}

export interface ProblemView {
  readonly code: string
  readonly title: string
  readonly status: number
  readonly detail?: string
  readonly requestId?: string
  readonly violations: readonly FieldViolation[]
  /** 是否需要提示用户刷新后重试（版本冲突类）。 */
  readonly refreshRequired: boolean
}

/** 需要刷新后重试的错误码（plan §5.4、§5.5）。 */
const REFRESH_CODES = ['CART_VERSION_CONFLICT', 'CHECKOUT_STALE']

/** 解析服务端的问题详情；无法识别时给出安全的通用结构。 */
export function parseProblem(payload: unknown): ProblemView {
  const body = (payload ?? {}) as Record<string, unknown>
  const violations: FieldViolation[] = Array.isArray(body.violations)
    ? body.violations
        .filter((entry): entry is Record<string, unknown> => typeof entry === 'object' && entry !== null)
        .map((entry) => ({
          field: typeof entry.field === 'string' ? entry.field : '',
          message: typeof entry.message === 'string' ? entry.message : '',
        }))
    : []
  const code = typeof body.code === 'string' ? body.code : 'UNKNOWN'
  return {
    code,
    title:
      typeof body.title === 'string'
        ? body.title
        : typeof body.message === 'string'
          ? body.message
          : '请求失败',
    status: typeof body.status === 'number' ? body.status : 0,
    detail: typeof body.detail === 'string' ? body.detail : undefined,
    requestId: typeof body.requestId === 'string' ? body.requestId : undefined,
    violations,
    refreshRequired: isRefreshRequired(code),
  }
}

/** 是否属于需要刷新后重试的冲突。 */
export function isRefreshRequired(code: string): boolean {
  return REFRESH_CODES.includes(code)
}

/** 生成展示文案：字段错误优先，其次标题，最后通用提示。 */
export function problemMessage(problem: ProblemView): string {
  if (problem.violations.length > 0) {
    return problem.violations.map((violation) => `${violation.field}: ${violation.message}`).join('；')
  }
  if (problem.refreshRequired) {
    return `${problem.title}，请刷新后重试`
  }
  return problem.title || '请求失败'
}
