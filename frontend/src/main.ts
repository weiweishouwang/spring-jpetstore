import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { createAppRouter } from './app/router'
import { fetchCsrf } from './features/identity/api'
import { useIdentityStore } from './features/identity/store'
import { setCsrfRefresher } from './shared/api/csrf'
import { httpClient } from './shared/api/http'
import './app/styles.css'

/**
 * T399: Vue依赖注入与启动入口。
 *
 * <p>装配顺序固定：CSRF token 补取 → Pinia（状态仓库）→ 路由（认证守卫依赖身份仓库）→ 身份
 * 引导（读取会话并在已登录时取得 CSRF token）。全局异常处理器把未预期的错误变成可见反馈，
 * 不静默吞掉（plan §8、D10）。</p>
 *
 * <p>这里只做装配，不创建第二套业务服务：所有调用都经由各模块的 API 模块与状态仓库
 * （plan §8）。</p>
 */
export async function bootstrap(target: HTMLElement | string = '#app'): Promise<void> {
  // 写请求的 token 补取：整页导航后内存为空时，第一个写请求先取一次 token（plan §5.2）。
  // 注册在应用入口（组合根），共享层不反向依赖业务模块。
  setCsrfRefresher(async () => {
    await fetchCsrf(httpClient)
  })
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(createAppRouter())
  app.config.errorHandler = (error) => {
    // 全局异常反馈：出错必须可见，不能静默失败。
    console.error('未预期的错误', error)
  }
  app.mount(target)

  // 身份引导：读取会话状态，已登录时同时刷新内存中的 CSRF token；
  // 守卫与启动入口共用同一次引导（ensureBootstrapped 幂等）。
  const identity = useIdentityStore(pinia)
  try {
    await identity.ensureBootstrapped()
  } catch (error) {
    console.error('会话状态读取失败', error)
  }
}

// 浏览器入口：存在挂载点时启动。测试通过导出函数显式装配。
if (typeof document !== 'undefined' && document.getElementById('app')) {
  void bootstrap()
}
