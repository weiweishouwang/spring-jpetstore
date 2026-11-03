import type { components } from '@/shared/api/schema'

import type { HttpClient } from '@/shared/api/http'

/**
 * T322: account 前端API调用。
 *
 * <p>注册、本人资料、资料更新、偏好推荐与横幅。响应严格按 OpenAPI 类型，不在客户端重复任何业务
 * 计算（plan §6.2、§8）。</p>
 *
 * <p>凭据只出现在请求体里，不写入任何浏览器存储（NFR-04）。</p>
 */

export type AccountResponse = components['schemas']['AccountResponse']
export type Address = components['schemas']['Address']
export type Preferences = components['schemas']['Preferences']
export type RegisterAccountRequest = components['schemas']['RegisterAccountRequest']
export type UpdateAccountRequest = components['schemas']['UpdateAccountRequest']
export type Product = components['schemas']['Product']
export type Banner = components['schemas']['Banner']

/** 注册账户；成功返回 201，不自动登录。 */
export async function registerAccount(
  client: HttpClient,
  request: RegisterAccountRequest,
): Promise<AccountResponse> {
  return client.request<AccountResponse>('/api/accounts', { method: 'POST', body: request })
}

/** 读取本人资料。 */
export async function fetchMe(client: HttpClient): Promise<AccountResponse> {
  return client.request<AccountResponse>('/api/account/me')
}

/** 更新本人资料、偏好与可选密码；请求里没有用户名字段。 */
export async function updateMe(client: HttpClient, request: UpdateAccountRequest): Promise<AccountResponse> {
  return client.request<AccountResponse>('/api/account/me', { method: 'PATCH', body: request })
}

/** 偏好分类下的推荐产品。 */
export async function fetchRecommendations(client: HttpClient): Promise<Product[]> {
  return client.request<Product[]>('/api/account/me/recommendations')
}

/** 横幅；缺失或关闭时服务端返回 null。 */
export async function fetchBanner(client: HttpClient): Promise<Banner | null> {
  const banner = await client.request<Banner | null>('/api/account/me/banner')
  return banner ?? null
}
