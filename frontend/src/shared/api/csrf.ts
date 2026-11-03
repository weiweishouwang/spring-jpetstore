/**
 * T316: 前端内存 CSRF 状态。
 *
 * <p>CSRF token 只保留在内存里：不写入 localStorage、sessionStorage 或 Cookie，页面刷新后由
 * `/api/auth/csrf` 重新取得（plan §5.2）。登录与退出都会作废旧 token——会话标识轮换后旧 token
 * 不再有效（AT-09）。</p>
 */

export interface CsrfToken {
  /** token 值。 */
  readonly token: string
  /** 携带 token 的请求头名称。 */
  readonly headerName: string
}

let current: CsrfToken | null = null

/** 显式更新 token 与请求头名称。 */
export function setCsrfToken(token: string, headerName: string): void {
  current = { token, headerName }
}

/** 读取当前 token；没有时返回 null。 */
export function getCsrfToken(): CsrfToken | null {
  return current
}

/** 作废当前 token：登录、退出或收到 403 之后调用。 */
export function clearCsrfToken(): void {
  current = null
}

/** 写请求要带的请求头；没有 token 时返回空对象，绝不伪造。 */
export function csrfHeaders(): Record<string, string> {
  return current ? { [current.headerName]: current.token } : {}
}
