<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'

import { useIdentityStore } from '@/features/identity/store'

/**
 * T364: 页面 LoginPage。
 *
 * <p>表单登录走 identity 状态仓库：成功后导航栏立即反映登录状态，CSRF token 同步刷新；
 * 失败只给通用提示，不区分用户不存在与口令错误（NFR-02）。returnTo 只接受站内路径，
 * 登录成功后回到该页面（plan §8）。</p>
 */

const props = defineProps<{ returnTo?: string }>()

const router = useRouter()
const identity = useIdentityStore()

const username = ref('')
const password = ref('')
const failed = ref(false)

async function submit(): Promise<void> {
  failed.value = false
  try {
    await identity.signIn(username.value, password.value)
    const target = props.returnTo
    await router.push(target && target.startsWith('/') && !target.startsWith('//') ? target : '/')
  } catch {
    failed.value = true
  }
}
</script>

<template>
  <section class="login">
    <h1>登录</h1>
    <form @submit.prevent="submit">
      <label>
        用户名
        <input v-model="username" name="username" type="text" required autocomplete="username" />
      </label>
      <label>
        密码
        <input v-model="password" name="password" type="password" required autocomplete="current-password" />
      </label>
      <p v-if="failed" class="login__failed">登录失败，请检查用户名与密码。</p>
      <button type="submit">登录</button>
    </form>
    <p v-if="props.returnTo && props.returnTo.startsWith('/') && !props.returnTo.startsWith('//')">
      登录后将返回：{{ props.returnTo }}
    </p>
  </section>
</template>
