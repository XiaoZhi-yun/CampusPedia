<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
      <h2 class="page-title">公告管理</h2>
      <el-card>
        <div style="margin-bottom:12px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">
          <el-input v-model="form.title" placeholder="公告标题" style="width:280px" />
          <el-input v-model="form.content" placeholder="公告内容" style="width:400px" />
          <el-button type="primary" @click="save">{{ editingId ? '更新' : '发布' }}</el-button>
          <el-button v-if="editingId" @click="reset">取消</el-button>
        </div>
        <el-table :data="list" size="small">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
          <el-table-column prop="createTime" label="创建时间" width="180" align="center" />
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button size="small" @click="edit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="del(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
const list = ref<any[]>([])
const form = reactive({ title: '', content: '' })
const editingId = ref<number | null>(null)
async function load() { const r = await request.get('/notice') as any; if (r.code === 200) list.value = r.data || [] }
async function save() {
  if (!form.title.trim()) { ElMessage.warning('请填写标题'); return }
  if (editingId.value) await request.put('/notice', { id: editingId.value, title: form.title, content: form.content })
  else await request.post('/notice', { title: form.title, content: form.content })
  reset(); load()
}
function edit(row: any) { editingId.value = row.id; form.title = row.title; form.content = row.content }
function reset() { editingId.value = null; form.title = ''; form.content = '' }
async function del(row: any) {
  try { await ElMessageBox.confirm('确定删除公告「' + row.title + '」吗？', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  await request.delete('/notice/' + row.id); load()
}
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.content { padding: 20px; }
</style>
