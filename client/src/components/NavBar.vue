<template>
  <header class="navbar">
    <div class="nav-brand" @click="$router.push('/')">
      <span class="logo">学</span>
      <span class="name">学事通 <i>CampusPedia</i></span>
    </div>
    <div class="nav-links">
      <template v-if="auth.token">
        <el-button text :class="{ active: $route.path === '/' }" @click="$router.push('/')">
          <el-icon class="nav-ico"><ChatDotRound /></el-icon><span>问答</span>
        </el-button>
        <el-button text :class="{ active: $route.path === '/profile' }" @click="$router.push('/profile')">
          <el-icon class="nav-ico"><User /></el-icon><span>个人中心</span>
        </el-button>
        <el-button v-if="auth.role === 'admin'" text :class="{ active: $route.path.startsWith('/admin') }" @click="$router.push('/admin')">
          <el-icon class="nav-ico"><DataLine /></el-icon><span>管理后台</span>
        </el-button>
      </template>
    </div>
    <div class="nav-right">
      <template v-if="auth.token">
        <el-tooltip :content="dark ? '切换日间模式' : '切换夜间模式'" placement="bottom">
          <el-button circle text @click="toggleDark">
            <el-icon :size="18"><Sunny v-if="dark" /><Moon v-else /></el-icon>
          </el-button>
        </el-tooltip>
        <div class="user-chip">
          <span class="user-avatar">{{ (auth.username || auth.studentNo || '学').charAt(0) }}</span>
          <span class="user">{{ auth.username || auth.studentNo }}</span>
        </div>
        <el-button text @click="logout">退出</el-button>
      </template>
      <template v-else>
        <el-button type="primary" size="small" @click="$router.push('/login')">登录 / 注册</el-button>
      </template>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, User, DataLine, Sunny, Moon } from '@element-plus/icons-vue'
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
.navbar {
  height: 58px;
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #e7ebe8;
  gap: 24px;
  position: sticky;
  top: 0;
  z-index: 100;
}
.nav-brand { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #ea580c;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 16px;
}
.name { font-weight: 700; font-size: 17px; color: #1f2937; }
.name i { font-style: normal; font-size: 11px; color: #9ca3af; margin-left: 5px; font-weight: 500; }
.nav-links { flex: 1; display: flex; gap: 4px; }
.nav-ico { margin-right: 5px; }
.nav-links :deep(.el-button.is-text) { color: #55605c; padding: 0 14px; height: 36px; border-radius: 8px; }
.nav-links :deep(.el-button.is-text:hover) { color: #c2410c; background: #fff7ed; }
.nav-links :deep(.el-button.is-text.active) { color: #c2410c; background: #fff7ed; font-weight: 600; }
.nav-right { display: flex; align-items: center; gap: 10px; }
.user-chip { display: flex; align-items: center; gap: 8px; padding: 4px 12px 4px 4px; background: #f3f4f6; border-radius: 20px; }
.user-avatar { width: 26px; height: 26px; border-radius: 50%; background: #ea580c; color: #fff; font-size: 12px; font-weight: 600; display: flex; align-items: center; justify-content: center; }
.user { font-size: 13px; color: #374151; }
.dark .navbar { background: #1b1b1b; border-color: #2e2e2e; }
.dark .name { color: #e5e7eb; }
.dark .user { color: #d1d5db; }
.dark .user-chip { background: #202020; }
.dark .nav-links :deep(.el-button.is-text) { color: #d1d5db; }
.dark .nav-links :deep(.el-button.is-text:hover) { color: #fb923c; background: #202020; }
.dark .nav-links :deep(.el-button.is-text.active) { color: #fb923c; background: #2a211a; }
</style>