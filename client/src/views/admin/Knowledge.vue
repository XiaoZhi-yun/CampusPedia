<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">语料管理</h2>
      <el-card>
        <h3>上传文档</h3>
        <!-- 上传面板：选择文档、指定语料分类与官网原文链接后提交入库 -->
        <div class="upload-panel">
          <div class="upload-line">
            <input type="file" ref="fileRef" accept=".md,.txt,.pdf,.doc,.docx" style="display:none" @change="onFile" />
            <el-button @click="fileRef?.click()">📎 选择文件</el-button>
            <span v-if="file" class="file-name" :title="file.name">{{ file.name }}</span>
            <span v-else class="file-name placeholder">未选择文件</span>
          </div>
          <div class="upload-line">
            <el-select v-model="categoryId" placeholder="请选择语料分类" size="default" style="width:200px">
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
            <el-input v-model="sourceUrl" placeholder="官网原文链接（可选）" style="width:320px" />
            <el-button type="primary" :disabled="!file || busy" :loading="busy" @click="upload">{{ busy ? '上传中…' : '上传' }}</el-button>
            <el-button @click="clearForm">清除</el-button>
          </div>
        </div>
        <p class="type-hint">支持文件类型：.md / .txt / .pdf / .doc / .docx</p>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </el-card>

      <!-- 文档列表：按分类筛选、批量向量化 / 删除，单行可预览切片或重向量化 -->
      <el-card style="margin-top:16px">
        <div style="margin:12px 0;display:flex;gap:8px;align-items:center">
          <span style="font-size:13px;color:#666">按分类筛选：</span>
          <el-select v-model="filterCat" placeholder="全部" size="small" style="width:160px" clearable>
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
            <el-option v-if="hasUncategorized" label="未分类（旧数据）" :value="0" />
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
/**
 * 职责：语料管理——文档上传、分类筛选、向量化、删除与切片预览。
 * 所属页面：路由 /admin/knowledge（管理后台，仅管理员可达）。
 * 依赖接口：/kb/categories（分类唯一来源）、/kb/documents 及 batch-vectorize、:id/chunks、:id/vectorize 子路径。
 * 约定：分类完全由 /kb/categories 驱动，「其他」是库里 id=7 的真实分类；历史 category_id=0 的旧数据需兼容展示。
 * 全局状态：不直接依赖 Pinia；登录 / 角色由 router.beforeEach 前端门禁保证，真正鉴权在后端。
 */
import { ref, computed, onMounted } from 'vue'
import request from '../../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const docs = ref<any[]>([])
const categories = ref<any[]>([])
const sel = ref<any[]>([])
const chunks = ref<any[]>([])
const chunksVisible = ref(false)
const categoryId = ref<number>(0)
const filterCat = ref<number | null>(null)
const filteredDocs = computed(() => {
  if (filterCat.value === null || filterCat.value === undefined) return docs.value
  return docs.value.filter((d: any) => Number(d.categoryId) === Number(filterCat.value))
})
/** 「其他」是 t_kb_category 里的真实分类，作为上传默认值；接口未返回时回落 0。 */
const otherCategoryId = computed(() => {
  const c = categories.value.find((x: any) => x.name === '其他')
  return c ? Number(c.id) : 0
})
/** 历史 category_id=0 的文档仍可单独筛出，避免旧数据突然消失。 */
const hasUncategorized = computed(() => docs.value.some((d: any) => Number(d.categoryId) === 0))
const file = ref<File | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const sourceUrl = ref('')
const busy = ref(false)
const msg = ref('')

function onFile(e: any) { file.value = e.target.files?.[0] || null }
function clearForm() { file.value = null; if (fileRef.value) fileRef.value.value = ''; sourceUrl.value = ''; categoryId.value = otherCategoryId.value }

async function loadCats() {
  try {
    const r = await request.get('/kb/categories') as any
    if (r.code === 200) {
      categories.value = r.data || []
      if (categoryId.value === 0) categoryId.value = otherCategoryId.value
    }
  } catch {}
}
function catName(id: any) {
  if (id === null || id === undefined || Number(id) === 0) return '其他'
  const c = categories.value.find((x: any) => Number(x.id) === Number(id))
  return c ? c.name : '其他'
}
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
/**
 * 批量删除选中文档（含其切片与向量）。
 * 先二次确认，用户取消则直接返回；逐个发 DELETE 后 reload；错误由 request 拦截器统一提示。
 */
async function batchDelete() {
  try { await ElMessageBox.confirm('确定删除选中的 ' + sel.value.length + ' 篇文档吗？删除后相关切片和向量将一并移除。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  for (const row of sel.value) await request.delete('/kb/documents/' + row.id)
  load()
}
/**
 * 拉取全部语料文档，供表格与分类筛选使用；仅在 code===200 时覆盖 docs。
 */
async function load() {
  const res = await request.get('/kb/documents') as any
  if (res.code === 200) docs.value = res.data || []
}

/**
 * 上传文档并即时入库：以 FormData 携带文件、分类 id 与可选官网原文链接。
 * 副作用：成功后清空文件 / 链接输入、重置 file input、以 msg 展示切片数并 reload 列表。
 * 异常：接口非 200 或抛错时把后端 msg 回显到 msg，finally 复位 busy。
 */
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

/**
 * 删除单篇文档（含切片与向量）：确认后 DELETE 并 reload。
 * 参数 row：表格当前行；取消确认则直接返回。
 */
async function remove(row: any) {
  try { await ElMessageBox.confirm('确定删除《' + row.fileName + '》吗？删除后相关切片和向量将一并移除。', '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  await request.delete('/kb/documents/' + row.id)
  load()
}

onMounted(() => { load(); loadCats() })
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
.upload-panel { margin-top: var(--cp-sp-4); display: flex; flex-direction: column; gap: var(--cp-sp-3); }
.upload-line { display: flex; align-items: center; gap: var(--cp-sp-3); flex-wrap: wrap; }
.file-name { font-size: var(--cp-fs-sm); color: var(--cp-text-2); word-break: break-all; }
.file-name.placeholder { color: var(--cp-text-3); }
.type-hint { margin: var(--cp-sp-3) 0 0; font-size: var(--cp-fs-xs); color: var(--cp-text-3); }
.chunk-item {
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-r-sm);
  padding: var(--cp-sp-3);
  margin-bottom: var(--cp-sp-2);
  background: var(--cp-card-bg);
  max-height: 260px;
  overflow-y: auto;
}
.chunk-head { font-size: var(--cp-fs-xs); color: var(--cp-text-3); margin-bottom: var(--cp-sp-2); font-variant-numeric: tabular-nums; }
.chunk-head .clause { color: var(--cp-emphasis); margin-left: var(--cp-sp-1); }
.chunk-body { font-size: var(--cp-fs-sm); line-height: var(--cp-lh-body); white-space: pre-wrap; color: var(--cp-text-2); word-break: break-word; }
:deep(.el-dialog__body) { max-height: 65vh; overflow-y: auto; }

@media (max-width: 768px) {
  /* 模板内联宽度在窄屏会溢出，仅在 CSS 层覆盖，不动 template */
  .upload-line :deep(.el-select), .upload-line :deep(.el-input) { width: 100% !important; }
  .upload-line > :deep(.el-button) { flex: 1 1 auto; }
}
</style>