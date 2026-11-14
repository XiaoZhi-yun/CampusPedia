<template>
  <header class="navbar">
    <div class="nav-brand" @click="$router.push('/')">
      <span class="logo">学</span>
      <span class="name">学事通 <i>CampusPedia</i></span>
    </div>
    <div class="nav-links">
      <template v-if="auth.token">
        <el-button text :class="{ active: $route.path === '/' }" @click="$router.push('/')">问答</el-button>
        <el-button text :class="{ active: $route.path === '/profile' }" @click="$router.push('/profile')">个人中心</el-button>
        <el-button v-if="auth.role === 'admin'" text :class="{ active: $route.path.startsWith('/admin') }" @click="$router.push('/admin')">管理后台</el-button>
      </template>
    </div>
    <div class="nav-right">
      <template v-if="auth.token">
        <el-tooltip :content="dark ? '切换日间模式' : '切换夜间模式'" placement="bottom">
          <el-button circle text @click="toggleDark">
            <el-icon :size="18"><Sunny v-if="dark" /><Moon v-else /></el-icon>
          </el-button>
        </el-tooltip>
        <span class="user">{{ auth.username || auth.studentNo }}</span>
        <el-button text @click="logout">退出</el-button>
      </template>
      <template v-else>
        <el-button text @click="$router.push('/login')">登录 / 注册</el-button>
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const dark = ref(false)

function toggleDark() {
  dark.value = !dark.value
  document.documentElement.classList.toggle('dark', dark.value)
  localStorage.setItem('dark', dark.value ? '1' : '0')
}
function logout() { auth.logout(); router.push('/login') }
onMounted(() => {
  dark.value = localStorage.getItem('dark') === '1'
  if (dark.value) document.documentElement.classList.add('dark')
})
</script>

<style scoped>
.navbar { height: 56px; display: flex; align-items: center; padding: 0 20px; background: #fff; border-bottom: 1px solid #eee; gap: 24px; position: sticky; top: 0; z-index: 100; }
.nav-brand { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.logo { width: 30px; height: 30px; border-radius: 8px; background: linear-gradient(135deg, #4f46e5, #7c3aed); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.name { font-weight: 600; font-size: 16px; }
.name i { font-style: normal; font-size: 11px; color: #999; margin-left: 4px; }
.nav-links { flex: 1; display: flex; gap: 4px; }
.nav-links :deep(.el-button.is-text) { color: #555; }
.nav-links :deep(.el-button.is-text.active) { color: #4f46e5; background: #eef2ff; }
.nav-right { display: flex; align-items: center; gap: 8px; }
.user { font-size: 13px; color: #555; }
.dark .navbar { background: #1f2937; border-color: #374151; }
.dark .name { color: #e5e7eb; }
.dark .user { color: #d1d5db; }
.dark .nav-links :deep(.el-button.is-text) { color: #d1d5db; }
.dark .nav-links :deep(.el-button.is-text.active) { color: #a5b4fc; background: #374151; }
</style>
