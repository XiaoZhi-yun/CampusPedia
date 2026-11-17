<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">用户管理</h2>
      <el-card>
        <!-- 用户列表：展示角色，提供编辑资料、提权/降权与删除 -->
        <el-table :data="users" size="small">
          <el-table-column type="index" label="序号" width="55" align="center" />
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="studentNo" label="学号" width="140" />
          <el-table-column prop="username" label="姓名" width="100" />
          <el-table-column prop="major" label="专业" min-width="130" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" style="width:80px;display:inline-flex;align-items:center;justify-content:center">{{ row.role === 'admin' ? '管理员' : '普通用户' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="290" align="center">
            <template #default="{ row }">
              <div style="display:flex;gap:6px;justify-content:center;white-space:nowrap">
              <el-button size="small" style="width:72px" @click="edit(row)">编辑</el-button>
              <el-button size="small" style="width:96px" @click="toggleRole(row)">{{ row.role === 'admin' ? '设为普通用户' : '提权管理员' }}</el-button>
              <el-button size="small" type="danger" style="width:64px" @click="del(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 编辑弹窗：仅改姓名 / 专业 / 班级，角色变更走列表内的独立按钮 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="400px">
      <el-form label-width="70px">
        <el-form-item label="姓名"><el-input v-model="editForm.username" /></el-form-item>
        <el-form-item label="专业"><el-input v-model="editForm.major" /></el-form-item>
        <el-form-item label="班级"><el-input v-model="editForm.className" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 职责：用户管理——列表展示、编辑资料、提权 / 降权与删除。
 * 所属页面：路由 /admin/users。
 * 依赖接口：/admin/users（GET 列表、PUT 改资料 / 角色、DELETE 删用户）。
 * 全局状态：useAuthStore 用于判断被删的是否为当前登录者；若删除自己则登出并跳登录页。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { useAuthStore } from '../../stores/auth'
const router = useRouter()
const auth = useAuthStore()
const users = ref<any[]>([])
const editVisible = ref(false)
const editForm = reactive({ id: 0, username: '', major: '', className: '' })
/**
 * 拉取用户列表填充 users；仅 code===200 时覆盖。
 */
async function load() { const r = await request.get('/admin/users') as any; if (r.code === 200) users.value = r.data || [] }
function edit(row: any) { editForm.id = row.id; editForm.username = row.username; editForm.major = row.major; editForm.className = row.className; editVisible.value = true }
async function saveEdit() { await request.put('/admin/users/' + editForm.id, { username: editForm.username, major: editForm.major, className: editForm.className }); editVisible.value = false; load() }
async function toggleRole(row: any) { await request.put('/admin/users/' + row.id, { role: row.role === 'admin' ? 'user' : 'admin' }); load() }
/**
 * 删除用户：二次确认后 DELETE，并级联清除其会话与聊天记录。
 * 参数 row：表格当前行；若被删的是当前登录者（比对 studentNo），删除后登出并跳登录页，否则仅 reload。
 */
async function del(row: any) {
  try { await ElMessageBox.confirm('确定删除用户「' + (row.username || row.studentNo) + '」吗？将同时删除该用户的会话和聊天记录，不可恢复。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  const isSelf = row.studentNo === auth.studentNo
  await request.delete('/admin/users/' + row.id)
  if (isSelf) { auth.logout(); router.push('/login') } else { load() }
}
onMounted(load)
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
/* 用户资料可能较长，单元格内允许换行以免溢出 */
:deep(.el-table .cell) { word-break: break-word; }
:deep(.el-tag) { font-weight: var(--cp-fw-semi); letter-spacing: .02em; }
</style>
