import { beforeEach, describe, expect, it } from 'vitest'

import { clearCsrfToken, csrfHeaders, getCsrfToken, setCsrfToken } from './csrf'

/**
 * T315: 前端内存 CSRF 状态。
 *
 * <p>覆盖显式更新 token 与请求头名称、登录/退出清除旧 token，以及不落任何浏览器存储
 * （plan §5.2、AT-09）。</p>
 */
describe('csrf state', () => {
  beforeEach(() => {
    clearCsrfToken()
  })

  it('starts without a token', () => {
    expect(getCsrfToken()).toBeNull()
    expect(csrfHeaders()).toEqual({})
  })

  it('keeps the token and the header name in memory only', () => {
    setCsrfToken('token-value', 'X-CSRF-TOKEN')

    expect(getCsrfToken()).toEqual({ token: 'token-value', headerName: 'X-CSRF-TOKEN' })
    expect(csrfHeaders()).toEqual({ 'X-CSRF-TOKEN': 'token-value' })
    expect(window.localStorage.length).toBe(0)
    expect(window.sessionStorage.length).toBe(0)
    expect(document.cookie).not.toContain('token-value')
  })

  it('drops the old token on login or logout', () => {
    setCsrfToken('old-token', 'X-CSRF-TOKEN')

    clearCsrfToken()

    expect(getCsrfToken()).toBeNull()
    expect(csrfHeaders()).toEqual({})
  })

  it('replaces the token explicitly', () => {
    setCsrfToken('old-token', 'X-CSRF-TOKEN')
    setCsrfToken('new-token', 'X-CSRF-TOKEN')

    expect(getCsrfToken()?.token).toBe('new-token')
  })

  it('uses the header name reported by the server', () => {
    setCsrfToken('token-value', 'X-XSRF-TOKEN')

    expect(csrfHeaders()).toEqual({ 'X-XSRF-TOKEN': 'token-value' })
  })
})
