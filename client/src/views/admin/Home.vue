<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">管理后台 · 数据大屏</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <div class="cards">
        <el-card class="stat-card"><div class="num">{{ ov.documents ?? '-' }}</div><div class="label">语料文档</div></el-card>
        <el-card class="stat-card"><div class="num">{{ ov.sessions ?? '-' }}</div><div class="label">会话数</div></el-card>
        <el-card class="stat-card"><div class="num">{{ ov.messages ?? '-' }}</div><div class="label">消息数</div></el-card>
        <el-card class="stat-card"><div class="num">{{ ov.feedback ?? '-' }}</div><div class="label">反馈数</div></el-card>
      </div>
      <el-card style="margin-top:16px">
        <div ref="chartRef" style="height:320px"></div>
      </el-card>
      <div style="margin-top:16px;display:flex;gap:12px">
        <el-button type="primary" @click="$router.push('/admin/knowledge')">语料管理</el-button>
        <el-button @click="$router.push('/admin/config')">参数配置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

const ov = ref<any>({})
const chartRef = ref<HTMLElement | null>(null)

onMounted(async () => {
  try {
    const o = await request.get('/stats/overview') as any
    if (o.code === 200) ov.value = o.data || {}
  } catch {}
  try {
    const d = await request.get('/stats/daily') as any
    const rows = d.data || []
    const days = rows.map((r: any) => r.day)
    const counts = rows.map((r: any) => r.count)
    if (chartRef.value) {
      const chart = echarts.init(chartRef.value)
      chart.setOption({
        title: { text: '近 7 天问答量', left: 'center' },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: days },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{ type: 'line', data: counts, smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#4f46e5' } }],
      })
    }
  } catch {}
})
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { text-align: center; }
.num { font-size: 28px; font-weight: 700; color: #4f46e5; }
.label { font-size: 13px; color: #999; margin-top: 4px; }
</style>
