<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { httpClient } from '@/shared/api/http'

import { fetchMe, updateMe } from '@/features/account/api'
import type { AccountResponse } from '@/features/account/api'

/**
 * T366: 页面 AccountPage。
 *
 * <p>用户名只读；密码留空表示保留原口令，非空时两次必须一致；资料与偏好保存后用服务端返回的
 * 值刷新（ACC-07/08、Q09、plan §8）。</p>
 */

const profile = ref<AccountResponse | null>(null)
const email = ref('')
const phone = ref('')
const password = ref('')
const repeatedPassword = ref('')
const languagePreference = ref<'english' | 'japanese'>('english')
const favouriteCategoryId = ref<'FISH' | 'DOGS' | 'REPTILES' | 'CATS' | 'BIRDS'>('FISH')
const listOption = ref(true)
const bannerOption = ref(true)
const saved = ref(false)
const failed = ref(false)
const loading = ref(true)

onMounted(async () => {
  try {
    const current = await fetchMe(httpClient)
    profile.value = current
    email.value = current.email
    phone.value = current.phone
    languagePreference.value = current.preferences.languagePreference
    favouriteCategoryId.value = current.preferences.favouriteCategoryId
    listOption.value = current.preferences.listOption
    bannerOption.value = current.preferences.bannerOption
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

async function submit(): Promise<void> {
  saved.value = false
  failed.value = false
  if (password.value !== repeatedPassword.value) {
    failed.value = true
    return
  }
  try {
    const updated = await updateMe(httpClient, {
      email: email.value,
      phone: phone.value,
      address: profile.value?.address ?? {
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
        languagePreference: languagePreference.value,
        favouriteCategoryId: favouriteCategoryId.value,
        listOption: listOption.value,
        bannerOption: bannerOption.value,
      },
      password: password.value,
      repeatedPassword: repeatedPassword.value,
    })
    profile.value = updated
    email.value = updated.email
    phone.value = updated.phone
    password.value = ''
    repeatedPassword.value = ''
    saved.value = true
  } catch {
    failed.value = true
  }
}
</script>

<template>
  <section class="account">
    <h1>我的账户</h1>
    <p v-if="loading">加载中…</p>
    <form v-else @submit.prevent="submit">
      <label>
        用户名
        <input name="username" type="text" :value="profile?.username ?? ''" readonly />
      </label>
      <label>
        邮箱
        <input v-model="email" name="email" type="email" required />
      </label>
      <label>
        电话
        <input v-model="phone" name="phone" type="text" required />
      </label>
      <label>
        新密码（留空表示不改）
        <input v-model="password" name="password" type="password" autocomplete="new-password" />
      </label>
      <label>
        确认新密码
        <input v-model="repeatedPassword" name="repeatedPassword" type="password" autocomplete="new-password" />
      </label>
      <label>
        语言偏好
        <select v-model="languagePreference" name="languagePreference">
          <option value="english">english</option>
          <option value="japanese">japanese</option>
        </select>
      </label>
      <label>
        收藏分类
        <select v-model="favouriteCategoryId" name="favouriteCategoryId">
          <option value="FISH">FISH</option>
          <option value="DOGS">DOGS</option>
          <option value="REPTILES">REPTILES</option>
          <option value="CATS">CATS</option>
          <option value="BIRDS">BIRDS</option>
        </select>
      </label>
      <label>
        <input v-model="listOption" name="listOption" type="checkbox" /> 显示偏好分类推荐
      </label>
      <label>
        <input v-model="bannerOption" name="bannerOption" type="checkbox" /> 显示横幅
      </label>
      <p v-if="saved" class="account__saved">已保存。</p>
      <p v-if="failed" class="account__failed">保存失败：两次密码必须一致，或输入不合法。</p>
      <button type="submit">保存</button>
    </form>
  </section>
</template>
