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
  loading.value = true
  try {
    const res = await request.post('/auth/login', { username: username.value, password: password.value }) as any
    if (res.code === 200) {
      auth.setLogin(res.data.token, res.data.username, res.data.role)
      router.push('/')
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
  try {
    const res = await request.post('/auth/register', { username: username.value, password: password.value }) as any
    if (res.code === 200) {
      auth.setLogin(res.data.token, res.data.username, res.data.role)
      router.push('/')
    } else {
      msg.value = res.msg || '注册失败'
    }
  } catch (e: any) {
    msg.value = e?.response?.data?.msg || '注册失败'
  }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; }
.login-card { width: 360px; text-align: center; }
.sub { color: #999; margin: 4px 0 16px; font-size: 13px; }
.msg { color: #e74c3c; font-size: 13px; }
</style>
