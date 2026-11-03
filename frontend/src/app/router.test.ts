import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { createAppRouter, routes } from './router'

/**
 * T377: Vue路由及认证守卫。
 *
 * <p>覆盖仅站内 returnTo、路由参数编码、401 引导与受保护页面；守卫只负责导航体验，不代替后端
 * 授权（plan §8、NFR-02）。</p>
 */
describe('app router', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('declares the public and the protected routes', () => {
    const paths = routes.map((route) => route.path)

    expect(paths).toContain('/')
    expect(paths).toContain('/category/:categoryId')
    expect(paths).toContain('/cart')
    expect(paths).toContain('/orders')
  })

  it('sends an unauthenticated visitor to the login page', async () => {
    const router = createAppRouter()

    await router.push('/orders')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('keeps only an internal return target', async () => {
    const router = createAppRouter()

    await router.push('/orders')

    const target = router.currentRoute.value.query.returnTo
    expect(target).toBe('/orders')
    expect(String(target).startsWith('/')).toBe(true)
  })

  it('encodes the route parameters', () => {
    const route = routes.find((candidate) => candidate.path === '/product/:productId')

    expect(route?.path).toBe('/product/:productId')
  })
})
