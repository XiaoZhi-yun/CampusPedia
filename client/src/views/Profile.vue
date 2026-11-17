<template>
  <div class="profile-page">
    <NavBar />
    <div class="content">
      <!-- 基本信息卡：编辑资料、选择头像与修改密码入口 -->
      <el-card class="info-card">
        <h3>基本信息</h3>
        <div class="avatar-row">
          <div class="avatar" :style="{ background: AVATAR_COLORS[auth.avatar] || AVATAR_COLORS.p1 }">{{ auth.username ? auth.username[0] : '学' }}</div>
        </div>
        <el-form label-width="80px" style="margin-top:16px">
          <el-form-item label="学号"><el-input v-model="form.studentNo" disabled /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.username" /></el-form-item>
          <el-form-item label="专业"><el-input v-model="form.major" /></el-form-item>
          <el-form-item label="班级"><el-input v-model="form.className" /></el-form-item>
        </el-form>
        <h3 style="margin-top:20px">选择头像</h3>
        <div class="avatar-options">
          <div v-for="(c, k) in AVATAR_COLORS" :key="k" class="avatar-opt" :class="{ active: form.avatar === k }" :style="{ background: c }" @click="form.avatar = k">{{ auth.username ? auth.username[0] : '学' }}</div>
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

      <!-- 会话记录卡：点击跳回问答页对应会话，支持批量勾选删除 -->
      <el-card class="session-card">
        <div class="session-head">
          <h3>我的会话记录（{{ sessions.length }}）</h3>
          <template v-if="!batchMode">
            <el-button size="small" @click="batchMode = true">批量删除</el-button>
          </template>
          <template v-else>
            <el-button size="small" @click="cancelBatch">取消</el-button>
          </template>
        </div>
        <div v-if="batchMode" class="batch-bar">
          <el-checkbox :model-value="sel.length === sessions.length && sessions.length > 0" @change="toggleAll">全选</el-checkbox>
          <el-button size="small" type="danger" :disabled="!sel.length" @click="batchDelete">确认删除（{{ sel.length }}）</el-button>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
        <div v-for="s in sessions" :key="s.id" class="session-item" @click="$router.push({ path: '/', query: { sessionId: s.id } })">
          <el-checkbox v-if="batchMode" :model-value="sel.includes(s.id)" @click.stop @change="toggleSel(s.id)" />
          <span class="stitle">{{ s.title }}</span>
          <span class="scount">{{ s.messageCount || 0 }} 条消息</span>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 职责：个人中心——编辑资料与头像、修改密码、查看并批量删除会话记录。
 * 所属页面：路由 /profile。
 * 依赖接口：/sessions、/profile（GET 读取 / PUT 保存）、/profile/password（改密）。
 * 全局状态：useAuthStore；保存成功后同步 Pinia 与 sessionStorage，供路由门禁和头像读取。
 * 布局：两张卡片撑满视口高度，左资料、右会话记录。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
import request from '../api/request'

const auth = useAuthStore()
const router = useRouter()
const sessions = ref<any[]>([])
const sel = ref<number[]>([])
const batchMode = ref(false)
const pwdVisible = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const form = reactive({ studentNo: auth.studentNo, username: auth.username, major: auth.major, className: auth.className, avatar: auth.avatar })

const AVATAR_COLORS: Record<string, string> = {
  p1: '#ea580c', p2: '#d97706', p3: '#dc2626', p4: '#b45309',
  p5: '#a16207', p6: '#64748b', p7: '#7c2d12', p8: '#c2410c',
}

/**
 * 并行拉取会话列表与个人资料，两段各自 try/catch 容错。
 * 资料为空时保留 useAuthStore 初始值，避免表单被清空。
 */
async function load() {
  try { const r = await request.get('/sessions') as any; if (r.code === 200) sessions.value = r.data || [] } catch {}
  try {
    const p = await request.get('/profile') as any
    if (p.code === 200 && p.data) {
      form.username = p.data.username || ''; form.major = p.data.major || ''
      form.className = p.data.className || ''; form.avatar = p.data.avatar || 'p1'
    }
  } catch {}
}

function cancelBatch() { batchMode.value = false; sel.value = [] }
function toggleAll(val: boolean) { sel.value = val ? sessions.value.map((s: any) => s.id) : [] }
function toggleSel(id: number) {
  const i = sel.value.indexOf(id)
  if (i >= 0) sel.value.splice(i, 1); else sel.value.push(id)
}
/**
 * 批量删除选中会话：二次确认后逐个 DELETE，清空选择、退出批量模式并 reload。
 * 错误由 request 拦截器统一提示。
 */
