import type { components } from '@/shared/api/schema'

import type { HttpClient } from '@/shared/api/http'

/**
 * T324: catalog 前端API调用。
 *
 * <p>分类、产品、可售项与名称搜索。响应严格按 OpenAPI 类型；金额保持字符串，不在前端折算
 * （plan §6.2、§8、Q14）。</p>
 */

export type Category = components['schemas']['Category']
export type Product = components['schemas']['Product']
export type Item = components['schemas']['Item']
export type ProductDetail = components['schemas']['ProductDetail']

/** 全部分类。 */
export async function fetchCategories(client: HttpClient): Promise<Category[]> {
  return client.request<Category[]>('/api/categories')
}

/** 分类下的产品列表。 */
export async function fetchProductsByCategory(client: HttpClient, categoryId: string): Promise<Product[]> {
  return client.request<Product[]>(`/api/categories/${encodeURIComponent(categoryId)}/products`)
}

/** 产品详情（含可售项）。 */
export async function fetchProduct(client: HttpClient, productId: string): Promise<ProductDetail> {
  return client.request<ProductDetail>(`/api/products/${encodeURIComponent(productId)}`)
}

/** 可售项详情。 */
export async function fetchItem(client: HttpClient, itemId: string): Promise<Item> {
  return client.request<Item>(`/api/items/${encodeURIComponent(itemId)}`)
}

/** 按产品名称搜索；关键词必须编码后传输。 */
export async function searchProducts(client: HttpClient, keyword: string): Promise<Product[]> {
  return client.request<Product[]>(`/api/products?keyword=${encodeURIComponent(keyword)}`)
}
