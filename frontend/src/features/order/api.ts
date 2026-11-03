import type { components } from '@/shared/api/schema'

import type { HttpClient } from '@/shared/api/http'

/**
 * T328: order 前端API调用。
 *
 * <p>创建确认快照、提交订单、本人订单列表与详情。提交订单必须带幂等键；重试复用同一个键，
 * 服务端返回原结果并带 Idempotency-Replayed 标记（plan §5.7、AT-R02）。</p>
 *
 * <p>响应严格按 OpenAPI 类型；金额保持字符串，客户端不重算成交金额（plan §6.2、§8）。</p>
 */

export type CreateCheckoutRequest = components['schemas']['CreateCheckoutRequest']
export type CheckoutResponse = components['schemas']['CheckoutResponse']
export type OrderCreatedResponse = components['schemas']['OrderCreatedResponse']
export type OrderSummary = components['schemas']['OrderSummary']
export type OrderDetail = components['schemas']['OrderDetail']

/** 订单提交结果。 */
export interface OrderPlacement<T> {
  readonly status: number
  /** 新资源的站内路径；没有时为空。 */
  readonly location?: string
  /** 是否为幂等重放。 */
  readonly replayed: boolean
  readonly body: T
}

/** 创建确认快照；服务端返回 201。 */
export async function createCheckout(
  client: HttpClient,
  request: CreateCheckoutRequest,
): Promise<CheckoutResponse> {
  return client.request<CheckoutResponse>('/api/checkouts', { method: 'POST', body: request })
}

/**
 * 提交订单。
 *
 * @param idempotencyKey 幂等键；重试必须复用同一个键，绝不重新生成（plan §5.7）
 */
export async function placeOrder(
  client: HttpClient,
  checkoutId: string,
  idempotencyKey: string,
): Promise<OrderPlacement<OrderCreatedResponse>> {
  const response = await client.requestWithResponse<OrderCreatedResponse>('/api/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { checkoutId },
  })
  return {
    status: response.status,
    location: response.headers.get('Location') ?? undefined,
    replayed: response.headers.get('Idempotency-Replayed') === 'true',
    body: response.body,
  }
}

/** 本人订单列表；最新优先，首期不分页。 */
export async function listOrders(client: HttpClient): Promise<OrderSummary[]> {
  return client.request<OrderSummary[]>('/api/orders')
}

/** 本人订单详情；他人订单与不存在订单都是 404。 */
export async function fetchOrder(client: HttpClient, orderId: string): Promise<OrderDetail> {
  return client.request<OrderDetail>(`/api/orders/${encodeURIComponent(orderId)}`)
}