async function batchDelete() {
  try { await ElMessageBox.confirm('确定删除选中的 ' + sel.value.length + ' 个会话吗？', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  for (const id of sel.value) await request.delete('/sessions/' + id)
  sel.value = []; batchMode.value = false; load(); ElMessage.success('已删除')
}
async function changePwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { ElMessage.warning('请填写完整'); return }
  try {
    const r = await request.put('/profile/password', pwdForm) as any
    if (r.code === 200) { ElMessage.success('密码已修改，请重新登录'); pwdVisible.value = false; auth.logout(); router.push('/login') }
  } catch { /* 错误已由请求拦截器统一提示 */ }
}

/**
 * 保存个人资料与头像。
 * 成功后同步 useAuthStore 与 sessionStorage（username / major / className / avatar），
 * 使路由门禁读取的展示信息与头像配色在刷新后仍一致；错误由拦截器提示。
 */
async function save() {
  try {
    const r = await request.put('/profile', { username: form.username, major: form.major, className: form.className, avatar: form.avatar }) as any
    if (r.code === 200) {
      auth.username = form.username; auth.major = form.major; auth.className = form.className; auth.avatar = form.avatar
      sessionStorage.setItem('username', form.username); sessionStorage.setItem('major', form.major)
      sessionStorage.setItem('className', form.className); sessionStorage.setItem('avatar', form.avatar)
      ElMessage.success('已保存')
    }
  } catch {}
}

onMounted(load)
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
.content {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: var(--cp-sp-4);
  align-items: stretch;
  min-height: calc(100vh - var(--cp-header-h));
  padding-bottom: var(--cp-sp-5);
}
.info-card, .session-card { display: flex; flex-direction: column; min-height: 0; }
.info-card :deep(.el-card__body) { display: flex; flex-direction: column; flex: 1; }
/* 操作按钮沉到卡片底部，让纵向空间被均匀使用 */
.info-card :deep(.el-card__body > .el-button:nth-last-child(2)) { margin-top: auto !important; }
.info-card :deep(.el-form) { margin-top: var(--cp-sp-4); }
.avatar-row { text-align: center; margin-bottom: var(--cp-sp-2); }
.avatar {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  color: #fff;
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-2xl);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 3px solid var(--cp-card-bg);
  box-shadow: var(--cp-shadow-2);
}
.avatar-options { display: flex; gap: var(--cp-sp-3); flex-wrap: wrap; }
.avatar-opt {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 3px solid transparent;
  box-shadow: var(--cp-shadow-1);
  transition: transform var(--cp-dur-fast) ease-out, border-color var(--cp-dur-fast) ease-out;
}
.avatar-opt:hover { transform: translateY(-2px); }
.avatar-opt.active { border-color: var(--cp-accent); box-shadow: var(--cp-focus-ring); }
.session-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--cp-sp-3); }
.session-head h3 { margin: 0; padding: 0; border: none; }
.batch-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--cp-sp-2) var(--cp-sp-3);
  background: var(--cp-source-bg);
  border: 1px solid var(--cp-border-soft);
  border-radius: var(--cp-r-sm);
  margin-bottom: var(--cp-sp-3);
}
.session-card :deep(.el-card__body) { flex: 1; overflow-y: auto; }
.session-item {
  display: flex;
  align-items: center;
  gap: var(--cp-sp-3);
  padding: var(--cp-sp-3);
  border-bottom: 1px solid var(--cp-border-soft);
  border-radius: var(--cp-r-sm);
  cursor: pointer;
  font-size: var(--cp-fs-base);
  color: var(--cp-text-2);
  transition: background-color var(--cp-dur-fast) ease-out, color var(--cp-dur-fast) ease-out;
}
.session-item:last-child { border-bottom: none; }
.session-item:hover { background: var(--cp-hover-bg); color: var(--cp-emphasis); }
.stitle { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scount { color: var(--cp-text-3); font-size: var(--cp-fs-xs); white-space: nowrap; }

@media (max-width: 900px) {
  .content { grid-template-columns: minmax(0, 1fr); min-height: auto; }
  .session-card :deep(.el-card__body) { max-height: 60vh; }
}
</style>