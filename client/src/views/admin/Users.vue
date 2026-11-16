<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">用户管理</h2>
      <el-card>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '../../api/request'
const users = ref<any[]>([])
const editVisible = ref(false)
const editForm = reactive({ id: 0, username: '', major: '', className: '' })
async function load() { const r = await request.get('/admin/users') as any; if (r.code === 200) users.value = r.data || [] }
function edit(row: any) { editForm.id = row.id; editForm.username = row.username; editForm.major = row.major; editForm.className = row.className; editVisible.value = true }
async function saveEdit() { await request.put('/admin/users/' + editForm.id, { username: editForm.username, major: editForm.major, className: editForm.className }); editVisible.value = false; load() }
async function toggleRole(row: any) { await request.put('/admin/users/' + row.id, { role: row.role === 'admin' ? 'user' : 'admin' }); load() }
async function del(row: any) {
  try { await ElMessageBox.confirm('确定删除用户「' + (row.username || row.studentNo) + '」吗？该操作不可恢复。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  await request.delete('/admin/users/' + row.id); load()
}
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
