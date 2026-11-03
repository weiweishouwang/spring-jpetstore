<script setup lang="ts">
import { ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { login } from '@/features/identity/api'

/**
 * T364: 页面 LoginPage。
 *
 * <p>表单登录；失败只给通用提示，不区分用户不存在与口令错误（NFR-02）。returnTo 只接受站内
 * 路径，登录成功后回到该页面（plan §8）。</p>
 */

const props = defineProps<{ returnTo?: string }>()

const username = ref('')
const password = ref('')
const failed = ref(false)

async function submit(): Promise<void> {
  failed.value = false
  try {
    await login(httpClient, username.value, password.value)
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
