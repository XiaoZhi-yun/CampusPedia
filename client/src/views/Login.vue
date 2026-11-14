<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2>学事通 · CampusPedia</h2>
      <p class="sub">校园学生事务智能问答</p>
      <el-tabs v-model="tab">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginRef" @submit.prevent>
            <el-form-item prop="studentNo">
              <el-input v-model="loginForm.studentNo" placeholder="学号" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password @keyup.enter="login" />
            </el-form-item>
            <el-button type="primary" style="width:100%" :loading="loading" @click="login">登录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" :rules="regRules" ref="regRef" @submit.prevent>
            <el-form-item prop="studentNo"><el-input v-model="regForm.studentNo" placeholder="学号（4-20位）" /></el-form-item>
            <el-form-item prop="username"><el-input v-model="regForm.username" placeholder="姓名" /></el-form-item>
            <el-form-item prop="major"><el-input v-model="regForm.major" placeholder="专业（如：计算机科学与技术）" /></el-form-item>
            <el-form-item prop="className"><el-input v-model="regForm.className" placeholder="班级（如：计科2301）" /></el-form-item>
            <el-form-item prop="password"><el-input v-model="regForm.password" type="password" placeholder="密码（6-20位）" show-password /></el-form-item>
            <el-button type="primary" style="width:100%" :loading="loading" @click="register">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

async function login() {
  try { await loginRef.value.validate() } catch { return }
  loading.value = true
  try {
    const res = await request.post('/auth/login', loginForm) as any
    if (res.code === 200) { auth.setLogin(res.data); router.push('/') }
    else ElMessage.error(res.msg || '登录失败')
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '登录失败') }
  finally { loading.value = false }
}

async function register() {
  try { await regRef.value.validate() } catch { return }
  loading.value = true
  try {
    const res = await request.post('/auth/register', regForm) as any
    if (res.code === 200) { auth.setLogin(res.data); router.push('/') }
    else ElMessage.error(res.msg || '注册失败')
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '注册失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.login-card { width: 400px; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,.2); }
.login-card :deep(.el-card__body) { padding: 28px 28px 20px; }
h2 { margin: 0 0 4px; text-align: center; }
.sub { color: #999; margin: 0 0 16px; font-size: 13px; text-align: center; }
</style>
