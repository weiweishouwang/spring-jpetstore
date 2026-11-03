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

/** 需要登录才能访问的路径。 */
const PROTECTED_PATHS = ['/account', '/cart', '/checkout', '/confirm', '/orders']

export const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: HomePage },
  { path: '/category/:categoryId', name: 'category', component: CategoryPage },
  { path: '/product/:productId', name: 'product', component: ProductPage },
  { path: '/items/:itemId', name: 'item', component: ItemPage },
  { path: '/search', name: 'search', component: SearchPage },
  { path: '/register', name: 'register', component: RegisterPage },
  { path: '/login', name: 'login', component: LoginPage },
  { path: '/account', name: 'account', component: AccountPage, meta: { requiresAuth: true } },
  { path: '/cart', name: 'cart', component: CartPage },
  { path: '/checkout', name: 'checkout', component: CheckoutPage, meta: { requiresAuth: true } },
  { path: '/confirm', name: 'confirm', component: ConfirmPage, meta: { requiresAuth: true } },
  { path: '/orders', name: 'orders', component: OrdersPage, meta: { requiresAuth: true } },
  { path: '/orders/:orderId', name: 'order-detail', component: OrderDetailPage, meta: { requiresAuth: true } },
]

/** 创建应用路由：守卫在导航前检查登录状态。 */
export function createAppRouter(history = createWebHistory()): Router {
  const router = createRouter({ history, routes })
  router.beforeEach((to) => {
    if (!to.meta.requiresAuth && !PROTECTED_PATHS.some((path) => to.path.startsWith(path))) {
      return true
    }
    const identity = useIdentityStore()
    if (identity.authenticated) {
      return true
    }
    identity.rememberReturnTo(to.fullPath)
    return { path: '/login', query: { returnTo: to.fullPath } }
  })
  return router
}
