import { describe, expect, it, vi } from 'vitest'

import { createHttpClient } from '@/shared/api/http'

import { fetchCategories, fetchItem, fetchProduct, fetchProductsByCategory, searchProducts } from './api'

/**
 * T323: catalog 前端API调用。
 *
 * <p>验证分类、产品、SKU 与名称搜索的调用方式，搜索关键词必须编码后传输（plan §6.2、§8、
 * Q14）。</p>
 */
describe('catalog api', () => {
  it('lists the categories', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse([{ categoryId: 'FISH' }]))
    const client = createHttpClient({ fetchImpl })

    const categories = await fetchCategories(client)

    expect(categories[0]?.categoryId).toBe('FISH')
    expect(fetchImpl.mock.calls[0][0]).toBe('/api/categories')
  })

  it('lists the products of a category', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse([{ productId: 'FI-SW-01' }]))
    const client = createHttpClient({ fetchImpl })

    await fetchProductsByCategory(client, 'FISH')

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/categories/FISH/products')
  })

  it('reads a product and an item', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ product: { productId: 'FI-SW-01' }, items: [] }))
      .mockResolvedValueOnce(jsonResponse({ itemId: 'EST-1', unitPrice: '16.50' }))
    const client = createHttpClient({ fetchImpl })

    const product = await fetchProduct(client, 'FI-SW-01')
    const item = await fetchItem(client, 'EST-1')

    expect(product.product.productId).toBe('FI-SW-01')
    expect(item.unitPrice).toBe('16.50')
  })

  it('encodes the search keyword', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse([]))
    const client = createHttpClient({ fetchImpl })

    await searchProducts(client, 'a b&c')

    expect(fetchImpl.mock.calls[0][0]).toBe('/api/products?keyword=a%20b%26c')
  })

  it('keeps the money as a string', async () => {
    const fetchImpl = vi.fn(async (_url: string, _init: RequestInit) => jsonResponse({ itemId: 'EST-1', unitPrice: '16.50' }))
    const client = createHttpClient({ fetchImpl })

    const item = await fetchItem(client, 'EST-1')

    expect(typeof item.unitPrice).toBe('string')
  })
})

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
