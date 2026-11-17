<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
      <h2 class="page-title">公告管理</h2>
      <el-card>
        <!-- 发布 / 编辑表单：editingId 决定按钮文案与 PUT / POST 分支 -->
        <div style="margin-bottom:12px;display:flex;gap:8px;justify-content:center;flex-wrap:wrap">
          <el-input v-model="form.title" placeholder="公告标题" style="width:280px" />
          <el-input v-model="form.content" placeholder="公告内容" style="width:400px" />
          <el-button type="primary" @click="save">{{ editingId ? '更新' : '发布' }}</el-button>
          <el-button v-if="editingId" @click="reset">取消</el-button>
        </div>
        <!-- 公告列表：逐行编辑或删除，标题与内容超长省略 -->
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
/**
 * 职责：公告的发布、编辑、删除与列表维护。
 * 所属页面：路由 /admin/notice。
 * 依赖接口：/notice（GET 列表、POST 新增、PUT 更新、DELETE 删除）。
 * 上下游：问答页 Chat.vue 挂载时通过 GET /notice 读取本页维护的公告，展示在消息区顶部横幅。
 * 全局状态：无 Pinia 依赖。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
const list = ref<any[]>([])
const form = reactive({ title: '', content: '' })
const editingId = ref<number | null>(null)
/**
 * 拉取公告列表填充 list；仅 code===200 时覆盖。
 */
async function load() { const r = await request.get('/notice') as any; if (r.code === 200) list.value = r.data || [] }
/**
 * 发布或更新公告：标题为空则提示并中止。
 * editingId 存在时 PUT 更新，否则 POST 新增；随后 reset 表单并 reload。
 * 错误由 request 拦截器统一提示。
 */
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
.admin-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
:deep(.el-table .cell) { word-break: break-word; }

@media (max-width: 768px) {
  /* 表单行内联宽度 280px / 400px 在窄屏溢出，仅在 CSS 层覆盖 */
  :deep(.el-card__body .el-input) { width: 100% !important; }
}
</style>
