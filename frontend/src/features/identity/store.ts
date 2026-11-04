import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { clearCsrfToken } from '@/shared/api/csrf'
import { httpClient } from '@/shared/api/http'
import type { HttpClient } from '@/shared/api/http'

import { fetchCsrf, fetchSession, login, logout } from './api'
import type { SessionResponse } from './api'

/**
 * T336: identity Vue状态仓库。
 *
 * <p>只保存当前会话状态与站内返回目标：口令、长期 token 与密码都不进入仓库，也不写入任何浏览器
 * 存储（plan §8、NFR-04）。登录后重新取得 CSRF token，退出清除身份与 token（AT-09）。</p>
 *
 * <p>returnTo 只接受站内路径：外部地址会被丢弃，避免开放重定向（NFR-02）。</p>
 */



export const useIdentityStore = defineStore('identity', () => {
  const authenticated = ref(false)
  const username = ref<string | null>(null)
  const returnTo = ref<string | null>(null)

  const session = computed<SessionResponse>(() => ({
    authenticated: authenticated.value,
    username: authenticated.value ? username.value : null,
  }))

  /**
   * 读取当前会话，并取得会话级 CSRF token。
   *
   * <p>token 与会话绑定，游客的写请求（加购）同样需要：引导时一并取得，写请求就不必在发送前
   * 临时补取——补取期间如果发生整页导航，请求会被中断，写操作随之丢失（plan §5.2）。token 依旧
   * 只保留在内存里。</p>
   *
   * <p>取不到 token 不影响会话状态：写请求会按需再补取一次（见 shared/api/csrf.ts）。</p>
   */
  async function bootstrap(client: HttpClient = httpClient): Promise<void> {
    const current = await fetchSession(client)
    authenticated.value = current.authenticated
    username.value = current.username ?? null
    try {
      await fetchCsrf(client)
    } catch {
      clearCsrfToken()
    }
  }

  // 会话引导只做一次；失败（如网络抖动）不缓存，下次再试。
  let bootstrapPromise: Promise<void> | null = null

  /**
   * 确保会话已经读取：路由守卫在整页直达受保护页时等待它完成，再判定登录状态。
   *
   * <p>失败按匿名处理：守卫会引导到登录页，而不是让导航挂在未处理的拒绝上。</p>
   */
  function ensureBootstrapped(client: HttpClient = httpClient): Promise<void> {
    if (!bootstrapPromise) {
      bootstrapPromise = bootstrap(client).catch(() => {
        bootstrapPromise = null
      })
    }
    return bootstrapPromise
  }

  /** 登录：口令只在这一次调用里出现。 */
  async function signIn(user: string, password: string, client: HttpClient = httpClient): Promise<void> {
    const result = await login(client, user, password)
    authenticated.value = result.authenticated
    username.value = result.username ?? null
  }

  /** 退出：清除身份、CSRF token 与站内返回目标。 */
  async function signOut(client: HttpClient = httpClient): Promise<void> {
    await logout(client)
    authenticated.value = false
    username.value = null
    returnTo.value = null
    clearCsrfToken()
  }

  /** 记住站内返回目标；外部地址一律忽略。 */
  function rememberReturnTo(target: string | null | undefined): void {
    returnTo.value = isInternalTarget(target) ? (target as string) : null
  }

  /** 取出并清除站内返回目标。 */
  function consumeReturnTo(): string | null {
    const target = returnTo.value
    returnTo.value = null
    return target
  }

  return {
    authenticated,
    username,
    returnTo,
    session,
    bootstrap,
    ensureBootstrapped,
    signIn,
    signOut,
    rememberReturnTo,
    consumeReturnTo,
  }
})

/** 只接受站内绝对路径：必须以单个斜杠开头，且不是协议相对地址。 */
function isInternalTarget(target: string | null | undefined): boolean {
  if (!target) {
    return false
  }
  return target.startsWith('/') && !target.startsWith('//')
}
