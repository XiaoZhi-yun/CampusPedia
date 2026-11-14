<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">反馈管理</h2>
      <el-card>
        <el-table :data="list" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="userId" label="用户ID" width="80" />
          <el-table-column prop="question" label="问题" min-width="180" show-overflow-tooltip />
          <el-table-column label="评分" width="90">
            <template #default="{ row }"><el-tag :type="row.rating === 1 ? 'success' : 'danger'">{{ row.rating === 1 ? '👍 有用' : '👎 无用' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="comment" label="评语" />
          <el-table-column prop="createTime" label="时间" width="180" />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../api/request'
const list = ref<any[]>([])
async function load() { const r = await request.get('/feedback') as any; if (r.code === 200) list.value = r.data || [] }
onMounted(load)
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
