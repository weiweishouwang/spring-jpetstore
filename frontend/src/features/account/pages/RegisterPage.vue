<script setup lang="ts">
import { ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { registerAccount } from '@/features/account/api'
import FieldErrors from '@/shared/components/FieldErrors.vue'

/**
 * T362: 页面 RegisterPage。
 *
 * <p>注册表单：用户名唯一、密码与确认必须一致；成功后引导登录但不自动建立会话（ACC-03/04）。
 * 失败只展示服务端给出的提示，不回显密码（NFR-04）。</p>
 */

const username = ref('')
const password = ref('')
const repeatedPassword = ref('')
const email = ref('')
const phone = ref('')
const message = ref('')
const conflict = ref(false)
const registered = ref(false)

async function submit(): Promise<void> {
  message.value = ''
  conflict.value = false
  if (password.value !== repeatedPassword.value) {
    message.value = '两次密码必须一致。'
    return
  }
  try {
    await registerAccount(httpClient, {
      username: username.value,
      password: password.value,
      repeatedPassword: repeatedPassword.value,
      email: email.value,
      phone: phone.value,
      address: {
        firstName: '',
        lastName: '',
        address1: '',
        address2: null,
        city: '',
        state: '',
        zip: '',
        country: '',
      },
      preferences: {
        languagePreference: 'english',
        favouriteCategoryId: 'FISH',
        listOption: true,
        bannerOption: true,
      },
    })
    registered.value = true
  } catch (failure) {
    const code = (failure as { code?: string })?.code
    if (code === 'USERNAME_CONFLICT' || (failure as Error)?.message === 'USERNAME_CONFLICT') {
      conflict.value = true
      message.value = '该用户名已被使用。'
      return
    }
    message.value = '注册失败，请稍后重试。'
  }
}
</script>

<template>
  <section class="register">
    <h1>注册</h1>
    <p v-if="registered" class="register__done">注册成功，请前往登录。</p>
    <form @submit.prevent="submit">
      <label>
        用户名
        <input v-model="username" name="username" type="text" required maxlength="25" />
      </label>
      <label>
        密码
        <input v-model="password" name="password" type="password" required autocomplete="new-password" />
      </label>
      <label>
        确认密码
        <input v-model="repeatedPassword" name="repeatedPassword" type="password" required
               autocomplete="new-password" />
      </label>
      <label>
        邮箱
        <input v-model="email" name="email" type="email" required />
      </label>
      <label>
        电话
        <input v-model="phone" name="phone" type="text" required />
      </label>
      <p v-if="conflict" class="register__conflict">该用户名已被使用。</p>
      <FieldErrors :violations="message ? [{ field: 'form', message }] : []" />
      <button type="submit">注册</button>
    </form>
    <p><RouterLink to="/login">已有账号？去登录</RouterLink></p>
  </section>
</template>
