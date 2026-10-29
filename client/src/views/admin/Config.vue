<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">参数配置</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <el-card>
        <el-table :data="configs" size="small">
          <el-table-column prop="configKey" label="配置键" width="240" />
          <el-table-column prop="configValue" label="值">
            <template #default="{ row }">
              <el-input v-model="row.configValue" size="small" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" />
        </el-table>
        <el-button type="primary" style="margin-top:12px" @click="save">保存</el-button>
        <p v-if="msg">{{ msg }}</p>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../api/request'

const configs = ref<any[]>([])
const msg = ref('')

async function load() {
  const res = await request.get('/config') as any
  if (res.code === 200) configs.value = res.data || []
}

async function save() {
  const res = await request.put('/config', configs.value) as any
  msg.value = res.code === 200 ? '✅ 已保存（实时生效）' : '❌ 保存失败'
}

onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
