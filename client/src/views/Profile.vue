<template>
  <div class="profile-page">
    <header class="topbar">
      <div class="brand">个人中心</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <el-card class="info-card">
        <h3>基本信息</h3>
        <div class="avatar-row">
          <div class="avatar" :style="{ background: AVATAR_COLORS[auth.avatar] || AVATAR_COLORS.p1 }">{{ auth.realName ? auth.realName[0] : '学' }}</div>
        </div>
        <el-form label-width="80px" style="margin-top:16px">
          <el-form-item label="学号"><el-input v-model="form.studentNo" disabled /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="班级"><el-input v-model="form.className" /></el-form-item>
        </el-form>
        <h3 style="margin-top:20px">选择头像</h3>
        <div class="avatar-options">
          <div v-for="(c, k) in AVATAR_COLORS" :key="k" class="avatar-opt" :class="{ active: form.avatar === k }" :style="{ background: c }" @click="form.avatar = k">{{ auth.realName ? auth.realName[0] : '学' }}</div>
        </div>
        <el-button type="primary" style="margin-top:20px" @click="save">保存</el-button>
        <el-button style="margin-top:20px" @click="pwdVisible = true">修改密码</el-button>
      </el-card>

    <el-dialog v-model="pwdVisible" title="修改密码" width="360px">
      <el-form label-width="80px">
        <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="changePwd">确定</el-button>
      </template>
    </el-dialog>

      <el-card class="session-card">
        <h3>我的会话记录（{{ sessions.length }}）</h3>
        <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
        <div v-for="s in sessions" :key="s.id" class="session-item" @click="$router.push('/')">
          <span class="stitle">{{ s.title }}</span>
          <span class="scount">{{ s.messageCount || 0 }} 条消息</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import request from '../api/request'

const auth = useAuthStore()
const sessions = ref<any[]>([])
const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const form = reactive({ studentNo: auth.studentNo, realName: auth.realName, major: auth.major, className: auth.className, avatar: auth.avatar })

const AVATAR_COLORS: Record<string, string> = {
  p1: '#4f46e5', p2: '#0ea5e9', p3: '#10b981', p4: '#f59e0b',
  p5: '#ef4444', p6: '#8b5cf6', p7: '#ec4899', p8: '#14b8a6',
}

async function load() {
  try { const r = await request.get('/sessions') as any; if (r.code === 200) sessions.value = r.data || [] } catch {}
  try {
    const p = await request.get('/profile') as any
    if (p.code === 200 && p.data) {
      form.realName = p.data.realName || ''; form.major = p.data.major || ''
      form.className = p.data.className || ''; form.avatar = p.data.avatar || 'p1'
    }
  } catch {}
}

async function changePwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { ElMessage.warning('请填写完整'); return }
  try {
    const r = await request.put('/profile/password', pwdForm) as any
    if (r.code === 200) { ElMessage.success('密码已修改'); pwdVisible.value = false; pwdForm.oldPassword = ''; pwdForm.newPassword = '' }
    else ElMessage.error(r.msg || '修改失败')
  } catch (e: any) { ElMessage.error(e?.response?.data?.msg || '修改失败') }
}

async function save() {
  try {
    const r = await request.put('/profile', { realName: form.realName, major: form.major, className: form.className, avatar: form.avatar }) as any
    if (r.code === 200) {
      auth.realName = form.realName; auth.major = form.major; auth.className = form.className; auth.avatar = form.avatar
      localStorage.setItem('realName', form.realName); localStorage.setItem('major', form.major)
      localStorage.setItem('className', form.className); localStorage.setItem('avatar', form.avatar)
      ElMessage.success('已保存')
    }
  } catch {}
}

onMounted(load)
</script>

<style scoped>
.profile-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; display: flex; gap: 16px; flex-wrap: wrap; }
.info-card { width: 420px; }
.session-card { flex: 1; min-width: 300px; }
.avatar-row { text-align: center; }
.avatar { width: 64px; height: 64px; border-radius: 50%; color: #fff; font-size: 26px; display: inline-flex; align-items: center; justify-content: center; }
.avatar-options { display: flex; gap: 10px; flex-wrap: wrap; }
.avatar-opt { width: 40px; height: 40px; border-radius: 50%; color: #fff; display: flex; align-items: center; justify-content: center; cursor: pointer; border: 3px solid transparent; }
.avatar-opt.active { border-color: #333; }
.session-item { display: flex; justify-content: space-between; padding: 10px; border-bottom: 1px solid #f0f0f0; cursor: pointer; font-size: 14px; }
.session-item:hover { background: #f5f6f8; }
.scount { color: #999; font-size: 12px; }
</style>
