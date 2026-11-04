import type { components } from '@/shared/api/schema'

import { clearCsrfToken, setCsrfToken } from '@/shared/api/csrf'
import type { HttpClient } from '@/shared/api/http'

/**
 * T320: identity 前端API调用。
 *
 * <p>只负责调用与类型：会话状态、CSRF token、登录与退出。响应严格按 OpenAPI 类型，不在客户端
 * 重复任何业务计算（plan §6.2、§8）。</p>
 *
 * <p>登录成功后重新取得 CSRF token：会话标识轮换后旧 token 失效（AT-09）。退出清除内存中的
 * token，不写入任何浏览器存储（NFR-04）。写请求的 token 补取由应用入口注册
 * （见 main.ts），避免共享层反向依赖业务模块。</p>
 */

export type SessionResponse = components['schemas']['SessionResponse']
export type CsrfResponse = components['schemas']['CsrfResponse']

/** 读取当前会话状态；未登录时 authenticated 为 false。 */
export async function fetchSession(client: HttpClient): Promise<SessionResponse> {
  return client.request<SessionResponse>('/api/auth/session')
}

/** 取得 CSRF token 并保存在内存里。 */
export async function fetchCsrf(client: HttpClient): Promise<CsrfResponse> {
  const csrf = await client.request<CsrfResponse>('/api/auth/csrf')
  setCsrfToken(csrf.token, csrf.headerName)
  return csrf
}

/**
 * 登录：表单编码提交用户名与口令，成功后刷新 CSRF token。
 *
 * <p>口令只在这一次请求里出现，不写入任何存储（NFR-04）。</p>
 */
export async function login(client: HttpClient, username: string, password: string): Promise<SessionResponse> {
  const session = await client.request<SessionResponse>('/api/auth/session', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }).toString(),
  })
  await fetchCsrf(client)
  return session
}

/** 退出：会话失效后清除内存中的 CSRF token。 */
export async function logout(client: HttpClient): Promise<void> {
  await client.request<void>('/api/auth/logout', { method: 'POST' })
  clearCsrfToken()
}
