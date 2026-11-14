<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">语料管理</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <el-card>
        <h3>上传文档</h3>
        <div class="upload-row">
          <input type="file" ref="fileRef" accept=".md,.txt,.pdf,.doc,.docx" @change="onFile" />
          <el-select v-model="categoryId" placeholder="请选择语料分类" size="default" style="width:180px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            <el-option label="其他" :value="0" />
          </el-select>
          <el-input v-model="sourceUrl" placeholder="官网原文链接（可选）" style="width:280px" />
          <el-button type="primary" :disabled="!file || busy" @click="upload">{{ busy ? '上传中…' : '上传' }}</el-button>
          <el-button @click="clearForm">清除</el-button>
        </div>
        <p class="type-hint">支持文件类型：.md / .txt / .pdf / .doc / .docx</p>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </el-card>

      <el-card style="margin-top:16px">
        <div style="margin:12px 0;display:flex;gap:8px;align-items:center">
          <span style="font-size:13px;color:#666">按分类筛选：</span>
          <el-select v-model="filterCat" placeholder="全部" size="small" style="width:160px" clearable>
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            <el-option label="其他" :value="0" />
          </el-select>
        </div>
        <h3>文档列表（{{ filteredDocs.length }}）
        <span style="float:right">
          <el-button size="small" :disabled="!sel.length" @click="batchVectorize">批量向量化（{{ sel.length }}）</el-button>
          <el-button size="small" type="danger" :disabled="!sel.length" @click="batchDelete">批量删除（{{ sel.length }}）</el-button>
        </span>
      </h3>
        <el-table :data="filteredDocs" size="small" @selection-change="(s: any) => sel = s">
          <el-table-column type="selection" width="40" />
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="fileName" label="文件名" min-width="200" />
          <el-table-column label="分类" width="140"><template #default="{ row }">{{ catName(row.categoryId) }}</template></el-table-column>
          <el-table-column prop="vectorStatus" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.vectorStatus === 'done' ? 'success' : 'warning'">{{ row.vectorStatus === 'done' ? '已入库' : '待处理' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="切片数" width="90" align="center" />
          <el-table-column label="操作" width="270" align="center">
            <template #default="{ row }">
              <el-button size="small" @click="preview(row)">预览切片</el-button>
              <el-button size="small" @click="vectorize(row.id)">重新向量化</el-button>
              <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="chunksVisible" title="切片预览" width="720px">
      <div v-for="c in chunks" :key="c.id" class="chunk-item">
        <div class="chunk-head">#{{ c.chunkIndex }} <span v-if="c.clauseRef" class="clause">{{ c.clauseRef }}</span>（{{ c.charCount }} 字）</div>
        <div class="chunk-body">{{ c.content }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '../../api/request'
import { ElMessage } from 'element-plus'

const docs = ref<any[]>([])
const categories = ref<any[]>([])
const sel = ref<any[]>([])
const chunks = ref<any[]>([])
const chunksVisible = ref(false)
const categoryId = ref<number>(0)
const filterCat = ref<number | null>(null)
const filteredDocs = computed(() => {
  if (filterCat.value === null || filterCat.value === undefined) return docs.value
  return docs.value.filter((d: any) => d.categoryId === filterCat.value)
})
const file = ref<File | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const sourceUrl = ref('')
const busy = ref(false)
const msg = ref('')

function onFile(e: any) { file.value = e.target.files?.[0] || null }
function clearForm() { file.value = null; if (fileRef.value) fileRef.value.value = ''; sourceUrl.value = ''; categoryId.value = 0 }

async function loadCats() { try { const r = await request.get('/kb/categories') as any; if (r.code === 200) categories.value = r.data || [] } catch {} }
function catName(id: any) { const c = categories.value.find((x: any) => x.id === id); return c ? c.name : '-' }
async function batchVectorize() {
  await request.post('/kb/documents/batch-vectorize', { ids: sel.value.map((r: any) => r.id) })
  ElMessage.success('已重新向量化 ' + sel.value.length + ' 篇')
  load()
}
async function preview(row: any) {
  const r = await request.get('/kb/documents/' + row.id + '/chunks') as any
  chunks.value = r.data || []
  chunksVisible.value = true
}
async function batchDelete() {
  for (const row of sel.value) await request.delete('/kb/documents/' + row.id)
  load()
}
async function load() {
  const res = await request.get('/kb/documents') as any
  if (res.code === 200) docs.value = res.data || []
}

async function upload() {
  if (!file.value) { msg.value = '请选择文件'; return }
  busy.value = true
  const fd = new FormData()
  fd.append('file', file.value)
  fd.append('categoryId', String(categoryId.value))
  if (sourceUrl.value.trim()) fd.append('sourceUrl', sourceUrl.value.trim())
  try {
    const res = await request.post('/kb/documents', fd) as any
    if (res.code === 200) {
      msg.value = '✅ 已上传《' + res.data.fileName + '》，切片 ' + res.data.chunkCount + ' 块'
      file.value = null; sourceUrl.value = ''
      if (fileRef.value) fileRef.value.value = ''
      load()
    } else { msg.value = '❌ ' + (res.msg || '上传失败') }
  } catch (e: any) { msg.value = '❌ ' + (e?.response?.data?.msg || '上传失败') }
  finally { busy.value = false }
}

async function vectorize(id: number) {
  await request.post('/kb/documents/' + id + '/vectorize')
  load()
}

async function remove(row: any) {
  if (!confirm('确定删除《' + row.fileName + '》？')) return
  await request.delete('/kb/documents/' + row.id)
  load()
}

onMounted(() => { load(); loadCats() })
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
.upload-row { display: flex; gap: 10px; align-items: center; margin-top: 8px; justify-content: center; flex-wrap: wrap; }
.msg { margin-top: 8px; font-size: 13px; }
.type-hint { margin-top: 8px; font-size: 12px; color: #999; }
.chunk-item { border: 1px solid #f0f0f0; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; }
.chunk-head { font-size: 12px; color: #999; margin-bottom: 4px; }
.chunk-head .clause { color: #4f46e5; }
.chunk-body { font-size: 13px; line-height: 1.6; white-space: pre-wrap; }
</style>
