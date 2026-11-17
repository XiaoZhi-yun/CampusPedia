<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">参数配置</h2>
      <el-card>
        <!-- 参数表：布尔项渲染为开关、其余为输入框，编辑后统一保存 -->
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
        <el-button style="margin-top:12px" @click="resetDefaults">恢复默认值</el-button>
        <p v-if="msg" class="msg">{{ msg }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 职责：RAG / LLM 运行参数的可视化配置与「恢复默认值」。
 * 所属页面：路由 /admin/config。
 * 依赖接口：/config（GET 读取整表、PUT 整表提交，后端实时生效）。
 * 约定：KEY_NAMES 仅做展示名映射，BOOL_KEYS 决定哪些项渲染为开关；DEFAULTS 只用于恢复默认值按钮。
 */
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

/**
 * 读取运行参数整表填充 configs；仅 code===200 时覆盖。
 */
async function load() {
  const res = await request.get('/config') as any
  if (res.code === 200) configs.value = res.data || []
}
const DEFAULTS: Record<string, string> = {
  rag_top_k_recall: '20', rag_top_n_final: '5', rag_score_threshold: '0.3',
  rerank_enabled: 'true', hybrid_enabled: 'true', query_rewrite_enabled: 'true',
  temperature: '0.3', history_limit: '6', chunk_size: '500', chunk_overlap: '80',
  system_prompt: '你是学事通CampusPedia，只依据参考资料回答，资料不足明说，结论后标[1][2]',
}
function resetDefaults() {
  for (const c of configs.value) { if (DEFAULTS[c.configKey] !== undefined) c.configValue = DEFAULTS[c.configKey] }
  save()
}

/**
 * 整表 PUT 提交当前 configs，后端实时生效。
 * 结果以 msg 反馈成功 / 失败文案；错误由 request 拦截器统一提示。
 */
async function save() {
  const res = await request.put('/config', configs.value) as any
  msg.value = res.code === 200 ? '✅ 已保存（实时生效）' : '❌ 保存失败：' + (res.msg || '')
}
onMounted(load)
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
/* 参数值 / 系统提示词可能很长，允许单元格内换行以免撑破布局 */
:deep(.el-table .cell) { word-break: break-word; }
:deep(.el-switch) { vertical-align: middle; }

@media (max-width: 600px) {
  :deep(.el-table .el-input__inner) { font-size: var(--cp-fs-xs); }
}
</style>
