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

/** token 的补取函数：由 identity 模块注册，避免共享层反向依赖业务模块。 */
type CsrfRefresher = () => Promise<void>

let refresher: CsrfRefresher | null = null

/** 注册补取函数：写请求发现没有 token 时由 HTTP 客户端调用。 */
export function setCsrfRefresher(fetcher: CsrfRefresher): void {
  refresher = fetcher
}

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

/**
 * 确保写请求发出前内存里有 token。
 *
 * <p>整页导航（刷新、直链进入）会清空内存状态：第一个写请求先补取一次 token，避免无谓的
 * 403。token 已存在时什么都不做（plan §5.2：token 只在前端内存中）。</p>
 */
export async function ensureCsrfToken(): Promise<void> {
  if (current === null && refresher) {
    await refresher()
  }
}

/** 写请求要带的请求头；没有 token 时返回空对象，绝不伪造。 */
export function csrfHeaders(): Record<string, string> {
  return current ? { [current.headerName]: current.token } : {}
}
