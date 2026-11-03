import { describe, expect, it } from 'vitest'

import { isRefreshRequired, parseProblem, problemMessage } from './problem'

/**
 * T333: 展示转换 errorformat。
 *
 * <p>字段错误按字段展示、安全问题给出通用提示、409 提示刷新、requestId 可用于排查；正文不含
 * SQL、异常栈或凭据原文（plan §8、NFR-04、NFR-05）。</p>
 */
describe('problem format', () => {
  it('lists the field violations', () => {
    const problem = parseProblem({
      code: 'VALIDATION_FAILED',
      title: '输入不合法',
      status: 400,
      requestId: 'req-1',
      violations: [{ field: 'items', message: '数量不合法' }],
    })

    expect(problem.code).toBe('VALIDATION_FAILED')
    expect(problem.requestId).toBe('req-1')
    expect(problem.violations).toEqual([{ field: 'items', message: '数量不合法' }])
    expect(problemMessage(problem)).toContain('items')
    expect(problemMessage(problem)).toContain('数量不合法')
  })

  it('asks for a refresh on a version conflict', () => {
    const problem = parseProblem({
      code: 'CART_VERSION_CONFLICT',
      title: 'Conflict',
      status: 409,
    })

    expect(problem.refreshRequired).toBe(true)
    expect(isRefreshRequired('CART_VERSION_CONFLICT')).toBe(true)
    expect(isRefreshRequired('CHECKOUT_STALE')).toBe(true)
    expect(isRefreshRequired('OUT_OF_STOCK')).toBe(false)
  })

  it('keeps a security problem generic', () => {
    const problem = parseProblem({
      code: 'AUTHENTICATION_REQUIRED',
      title: '需要登录',
      status: 401,
    })

    expect(problemMessage(problem)).toBe('需要登录')
    expect(problem.violations).toEqual([])
  })

  it('never invents content that the server did not send', () => {
    const problem = parseProblem({
      code: 'INTERNAL_ERROR',
      title: 'Internal Server Error',
      status: 500,
    })

    expect(problem.detail).toBeUndefined()
    expect(problemMessage(problem)).not.toContain('SQL')
    expect(problemMessage(problem)).not.toContain('password')
  })

  it('falls back to a generic message for an unreadable body', () => {
    const problem = parseProblem(undefined)

    expect(problem.code).toBe('UNKNOWN')
    expect(problemMessage(problem)).toBe('请求失败')
  })
})
