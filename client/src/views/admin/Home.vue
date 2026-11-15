<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
    <h2 class="page-title">数据大屏</h2>
      <div class="cards">
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#2563eb">{{ ov.documents ?? '-' }}</div><div class="label">语料文档</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#0ea5e9">{{ ov.users ?? '-' }}</div><div class="label">注册用户</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#10b981">{{ ov.sessions ?? '-' }}</div><div class="label">会话数</div></el-card>
        <el-card class="stat-card" shadow="hover"><div class="num" style="color:#f59e0b">{{ ov.messages ?? '-' }}</div><div class="label">消息数</div></el-card>
              </div>

      <div class="charts">
        <el-card shadow="hover" style="background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12)"><div ref="lineRef" style="height:320px"></div></el-card>
        <el-card shadow="hover" style="background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12)"><div ref="pieRef" style="height:320px"></div></el-card>
      </div>

      <el-card shadow="hover" style="margin-top:16px;background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.12)">
        <h3 style="margin-top:0;color:#e2e8f0">热门问题 TOP10</h3>
        <el-table :data="hot" size="small">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="question" label="问题" />
          <el-table-column prop="count" label="提问次数" width="100" />
        </el-table>
      </el-card>
      <div style="margin-top:16px;display:flex;gap:12px;flex-wrap:wrap">
        <el-button type="primary" @click="$router.push('/admin/knowledge')">语料管理</el-button>
        <el-button @click="$router.push('/admin/config')">参数配置</el-button>
        <el-button @click="$router.push('/admin/users')">用户管理</el-button>
        <el-button @click="$router.push('/admin/faq')">FAQ 管理</el-button>
        <el-button @click="$router.push('/admin/notice')">公告发布</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

const ov = ref<any>({})
const hot = ref<any[]>([])
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
      const chart = echarts.init(lineRef.value, 'dark')
      chart.setOption({
        title: { text: '近 7 天问答量', left: 'center', textStyle: { fontSize: 15 } },
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 50, bottom: 30 },
        xAxis: { type: 'category', data: rows.map((r: any) => r.day) },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{ type: 'line', data: rows.map((r: any) => r.count), smooth: true, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#2563eb' } }],
      })
    }
  } catch {}
  try {
    const h = await request.get('/stats/topQuestions') as any
    if (h.code === 200) hot.value = h.data || []
  } catch {}
  try {
    const t = await request.get('/stats/docTypes') as any
    const rows = t.data || []
    if (pieRef.value) {
      const chart = echarts.init(pieRef.value, 'dark')
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
.admin-page { min-height: 100vh; background: radial-gradient(ellipse 80% 55% at 50% -10%, rgba(37,99,235,.35), transparent), radial-gradient(ellipse 55% 45% at 100% 100%, rgba(6,182,212,.25), transparent), #0b1220; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; } .page-title { color: #e2e8f0; }
.content { padding: 20px; }
.cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; }
.stat-card { text-align: center; background: rgba(255,255,255,.07); border: 1px solid rgba(255,255,255,.12); border-radius: 14px; }
.num { font-size: 30px; font-weight: 700; color: #60a5fa; }
.label { font-size: 13px; color: #94a3b8; margin-top: 4px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
</style>
