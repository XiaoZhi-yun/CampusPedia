<template>
  <header class="navbar">
    <div class="nav-brand" @click="$router.push('/')">
      <span class="logo">学</span>
      <span class="name">学事通 <i>CampusPedia</i></span>
    </div>
    <!-- 主导航：登录态下按角色显示问答 / 个人中心 / 管理后台，激活态由当前路由决定 -->
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
    <!-- 右侧操作区：日 / 夜切换、用户标识、退出；未登录时改为登录入口 -->
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
/**
 * 职责：全站顶部导航栏——品牌入口、主导航、日 / 夜切换、用户标识与退出。
 * 所属页面：被各视图以 <NavBar /> 引入，非独立路由页面。
 * 全局状态：useAuthStore 的 token / role 决定菜单显隐（管理员才见「管理后台」）；未登录显示登录入口。
 * 副作用：切换 .dark 类并写入 localStorage，onMounted 时读取偏好恢复，故刷新后主题不丢失。
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChatDotRound, User, DataLine, Sunny, Moon } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const dark = ref(false)

/**
 * 切换日 / 夜模式并持久化偏好。
 * 副作用：给 <html> 切换 .dark 类、写 localStorage['dark']；与 onMounted 的读取恢复成对。
 */
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
  height: var(--cp-header-h);
  display: flex;
  align-items: center;
  gap: var(--cp-sp-5);
  padding: 0 var(--cp-sp-5);
  background: var(--cp-card-bg);
  border-bottom: 1px solid var(--cp-border);
  box-shadow: var(--cp-shadow-1);
  position: sticky;
  top: 0;
  z-index: 100;
}
.nav-brand { display: flex; align-items: center; gap: var(--cp-sp-2); cursor: pointer; flex: 0 0 auto; }
.logo {
  width: 34px;
  height: 34px;
  border-radius: var(--cp-r-sm);
  background: var(--cp-accent);
  color: var(--cp-on-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-md);
  font-weight: var(--cp-fw-bold);
  box-shadow: var(--cp-shadow-1);
  transition: transform var(--cp-dur-base) var(--cp-ease);
}
.nav-brand:hover .logo { transform: rotate(-6deg); }
.name {
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-md);
  font-weight: var(--cp-fw-bold);
  color: var(--cp-text-1);
  letter-spacing: .04em;
  white-space: nowrap;
}
.name i {
  font-family: var(--cp-font-sans);
  font-style: normal;
  font-size: var(--cp-fs-xs);
  color: var(--cp-text-3);
  margin-left: var(--cp-sp-1);
  font-weight: var(--cp-fw-medium);
  letter-spacing: .08em;
}
.nav-links { flex: 1; display: flex; gap: var(--cp-sp-1); min-width: 0; }
.nav-ico { margin-right: var(--cp-sp-1); }
.nav-links :deep(.el-button.is-text) {
  position: relative;
  color: var(--cp-text-2);
  padding: 0 var(--cp-sp-3);
  height: 44px;
  border-radius: var(--cp-r-sm);
  font-size: var(--cp-fs-base);
  transition: color var(--cp-dur-fast) ease-out, background-color var(--cp-dur-fast) ease-out;
}
/* 导航激活：下划线扫过，而非又一块同色圆角背景 */
.nav-links :deep(.el-button.is-text)::after {
  content: '';
  position: absolute;
  left: 14px; right: 14px; bottom: 5px;
  height: 2px;
  border-radius: 2px;
  background: var(--cp-accent);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform var(--cp-dur-base) var(--cp-ease);
}
.nav-links :deep(.el-button.is-text:hover) { color: var(--cp-emphasis); background: var(--cp-emphasis-soft); }
.nav-links :deep(.el-button.is-text.active) { color: var(--cp-emphasis); font-weight: var(--cp-fw-semi); }
.nav-links :deep(.el-button.is-text.active)::after { transform: scaleX(1); }
.nav-right { display: flex; align-items: center; gap: var(--cp-sp-2); flex: 0 0 auto; }
.user-chip {
  display: flex;
  align-items: center;
  gap: var(--cp-sp-2);
  padding: var(--cp-sp-1) var(--cp-sp-3) var(--cp-sp-1) var(--cp-sp-1);
  background: var(--cp-hover-bg);
  border: 1px solid var(--cp-border-soft);
  border-radius: var(--cp-r-pill);
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--cp-accent);
  color: var(--cp-on-accent);
  font-size: var(--cp-fs-xs);
  font-weight: var(--cp-fw-semi);
  display: flex;
  align-items: center;
  justify-content: center;
}
.user { font-size: var(--cp-fs-sm); color: var(--cp-text-2); font-weight: var(--cp-fw-medium); white-space: nowrap; }

@media (max-width: 900px) {
  .navbar { gap: var(--cp-sp-3); padding: 0 var(--cp-sp-3); }
  .name i { display: none; }
  .nav-links :deep(.el-button.is-text) { padding: 0 var(--cp-sp-2); }
}
@media (max-width: 600px) {
  .navbar { gap: var(--cp-sp-2); padding: 0 var(--cp-sp-2); }
  .name { font-size: var(--cp-fs-sm); }
  .nav-links { overflow-x: auto; scrollbar-width: none; }
  .nav-links::-webkit-scrollbar { display: none; }
  .nav-links :deep(.el-button.is-text) { padding: 0 var(--cp-sp-2); font-size: var(--cp-fs-sm); }
  .nav-links :deep(.el-button.is-text)::after { display: none; }
  .user-chip { padding: var(--cp-sp-1); }
  .user { display: none; }
  .nav-right { gap: var(--cp-sp-1); }
}
</style>