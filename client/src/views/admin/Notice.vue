<template>
  <div class="admin-page">
    <header class="topbar"><div class="brand">公告管理</div><div><el-button text @click="$router.push('/')">返回问答</el-button></div></header>
    <div class="content">
      <el-card>
        <div style="margin-bottom:12px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">
          <el-input v-model="form.title" placeholder="公告标题" style="width:280px" />
          <el-input v-model="form.content" placeholder="公告内容" style="width:400px" />
          <el-button type="primary" @click="save">发布</el-button>
        </div>
        <el-table :data="list" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="content" label="内容" />
          <el-table-column prop="createTime" label="时间" width="180" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }"><el-button size="small" type="danger" @click="del(row.id)">删除</el-button></template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import request from '../../api/request'
const list = ref<any[]>([])
const form = reactive({ title: '', content: '' })
async function load() { const r = await request.get('/notice') as any; if (r.code === 200) list.value = r.data || [] }
async function save() {
  if (!form.title.trim()) return
  await request.post('/notice', { title: form.title, content: form.content })
  form.title = ''; form.content = ''; load()
}
async function del(id: number) { await request.delete('/notice/' + id); load() }
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; max-width: 1200px; margin: 0 auto; }
</style>
