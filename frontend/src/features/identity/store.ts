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

  /** 读取当前会话；已登录时同时刷新 CSRF token。 */
  async function bootstrap(client: HttpClient = httpClient): Promise<void> {
    const current = await fetchSession(client)
    authenticated.value = current.authenticated
    username.value = current.username ?? null
    if (current.authenticated) {
      await fetchCsrf(client)
    } else {
      clearCsrfToken()
    }
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
