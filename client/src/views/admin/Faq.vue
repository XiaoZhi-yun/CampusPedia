<template>
  <div class="admin-page">
    <header class="topbar"><div class="brand">FAQ 常见问题</div><div><el-button text @click="$router.push('/')">返回问答</el-button></div></header>
    <div class="content">
      <el-card>
        <div style="margin-bottom:12px;display:flex;gap:8px">
          <el-input v-model="form.question" placeholder="问题" style="width:280px" />
          <el-input v-model="form.answer" placeholder="答案" style="width:400px" />
          <el-button type="primary" @click="save">{{ editingId ? '更新' : '新增' }}</el-button>
          <el-button v-if="editingId" @click="reset">取消</el-button>
        </div>
        <el-table :data="list" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="question" label="问题" />
          <el-table-column prop="answer" label="答案" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="edit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="del(row.id)">删除</el-button>
            </template>
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
const editingId = ref<number | null>(null)
const form = reactive({ question: '', answer: '' })
async function load() { const r = await request.get('/faq') as any; if (r.code === 200) list.value = r.data || [] }
async function save() {
  if (!form.question.trim()) return
  if (editingId.value) await request.put('/faq', { id: editingId.value, question: form.question, answer: form.answer })
  else await request.post('/faq', { question: form.question, answer: form.answer })
  reset(); load()
}
function edit(row: any) { editingId.value = row.id; form.question = row.question; form.answer = row.answer }
function reset() { editingId.value = null; form.question = ''; form.answer = '' }
async function del(id: number) { await request.delete('/faq/' + id); load() }
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
