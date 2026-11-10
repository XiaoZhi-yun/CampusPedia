<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">管理后台 · 数据大屏</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <div class="cards">
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#4f46e5">{{ ov.documents ?? '-' }}</div><div class="label">语料文档</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#0ea5e9">{{ ov.users ?? '-' }}</div><div class="label">注册用户</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#10b981">{{ ov.sessions ?? '-' }}</div><div class="label">会话数</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#f59e0b">{{ ov.messages ?? '-' }}</div><div class="label">消息数</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#ef4444">{{ ov.feedback ?? '-' }}</div><div class="label">反馈数</div></el-card>
      </div>

      <div class="charts">
        <el-card shadow="hover"><div ref="lineRef" style="height:320px"></div></el-card>
        <el-card shadow="hover"><div ref="pieRef" style="height:320px"></div></el-card>
      </div>

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
const lineRef = ref<HTMLElement | null>(null)
const pieRef = ref<HTMLElement | null>(null)

onMounted(async () => {
  try {
    const o = await request.get('/stats/overview') as any
    if (o.code === 200) ov.value = o.data || {}
  } catch {}
  try {
    const d = await request.get('/stats/daily') as any
    const rows = d.data || []
    if (lineRef.value) {
      const chart = echarts.init(lineRef.value)
      chart.setOption({
        title: { text: '近 7 天问答量', left: 'center', textStyle: { fontSize: 15 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 50, bottom: 30 },
        xAxis: { type: 'category', data: rows.map((r: any) => r.day) },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{ type: 'line', data: rows.map((r: any) => r.count), smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#4f46e5' } }],
      })
    }
  } catch {}
  try {
    const t = await request.get('/stats/docTypes') as any
    const rows = t.data || []
    if (pieRef.value) {
      const chart = echarts.init(pieRef.value)
      chart.setOption({
        title: { text: '语料类型分布', left: 'center', textStyle: { fontSize: 15 } },
        tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
        legend: { bottom: 0 },
        series: [{ type: 'pie', radius: ['35%', '62%'], center: ['50%', '45%'], data: rows.map((r: any) => ({ name: r.type || '未知', value: r.count })) }],
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
.cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; }
.stat-card { text-align: center; }
.num { font-size: 28px; font-weight: 700; }
.label { font-size: 13px; color: #999; margin-top: 4px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
</style>
