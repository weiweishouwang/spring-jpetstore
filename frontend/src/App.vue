<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { useIdentityStore } from '@/features/identity/store'

/**
 * T380: 应用布局与导航。
 *
 * <p>公共导航、登录与退出入口、欢迎语与错误反馈。所有文本都通过插值呈现，不执行任何脚本
 * （D07/D10、NFR-05）。</p>
 */

const identity = useIdentityStore()
const error = ref('')

onMounted(async () => {
  try {
    await identity.bootstrap()
  } catch {
    error.value = '会话状态读取失败，请稍后重试。'
  }
})

async function signOut(): Promise<void> {
  try {
    await identity.signOut()
  } catch {
    error.value = '退出失败，请稍后重试。'
  }
}
</script>

<template>
  <div class="app">
    <header class="app__header">
      <h1 class="app__title">JPetStore</h1>
      <nav class="app__nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/search">搜索商品</RouterLink>
        <RouterLink to="/cart">购物车</RouterLink>
        <RouterLink v-if="identity.authenticated" to="/orders">我的订单</RouterLink>
        <RouterLink v-if="identity.authenticated" to="/account">我的账户</RouterLink>
        <RouterLink v-if="!identity.authenticated" to="/login">登录</RouterLink>
        <RouterLink v-if="!identity.authenticated" to="/register">注册</RouterLink>
        <button v-if="identity.authenticated" type="button" @click="signOut">退出</button>
      </nav>
      <p v-if="identity.authenticated" class="app__welcome">欢迎，{{ identity.username }}</p>
      <p v-if="error" class="app__error" role="alert">{{ error }}</p>
    </header>
    <main class="app__main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app__header {
  border-bottom: 1px solid #d6d6d6;
  padding: 0.5rem 1rem;
}

.app__nav {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.app__error {
  color: #a4262c;
}
</style>
