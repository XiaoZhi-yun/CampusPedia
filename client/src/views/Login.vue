<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>学事通 · CampusPedia</h2>
      <p class="sub">校园学生事务智能问答</p>
      <el-form @submit.prevent>
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password @keyup.enter="login" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="login">登录</el-button>
        <el-button text style="width:100%;margin-top:8px" @click="register">没有账号？注册</el-button>
      </el-form>
      <p v-if="msg" class="msg">{{ msg }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const loading = ref(false)
const msg = ref('')

async function login() {
  if (!username.value || !password.value) { msg.value = '请输入用户名和密码'; return }
  if (username.value.length < 3 || username.value.length > 20) { msg.value = '用户名长度需 3-20 位'; return }
  if (password.value.length < 6 || password.value.length > 20) { msg.value = '密码长度需 6-20 位'; return }
  loading.value = true
  try {
    const res = await request.post('/auth/login', { username: username.value, password: password.value }) as any
    if (res.code === 200) {
      auth.setLogin(res.data.token, res.data.username, res.data.role)
      router.push(res.data.role === 'admin' ? '/admin' : '/')
    } else {
      msg.value = res.msg || '登录失败'
    }
  } catch (e: any) {
    msg.value = e?.response?.data?.msg || '登录失败'
  } finally {
    loading.value = false
  }
}

async function register() {
  if (!username.value || !password.value) { msg.value = '请输入用户名和密码'; return }
  if (username.value.length < 3 || username.value.length > 20) { msg.value = '用户名长度需 3-20 位'; return }
  if (password.value.length < 6 || password.value.length > 20) { msg.value = '密码长度需 6-20 位'; return }
  try {
    const res = await request.post('/auth/register', { username: username.value, password: password.value }) as any
    if (res.code === 200) {
      auth.setLogin(res.data.token, res.data.username, res.data.role)
      router.push(res.data.role === 'admin' ? '/admin' : '/')
    } else {
      msg.value = res.msg || '注册失败'
    }
  } catch (e: any) {
    msg.value = e?.response?.data?.msg || '注册失败'
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.login-card { width: 380px; text-align: center; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,.2); }
.login-card :deep(.el-card__body) { padding: 32px 28px; }
.sub { color: #999; margin: 4px 0 20px; font-size: 13px; }
.msg { color: #e74c3c; font-size: 13px; margin-top: 8px; }
</style>
