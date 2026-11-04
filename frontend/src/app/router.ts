import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw, Router } from 'vue-router'

import AccountPage from '@/features/account/pages/AccountPage.vue'
import CartPage from '@/features/cart/pages/CartPage.vue'
import CheckoutPage from '@/features/order/pages/CheckoutPage.vue'
import ConfirmPage from '@/features/order/pages/ConfirmPage.vue'
import CategoryPage from '@/features/catalog/pages/CategoryPage.vue'
import HomePage from '@/features/catalog/pages/HomePage.vue'
import ItemPage from '@/features/catalog/pages/ItemPage.vue'
import LoginPage from '@/features/identity/pages/LoginPage.vue'
import OrderDetailPage from '@/features/order/pages/OrderDetailPage.vue'
import OrdersPage from '@/features/order/pages/OrdersPage.vue'
import ProductPage from '@/features/catalog/pages/ProductPage.vue'
import RegisterPage from '@/features/account/pages/RegisterPage.vue'
import SearchPage from '@/features/catalog/pages/SearchPage.vue'
import { useIdentityStore } from '@/features/identity/store'

/**
 * T378: Vue路由及认证守卫。
 *
 * <p>守卫只处理导航体验：未登录访问受保护页面时引导到登录页，并记住站内返回目标；它不代替后端
 * 授权，真正的访问控制始终由服务端判定（plan §8、NFR-02）。</p>
 *
 * <p>路由参数按 vue-router 的约定编码，页面自己负责把参数放进 API 路径时再编码一次。</p>
 */

/** 需要登录才能访问的路径；购物车对游客开放（Q08），不在此列。 */
const PROTECTED_PATHS = ['/account', '/checkout', '/confirm', '/orders']

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: HomePage },
  // props: true 把路径参数作为组件 prop 传入；否则页面拿到的参数是 undefined。
  { path: '/category/:categoryId', name: 'category', component: CategoryPage, props: true },
  { path: '/product/:productId', name: 'product', component: ProductPage, props: true },
  { path: '/items/:itemId', name: 'item', component: ItemPage, props: true },
  // 搜索关键词通过查询参数传入页面：/search?keyword=angel（Q14）。
  {
    path: '/search',
    name: 'search',
    component: SearchPage,
    props: (route) => ({ initialKeyword: typeof route.query.keyword === 'string' ? route.query.keyword : '' }),
  },
  { path: '/register', name: 'register', component: RegisterPage },
  {
    path: '/login',
    name: 'login',
    component: LoginPage,
    // 守卫记住的站内返回目标通过查询参数传给登录页。
    props: (route) => ({ returnTo: typeof route.query.returnTo === 'string' ? route.query.returnTo : undefined }),
  },
  { path: '/account', name: 'account', component: AccountPage, meta: { requiresAuth: true } },
  { path: '/cart', name: 'cart', component: CartPage },
  { path: '/checkout', name: 'checkout', component: CheckoutPage, meta: { requiresAuth: true } },
  { path: '/confirm', name: 'confirm', component: ConfirmPage, meta: { requiresAuth: true } },
  { path: '/orders', name: 'orders', component: OrdersPage, meta: { requiresAuth: true } },
  {
    path: '/orders/:orderId',
    name: 'order-detail',
    component: OrderDetailPage,
    props: true,
    meta: { requiresAuth: true },
  },
]

/** 创建应用路由：守卫在导航前检查登录状态。 */
export function createAppRouter(history = createWebHistory()): Router {
  const router = createRouter({ history, routes })
  router.beforeEach(async (to) => {
    if (!to.meta.requiresAuth && !PROTECTED_PATHS.some((path) => to.path.startsWith(path))) {
      return true
    }
    const identity = useIdentityStore()
    // 整页直达受保护页时，先等会话恢复完成再判定，避免把已登录用户送去登录页。
    await identity.ensureBootstrapped()
    if (identity.authenticated) {
      return true
    }
    identity.rememberReturnTo(to.fullPath)
    return { path: '/login', query: { returnTo: to.fullPath } }
  })
  return router
}
