<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="brand">
        <div class="brand-logo">学</div>
        <h2>学事通 · CampusPedia</h2>
        <p class="sub">校园学生事务智能问答</p>
      </div>
      <!-- 登录 / 注册共用一张卡片，由 tab 切换，两者共享同一份 loading 态 -->
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginRef" @submit.prevent>
            <el-form-item prop="studentNo">
              <el-input v-model="loginForm.studentNo" placeholder="学号" :prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" size="large" @keyup.enter="login" />
            </el-form-item>
            <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="login">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" :rules="regRules" ref="regRef" @submit.prevent>
            <el-form-item prop="studentNo"><el-input v-model="regForm.studentNo" placeholder="学号（4-20位）" :prefix-icon="User" size="large" /></el-form-item>
            <el-form-item prop="username"><el-input v-model="regForm.username" placeholder="姓名" :prefix-icon="UserFilled" size="large" /></el-form-item>
            <el-form-item prop="major"><el-input v-model="regForm.major" placeholder="专业（如：计算机科学与技术）" :prefix-icon="Reading" size="large" /></el-form-item>
            <el-form-item prop="className"><el-input v-model="regForm.className" placeholder="班级（如：计科2301）" :prefix-icon="School" size="large" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="regForm.password" type="password" placeholder="密码（6-20位）" show-password :prefix-icon="Lock" size="large" /></el-form-item>
            <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="register">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 职责：登录 / 注册页，成功写入登录态后跳转问答首页。
 * 所属页面：路由 /login。
 * 依赖接口：/auth/login、/auth/register（均经 request 封装提交）。
 * 全局状态：useAuthStore.setLogin 落库 token / role / 用户信息，供路由门禁与导航栏读取。
 * 约定：code!==200 与 401 由 request 拦截器统一拒绝并 ElMessage 提示，本页只处理表单校验与成功跳转。
 */
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, UserFilled, Reading, School } from '@element-plus/icons-vue'
import request from '../api/request'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const tab = ref('login')
const loading = ref(false)
const loginRef = ref(); const regRef = ref()
const loginForm = reactive({ studentNo: '', password: '' })
const regForm = reactive({ studentNo: '', username: '', major: '', className: '', password: '' })

const loginRules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }, { min: 4, max: 20, message: '学号长度需 4-20 位', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, max: 20, message: '密码长度需 6-20 位', trigger: 'blur' }],
}
const regRules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }, { min: 4, max: 20, message: '学号长度需 4-20 位', trigger: 'blur' }],
  username: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 6, max: 20, message: '密码长度需 6-20 位', trigger: 'blur' }],
}

/**
 * 登录：先做表单校验，失败直接返回；成功后 setLogin 写入登录态并跳转首页。
 * 副作用：loading 置位并在 finally 复位；code!==200 / 401 由 request 拦截器统一处理，本函数不重复提示。
 */
async function login() {
  try { await loginRef.value.validate() } catch { return }
  loading.value = true
  try {
    const res = await request.post('/auth/login', loginForm) as any
    if (res.code === 200) { auth.setLogin(res.data); router.push('/') }
  } catch { /* 错误已由请求拦截器统一提示 */ }
  finally { loading.value = false }
}

/**
 * 注册：先做表单校验，失败直接返回；成功后与登录一致地 setLogin 并跳转首页。
 * 副作用：loading 置位并在 finally 复位；错误提示由 request 拦截器统一负责。
 */
async function register() {
  try { await regRef.value.validate() } catch { return }
  loading.value = true
  try {
    const res = await request.post('/auth/register', regForm) as any
    if (res.code === 200) { auth.setLogin(res.data); router.push('/') }
  } catch { /* 错误已由请求拦截器统一提示 */ }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  padding: var(--cp-sp-4);
  background: var(--cp-login-bg);
  overflow: hidden;
  isolation: isolate;
}

/* 恢复原大屏背景后的遮罩层：深色渐隐 + 细网格，保证卡片文字清晰 */
.login-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(130% 100% at 50% 42%, transparent 30%, rgba(12, 10, 8, .42) 82%, rgba(12, 10, 8, .62) 100%),
    linear-gradient(rgba(255, 255, 255, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .035) 1px, transparent 1px),
    var(--cp-login-veil);
  background-size: auto, 34px 34px, 34px 34px, auto;
  pointer-events: none;
}

