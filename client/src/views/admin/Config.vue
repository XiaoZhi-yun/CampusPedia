<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">参数配置</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <el-card>
        <el-table :data="configs" size="small">
          <el-table-column label="配置项" width="220">
            <template #default="{ row }">{{ keyName(row.configKey) }}</template>
          </el-table-column>
          <el-table-column label="值">
            <template #default="{ row }">
              <el-switch v-if="isBool(row.configKey)" v-model="row.configValue" active-value="true" inactive-value="false" />
              <el-input v-else v-model="row.configValue" size="small" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" />
        </el-table>
        <el-button type="primary" style="margin-top:12px" @click="save">保存</el-button>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../api/request'

const configs = ref<any[]>([])
const msg = ref('')

const KEY_NAMES: Record<string, string> = {
  rag_top_k_recall: '混合召回条数',
  rag_top_n_final: '最终保留条数',
  rag_score_threshold: '相似度阈值',
  rerank_enabled: '启用 Rerank',
  hybrid_enabled: '启用混合检索',
  query_rewrite_enabled: '启用 Query 改写',
  temperature: 'LLM 温度',
  history_limit: '带入历史条数',
  chunk_size: '切片大小',
  chunk_overlap: '切片重叠',
  system_prompt: '系统提示词',
}
const BOOL_KEYS = ['rerank_enabled', 'hybrid_enabled', 'query_rewrite_enabled']

function keyName(k: string) { return KEY_NAMES[k] || k }
function isBool(k: string) { return BOOL_KEYS.includes(k) }

async function load() {
  const res = await request.get('/config') as any
  if (res.code === 200) configs.value = res.data || []
}
async function save() {
  const res = await request.put('/config', configs.value) as any
  msg.value = res.code === 200 ? '✅ 已保存（实时生效）' : '❌ 保存失败：' + (res.msg || '')
}
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
.msg { margin-top: 8px; font-size: 13px; }
</style>
