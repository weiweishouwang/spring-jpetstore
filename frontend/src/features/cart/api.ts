import type { components } from '@/shared/api/schema'

import type { HttpClient } from '@/shared/api/http'

/**
 * T326: cart 前端API调用。
 *
 * <p>读取购物车不会产生写操作；所有写操作都携带 expectedVersion，冲突由服务端返回 409，客户端
 * 提示刷新而不是静默覆盖其他标签页的修改（plan §5.4）。</p>
 *
 * <p>响应严格按 OpenAPI 类型；金额保持字符串，客户端不重算权威金额（plan §6.2、§8）。</p>
 */

export type CartResponse = components['schemas']['CartResponse']
export type CartQuantityEntry = components['schemas']['CartQuantityEntry']

/** 读取当前购物车；无车时服务端返回空 DTO。 */
export async function fetchCart(client: HttpClient): Promise<CartResponse> {
  return client.request<CartResponse>('/api/cart')
}

/** 加购：每次调用加 1，不自动重试。 */
export async function addItem(client: HttpClient, itemId: string, expectedVersion: number): Promise<CartResponse> {
  return client.request<CartResponse>('/api/cart/items', {
    method: 'POST',
    body: { itemId, expectedVersion },
  })
}

/** 修改单项数量；数量小于 1 表示移除。 */
export async function setQuantity(
  client: HttpClient,
  itemId: string,
  quantity: number,
  expectedVersion: number,
): Promise<CartResponse> {
  return client.request<CartResponse>(`/api/cart/items/${encodeURIComponent(itemId)}`, {
    method: 'PUT',
    body: { quantity, expectedVersion },
  })
}

/** 整批数量更新；服务端先校验整批，非法时不部分写入。 */
export async function applyQuantities(
  client: HttpClient,
  items: CartQuantityEntry[],
  expectedVersion: number,
): Promise<CartResponse> {
  return client.request<CartResponse>('/api/cart/items', {
    method: 'PUT',
    body: { expectedVersion, items },
  })
}

/** 移除某个可售项；版本通过查询参数传递。 */
export async function removeItem(
  client: HttpClient,
  itemId: string,
  expectedVersion: number,
): Promise<CartResponse> {
  return client.request<CartResponse>(
    `/api/cart/items/${encodeURIComponent(itemId)}?expectedVersion=${expectedVersion}`,
    { method: 'DELETE' },
  )
}

/**
 * 显式重试登录合并。
 *
 * <p>合并超限时服务端保留双方内容并返回 mergeConflict 与 pendingMerges；用户调整账号购物车后
 * 用同一个接口重试，仍超限则返回 409（Q08）。</p>
 */
export async function mergeCart(client: HttpClient, expectedVersion: number): Promise<CartResponse> {
  return client.request<CartResponse>('/api/cart/merge', {
    method: 'POST',
    body: { expectedVersion },
  })
}
