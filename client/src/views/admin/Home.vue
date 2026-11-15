<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
      <h2 class="page-title">数据大屏</h2>
      <p class="page-sub">校园问答运营概览</p>

      <div class="cards">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background:#fff7ed;color:#ea580c"><el-icon :size="22"><Files /></el-icon></div>
          <div class="stat-meta">
            <div class="num" style="color:#ea580c">{{ ov.documents ?? '-' }}</div>
            <div class="label">语料文档</div>
          </div>
        </el-card>
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background:#fef2f2;color:#dc2626"><el-icon :size="22"><User /></el-icon></div>
          <div class="stat-meta">
            <div class="num" style="color:#dc2626">{{ ov.users ?? '-' }}</div>
            <div class="label">注册用户</div>
          </div>
        </el-card>
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background:#fffbeb;color:#d97706"><el-icon :size="22"><ChatDotRound /></el-icon></div>
          <div class="stat-meta">
            <div class="num" style="color:#d97706">{{ ov.sessions ?? '-' }}</div>
            <div class="label">会话数</div>
          </div>
        </el-card>
        <el-card class="stat-card" shadow="hover">
          <div class="stat-icon" style="background:#f7f3ee;color:#7c2d12"><el-icon :size="22"><Message /></el-icon></div>
          <div class="stat-meta">
            <div class="num" style="color:#7c2d12">{{ ov.messages ?? '-' }}</div>
            <div class="label">消息数</div>
          </div>
        </el-card>
      </div>

      <div class="charts">
        <el-card shadow="hover" class="chart-card">
          <div class="chart-head">近 7 天问答量</div>
          <div ref="lineRef" class="chart-box"></div>
        </el-card>
        <el-card shadow="hover" class="chart-card">
          <div class="chart-head">语料类型分布</div>
          <div ref="pieRef" class="chart-box"></div>
        </el-card>
      </div>

      <el-card shadow="hover" style="margin-top:16px">
        <div class="chart-head">热门问题 TOP10</div>
        <el-table :data="hot" size="small">
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="question" label="问题" />
          <el-table-column prop="count" label="提问次数" width="120" align="center">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.count }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <div class="quick-actions">
        <el-button type="primary" @click="$router.push('/admin/knowledge')"><el-icon class="qa-ico"><Document /></el-icon>语料管理</el-button>
        <el-button @click="$router.push('/admin/config')"><el-icon class="qa-ico"><Setting /></el-icon>参数配置</el-button>
        <el-button @click="$router.push('/admin/users')"><el-icon class="qa-ico"><UserFilled /></el-icon>用户管理</el-button>
        <el-button @click="$router.push('/admin/notice')"><el-icon class="qa-ico"><Bell /></el-icon>公告发布</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { Files, User, ChatDotRound, Message, Document, Setting, UserFilled, Bell } from '@element-plus/icons-vue'
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
      const chart = echarts.init(lineRef.value)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 30 },
        xAxis: { type: 'category', data: rows.map((r: any) => r.day), boundaryGap: false, axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#9ca3af' } },
        yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f1f5f9' } }, axisLabel: { color: '#9ca3af' } },
        series: [{
          type: 'line',
          data: rows.map((r: any) => r.count),
          smooth: true,
          symbol: 'circle',
          symbolSize: 7,
          lineStyle: { width: 3, color: '#ea580c' },
          itemStyle: { color: '#ea580c', borderColor: '#fff', borderWidth: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(234, 88, 12, .22)' },
              { offset: 1, color: 'rgba(234, 88, 12, .02)' },
            ]),
          },
        }],
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
      const chart = echarts.init(pieRef.value)
      chart.setOption({
        color: ['#ea580c', '#d97706', '#dc2626', '#7c2d12', '#b45309', '#64748b', '#a16207'],
        tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
        legend: { bottom: 0, icon: 'circle', textStyle: { color: '#6b7280' } },
        series: [{
          type: 'pie',
          radius: ['38%', '64%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
          label: { show: false },
          emphasis: { label: { show: true, fontWeight: 700 } },
          data: rows.map((r: any) => ({ name: r.type || '未知', value: r.count })),
        }],
      })
    }
  } catch {}
})
</script>

<style scoped>
.admin-page { min-height: 100vh; background: #faf7f2; }
.content { padding: 24px; }
.page-title { margin-bottom: 4px; }
.page-sub { margin: 0 0 20px 12px; color: #6b7280; font-size: 13px; }
.cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { text-align: left; }
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; padding: 20px; }
.stat-icon { width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.num { font-size: 28px; font-weight: 700; line-height: 1.1; }
.label { font-size: 13px; color: #9ca3af; margin-top: 4px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-top: 16px; }
.chart-head { font-size: 15px; font-weight: 600; color: #111827; margin: 0 0 12px; padding-left: 10px; position: relative; }
.chart-head::before { content: ''; position: absolute; left: 0; top: 2px; bottom: 2px; width: 3px; border-radius: 3px; background: #ea580c; }
.chart-box { height: 320px; }
.quick-actions { margin-top: 16px; display: flex; gap: 12px; flex-wrap: wrap; }
.qa-ico { margin-right: 6px; }
@media (max-width: 768px) {
  .cards { grid-template-columns: repeat(2, 1fr); }
  .charts { grid-template-columns: 1fr; }
}
</style>