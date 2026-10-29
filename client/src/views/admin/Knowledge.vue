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
          <input type="file" ref="fileRef" accept=".md,.txt,.pdf,.docx" @change="onFile" />
          <el-input v-model="sourceUrl" placeholder="官网原文链接（可选）" style="width:280px" />
          <el-button type="primary" :disabled="!file || busy" @click="upload">{{ busy ? '上传中…' : '上传' }}</el-button>
        </div>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </el-card>

      <el-card style="margin-top:16px">
        <h3>文档列表（{{ docs.length }}）</h3>
        <el-table :data="docs" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="fileName" label="文件名" />
          <el-table-column prop="vectorStatus" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.vectorStatus === 'done' ? 'success' : 'warning'">{{ row.vectorStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="切片数" width="80" />
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="vectorize(row.id)">重新向量化</el-button>
              <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
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

const docs = ref<any[]>([])
const file = ref<File | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const sourceUrl = ref('')
const busy = ref(false)
const msg = ref('')

function onFile(e: any) { file.value = e.target.files?.[0] || null }

async function load() {
  const res = await request.get('/kb/documents') as any
  if (res.code === 200) docs.value = res.data || []
}

async function upload() {
  if (!file.value) { msg.value = '请选择文件'; return }
  busy.value = true
  const fd = new FormData()
  fd.append('file', file.value)
  fd.append('categoryId', '0')
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

onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
.upload-row { display: flex; gap: 10px; align-items: center; margin-top: 8px; }
.msg { margin-top: 8px; font-size: 13px; }
</style>
