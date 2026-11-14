<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">用户管理</h2>
      <el-card>
        <el-table :data="users" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="studentNo" label="学号" width="140" />
          <el-table-column prop="realName" label="姓名" width="100" />
          <el-table-column prop="major" label="专业" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">
              <el-tag :type="row.role === 'admin' ? 'danger' : 'info'">{{ row.role === 'admin' ? '管理员' : '普通用户' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="toggleRole(row)">{{ row.role === 'admin' ? '设为普通用户' : '提权管理员' }}</el-button>
              <el-button size="small" type="danger" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../api/request'
const users = ref<any[]>([])
async function load() { const r = await request.get('/admin/users') as any; if (r.code === 200) users.value = r.data || [] }
async function toggleRole(row: any) { await request.put('/admin/users/' + row.id, { role: row.role === 'admin' ? 'user' : 'admin' }); load() }
async function toggleStatus(row: any) { await request.put('/admin/users/' + row.id, { status: row.status === 1 ? 0 : 1 }); load() }
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