/* 双色光晕 + 斜向柔光带，仅用 transform 做缓慢漂移，制造纵深 */
.login-wrap::after {
  content: '';
  position: absolute;
  inset: -25%;
  background:
    radial-gradient(46rem 30rem at 14% 86%, rgba(234, 88, 12, .40), transparent 66%),
    radial-gradient(40rem 28rem at 88% 10%, rgba(66, 122, 178, .32), transparent 62%),
    linear-gradient(115deg, transparent 44%, rgba(255, 255, 255, .10) 50%, transparent 56%);
  filter: blur(20px);
  animation: cp-glow-drift 18s var(--cp-ease) infinite alternate;
  pointer-events: none;
}
@keyframes cp-glow-drift {
  from { transform: translate3d(-3%, 2%, 0) scale(1); }
  to   { transform: translate3d(3%, -2%, 0) scale(1.08); }
}

.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  max-width: 100%;
  background: var(--cp-login-card-bg) !important;
  backdrop-filter: blur(16px) saturate(1.25);
  border: 1px solid rgba(255, 255, 255, .55);
  border-radius: var(--cp-r-lg);
  box-shadow: var(--cp-shadow-3), 0 0 0 1px rgba(255, 255, 255, .45),
              0 20px 80px rgba(234, 88, 12, .22), inset 0 1px 0 rgba(255, 255, 255, .90);
  overflow: hidden;
  animation: cp-card-in var(--cp-dur-slow) var(--cp-ease) both;
}
@keyframes cp-card-in {
  from { opacity: 0; transform: translateY(20px) scale(.98); }
  to   { opacity: 1; transform: none; }
}
.login-card :deep(.el-card__body) { padding: var(--cp-sp-6) var(--cp-sp-6) var(--cp-sp-5); }

/* 品牌区错峰入场，只动 opacity / transform */
.brand { text-align: center; }
.brand > * { animation: cp-rise var(--cp-dur-slow) var(--cp-ease) both; }
.brand > :nth-child(1) { animation-delay: 120ms; }
.brand > :nth-child(2) { animation-delay: 200ms; }
.brand > :nth-child(3) { animation-delay: 280ms; }
.brand-logo {
  position: relative;
  z-index: 0;
  width: 56px;
  height: 56px;
  margin: 0 auto var(--cp-sp-3);
  border-radius: var(--cp-r-md);
  background: var(--cp-accent);
  color: var(--cp-on-accent);
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-2xl);
  font-weight: var(--cp-fw-bold);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--cp-shadow-2);
}
.brand-logo::after {
  content: '';
  position: absolute;
  inset: -10px;
  z-index: -1;
  border-radius: var(--cp-r-lg);
  background: radial-gradient(circle, rgba(234, 88, 12, .48), transparent 70%);
  animation: cp-logo-breathe 3.2s ease-in-out infinite alternate;
  pointer-events: none;
}
@keyframes cp-logo-breathe {
  from { opacity: .35; transform: scale(.9); }
  to   { opacity: .8; transform: scale(1.12); }
}
h2 {
  margin: 0 0 var(--cp-sp-1);
  text-align: center;
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-lg);
  font-weight: var(--cp-fw-bold);
  color: var(--cp-text-1);
  letter-spacing: .03em;
}
.sub { color: var(--cp-text-3); margin: 0 0 var(--cp-sp-5); font-size: var(--cp-fs-sm); text-align: center; letter-spacing: .08em; }

/* 单层输入框：透明底 + 唯一一层 1px 描边，聚焦只换色，不叠加高亮容器 */
.login-card :deep(.el-input__wrapper),
.login-card :deep(.el-input__wrapper:hover) {
  background-color: transparent;
  box-shadow: 0 0 0 1px var(--cp-border-strong) inset;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cp-accent) inset;
}
.login-card :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--cp-accent), var(--cp-accent-deep));
}
.login-card :deep(.el-button--primary) {
  background-image: linear-gradient(180deg, rgba(255, 255, 255, .14), rgba(255, 255, 255, 0) 55%);
  box-shadow: var(--cp-shadow-2);
}
.login-card :deep(.el-tabs__item) { font-size: var(--cp-fs-md); }
.login-card :deep(.el-tabs__nav-wrap) { margin-bottom: var(--cp-sp-4); }
.login-card :deep(.el-form-item) { margin-bottom: var(--cp-sp-4); }
.login-card :deep(.el-button--large) { height: 44px; font-size: var(--cp-fs-md); }

@media (max-width: 480px) {
  .login-card :deep(.el-card__body) { padding: var(--cp-sp-5) var(--cp-sp-4) var(--cp-sp-4); }
  .brand-logo { width: 48px; height: 48px; font-size: var(--cp-fs-xl); }
}
</style>