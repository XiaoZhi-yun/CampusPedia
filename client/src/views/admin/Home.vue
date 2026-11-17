<template>
  <div class="admin-page">
    <NavBar />
    <div class="content">
      <h2 class="page-title">数据大屏</h2>
      <p class="page-sub">校园问答运营概览</p>

      <!-- 运营概览：文档 / 用户 / 会话 / 消息四项总量指标 -->
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

      <!-- 图表区：近 7 天问答量折线与语料类型分布，由 ECharts 在挂载后渲染 -->
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
/**
 * 职责：数据大屏——运营概览统计卡、近 7 天问答量、语料类型分布与热门问题 TOP10。
 * 所属页面：路由 /admin（管理后台首页）。
 * 依赖接口：/stats/overview、/stats/daily、/stats/topQuestions、/stats/docTypes。
 * 依赖库：ECharts，在 onMounted 拉取数据后于两个 ref 容器上 init 并 setOption。
 * 全局状态：无 Pinia 依赖；各请求独立 try/catch，单项失败不影响其余区块。
 */
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { Files, User, ChatDotRound, Message, Document, Setting, UserFilled, Bell } from '@element-plus/icons-vue'
import request from '../../api/request'

const ov = ref<any>({})
const hot = ref<any[]>([])
const lineRef = ref<HTMLElement | null>(null)
const pieRef = ref<HTMLElement | null>(null)

/**
 * 挂载后依次拉取四组统计数据并渲染图表。
 * overview / daily / topQuestions / docTypes 各自 try/catch，单项失败只影响对应区块。
 * 图表仅在 lineRef / pieRef 容器就绪时 echarts.init 并 setOption，避免空容器报错。
 */
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
.admin-page {
  min-height: 100vh;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
.page-title { margin-bottom: var(--cp-sp-1); }
.page-sub { margin: 0 0 var(--cp-sp-5) var(--cp-sp-3); color: var(--cp-text-3); font-size: var(--cp-fs-sm); }

.cards { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--cp-sp-4); }
.stat-card { text-align: left; position: relative; overflow: hidden; }
/* 每张统计卡左侧的书脊，与页面标题呼应 */
.stat-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  background: var(--cp-accent);
}
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: var(--cp-sp-4); padding: var(--cp-sp-5); }
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--cp-r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid var(--cp-border-soft);
  /* 覆盖模板内联的 4 种强调色，收敛为单一品牌色体系 */
  background: var(--cp-emphasis-soft) !important;
  color: var(--cp-emphasis) !important;
}
.stat-meta { min-width: 0; }
.num {
  font-family: var(--cp-font-numeric);
  font-size: var(--cp-fs-2xl);
  font-weight: var(--cp-fw-bold);
  line-height: 1.05;
  letter-spacing: -.02em;
  font-variant-numeric: tabular-nums;
  color: var(--cp-text-1) !important;
}
.label { font-size: var(--cp-fs-sm); color: var(--cp-text-3); margin-top: var(--cp-sp-1); letter-spacing: .06em; }

.charts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--cp-sp-4); margin-top: var(--cp-sp-4); }
.chart-card { min-width: 0; }
.chart-head {
  position: relative;
  margin: 0 0 var(--cp-sp-4);
  padding-left: var(--cp-sp-3);
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-md);
  font-weight: var(--cp-fw-semi);
  color: var(--cp-text-1);
}
.chart-head::before {
  content: '';
  position: absolute;
  left: 0; top: 3px; bottom: 3px;
  width: 3px;
  border-radius: 2px;
  background: var(--cp-accent);
}
.chart-box { height: 300px; }
.quick-actions { margin-top: var(--cp-sp-4); display: flex; gap: var(--cp-sp-3); flex-wrap: wrap; }
.qa-ico { margin-right: var(--cp-sp-1); }

@media (max-width: 1080px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) { .charts { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 520px) {
  .cards { grid-template-columns: minmax(0, 1fr); }
  .chart-box { height: 240px; }
  .stat-card :deep(.el-card__body) { padding: var(--cp-sp-4); }
}
</style>