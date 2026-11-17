<template>
  <div class="chat-page">
    <NavBar />

    <div class="main">
      <!-- 会话侧栏：登录后展示，集中承载新建、搜索、重命名与删除会话 -->
      <aside v-if="auth.token" class="sidebar">
        <div class="sidebar-head">
          <el-button type="primary" size="small" style="width:100%" @click="newSession">＋ 新对话</el-button>
        </div>
        <div class="sidebar-search">
          <el-input v-model="sessionKw" size="small" placeholder="搜索会话" clearable />
        </div>
        <div class="session-list">
          <div v-for="s in filteredSessions" :key="s.id" class="session-item" :class="{ active: s.id === currentSessionId }" @click="openSession(s.id)">
            <span class="stitle">{{ s.title }}</span>
            <span class="sops">
              <el-icon class="sop" @click.stop="renameSession(s)"><Edit /></el-icon>
              <el-icon class="sop del" @click.stop="confirmDel(s)"><Delete /></el-icon>
            </span>
          </div>
          <div v-if="filteredSessions.length === 0" class="empty">暂无会话</div>
        </div>
      </aside>

      <div class="chat-area">
        <div v-if="notices.length" class="notice-banner">
          <span v-for="n in notices" :key="n.id" class="notice-item">📢 <b>{{ n.title }}</b>：{{ n.content }}</span>
        </div>
        <!-- 消息区：空态欢迎页与逐条消息；助手答案走 v-html，附操作条与参考来源 -->
        <div class="msg-list" ref="listRef">
          <div v-if="messages.length === 0" class="welcome">
            <div class="welcome-logo">通</div>
            <h2 class="welcome-title">你好，我是学事通</h2>
            <p class="welcome-sub">依据学校真实资料回答，杜绝"一本正经胡说八道"</p>
            <div v-if="hot.length" class="hint-btns">
              <span class="hint-title">猜你想问</span>
              <el-button v-for="h in hot" :key="h.question" class="suggest-btn" @click="send(h.question)">{{ h.question }}</el-button>
            </div>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="avatar" :style="m.role === 'user' ? { background: AVATAR_COLORS[auth.avatar] || AVATAR_COLORS.p1 } : {}">{{ m.role === 'user' ? (auth.username ? auth.username[0] : '我') : '通' }}</div>
            <div class="body">
              <div class="bubble" @click="onCiteClick($event)">
                <div v-if="m.role === 'assistant' && m.content" v-html="m.html"></div>
                <div v-else-if="m.role === 'assistant' && loading" class="thinking">思考中…</div>
                <div v-else>{{ m.content }}</div>
              </div>

              <div v-if="m.role === 'assistant' && m.content && !loading" class="op-bar">
                <el-button size="small" text @click="copy(m)">复制</el-button>
                <el-button size="small" text @click="regenerate(m)">重新生成</el-button>
                <el-button size="small" text @click="speak(m)">{{ speaking === m ? '停止朗读' : '朗读' }}</el-button>
              </div>

              <el-collapse v-if="m.sources && m.sources.length" class="sources">
                <el-collapse-item :title="'📎 参考来源（' + m.sources.length + ' 条）'" name="1">
                  <div v-for="g in groupedSources(m.sources)" :key="g.fileName + g.clauseRef" class="src" :ref="'src-' + g.indices[0]">
                    <div class="src-head">
                      <span class="src-no">{{ fmtIndices(g.indices) }}</span>
                      <a v-if="g.sourceUrl" :href="g.sourceUrl" target="_blank">《{{ g.fileName }}》</a>
                      <span v-else class="src-name">《{{ g.fileName }}》</span>
                      <span v-if="g.clauseRef" class="clause">{{ g.clauseRef }}</span>
                    </div>
                    <div class="src-snippet">{{ g.snippet }}</div>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>

        <!-- 输入栏：生成期间禁用输入，并把发送按钮切换为「停止」 -->
        <div class="input-bar">
          <el-input v-model="input" placeholder="输入你的校园问题…" :disabled="loading" @keyup.enter="send()" />
          <el-button v-if="loading" type="danger" @click="stop()">停止</el-button>
          <el-button v-else type="primary" :disabled="!input.trim()" @click="send()">发送</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 职责：流式问答主页面——会话目录、SSE 流式答案渲染、引用来源卡片与输入交互。
 * 所属页面：路由 '/'；支持 ?sessionId= 从个人中心直达指定会话。
 * 依赖接口：/sessions（会话增删改查）、/sessions/:id/messages（历史消息）、
 *           /api/chat/stream（原生 fetch 读取 SSE，因需流式而不走 request 封装）、
 *           /stats/topQuestions（猜你想问）、/notice（顶部公告横幅）。
 * 全局状态：useAuthStore 读取 token 与用户头像配色；路由仅用于读取 sessionId 查询参数。
 */
import { ref, nextTick, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import request from '../api/request'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const md = new MarkdownIt()
const messages = ref<any[]>([])
const sessions = ref<any[]>([])
const hot = ref<any[]>([])
const notices = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const sessionKw = ref('')
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)
let abortCtrl: AbortController | null = null
const dark = ref(false)
const AVATAR_COLORS: Record<string, string> = { p1: '#ea580c', p2: '#d97706', p3: '#dc2626', p4: '#b45309', p5: '#a16207', p6: '#64748b', p7: '#7c2d12', p8: '#c2410c' }
function toggleDark() { dark.value = !dark.value; document.documentElement.classList.toggle('dark', dark.value) }

const filteredSessions = computed(() => {
  const kw = sessionKw.value.trim()
  if (!kw) return sessions.value
  return sessions.value.filter((s: any) => (s.title || '').includes(kw))
})

function logout() { auth.logout(); router.push('/login') }

function renderAnswer(content: string) {
  const html = md.render(content)
  // 只在标签外的文本片段替换 [N]，避免命中 href 等属性内的数字方括号
  return html.replace(/(^|>)([^<]*)/g, (_all: string, lead: string, text: string) =>
    lead + text.replace(/\[(?:来源)?(\d+)\]/g, (all: string, n: string) => `<span class="cite" data-idx="${n}">${all}</span>`))
}

/**
 * 拉取当前用户的会话列表并覆盖 sessions。
 * 未登录（无 token）时直接跳过；失败静默，侧栏允许保持空态。
 */
async function loadSessions() {
  if (!auth.token) return
  try { const res = await request.get('/sessions') as any; if (res.code === 200) sessions.value = res.data || [] } catch {}
}
async function loadHot() {
  try { const r = await request.get('/stats/topQuestions') as any; if (r.code === 200) hot.value = (r.data || []).slice(0, 3) } catch {}
}
async function loadNotices() {
  try { const r = await request.get('/notice') as any; if (r.code === 200) notices.value = r.data || [] } catch {}
}

/**
 * 新建会话并切换为当前会话。
 * 前置约束：先刷新列表，若最新会话尚无消息则提示先在当前框对话并拒绝重复建空会话。
 * 副作用：unshift 新会话、置 currentSessionId、清空消息区；失败由 request 拦截器统一提示。
 */
async function newSession() {
  if (auth.token) {
    try {
      const res = await request.get('/sessions') as any
      if (res.code === 200) sessions.value = res.data || []
    } catch {}
  }
  const latest = sessions.value[0]
  if (latest && (latest.messageCount == null || latest.messageCount === 0)) { ElMessage.info('请在当前对话框进行对话'); return }
  try {
    const res = await request.post('/sessions', { title: '新对话' }) as any
    if (res.code === 200) { sessions.value.unshift(res.data); currentSessionId.value = res.data.id; messages.value = [] }
  } catch {}
}

async function renameSession(s: any) {
  try {
    const { value } = await ElMessageBox.prompt('重命名会话', '重命名', { inputValue: s.title, confirmButtonText: '保存', cancelButtonText: '取消' })
    if (value && value.trim()) { await request.put('/sessions/' + s.id, { title: value.trim() }); s.title = value.trim() }
  } catch {}
}

/**
 * 切换到指定会话并加载其历史消息。
 * 参数 id：会话 id，来自侧栏点击或路由 ?sessionId=。
 * 副作用：先置 currentSessionId，再把历史消息映射为渲染所需结构（助手消息经 renderAnswer 生成 html）。
 */
async function openSession(id: number) {
  currentSessionId.value = id
  try {
    const res = await request.get('/sessions/' + id + '/messages') as any
    if (res.code === 200) {
      messages.value = (res.data || []).map((m: any) => {
        const content = m.role === 'assistant' ? dedupeCitations(m.content || '') : (m.content || '')
        const item: any = {
          role: m.role, content,
          html: m.role === 'assistant' ? renderAnswer(content) : '',
          sources: m.sources ? safeJson(m.sources) : [], trace: null, fb: null,
        }
        // 历史消息与实时消息用同一套过滤，避免「历史 5 条来源、新答 2 条」的不一致
        if (m.role === 'assistant') filterCitedSources(item)
        return item
      })
      scrollBottom()
    }
  } catch {}
}

async function confirmDel(s: any) {
  try {
    await ElMessageBox.confirm('是否删除：对话「' + s.title + '」？', '删除确认', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    await request.delete('/sessions/' + s.id)
    sessions.value = sessions.value.filter(x => x.id !== s.id)
    if (currentSessionId.value === s.id) { currentSessionId.value = null; messages.value = [] }
  } catch {}
}

function safeJson(s: string) { try { return JSON.parse(s) } catch { return [] } }

/**
 * 发送问题并消费 SSE 流式答案，是本页核心流程。
 * 参数 q：可选的预置问题（猜你想问 / 重新生成）；缺省取输入框内容。
 * 流程：无会话时先建会话；随后以原生 fetch 读取 /api/chat/stream，按空行分包、归一化 CRLF，
 *       连接关闭时冲刷尾部残余帧；期间若切换 / 删除会话导致 aiIdx 失配则立即停止写入。
 * 异常：非 AbortError 写入错误文案；AbortError（用户点「停止」）保留已生成内容并补占位文案。
 * 收尾：过滤未被正文引用的来源、复位 loading 与 abortCtrl、滚动到底。
 */
async function send(q?: string) {
  const question = (q || input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  loading.value = true
  const history = messages.value.slice(-6).map(m => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: question, html: '', sources: [], trace: null, fb: null })
  messages.value.push({ role: 'assistant', content: '', html: '', sources: [], trace: null, fb: null, question })
  const aiIdx = messages.value.length - 1

  if (auth.token && !currentSessionId.value) {
    try {
      const res = await request.post('/sessions', { title: question.slice(0, 20) }) as any
      if (res.code === 200) { currentSessionId.value = res.data.id; sessions.value.unshift(res.data) }
    } catch {}
  } else if (auth.token && currentSessionId.value) {
    const cur = sessions.value.find((s: any) => s.id === currentSessionId.value)
    if (cur && (!cur.title || cur.title === '新对话')) cur.title = question.slice(0, 20)
  }

  abortCtrl = new AbortController()
  try {
    const token = sessionStorage.getItem('token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    const resp = await fetch('/api/chat/stream', {
      method: 'POST', headers, signal: abortCtrl.signal,
      body: JSON.stringify({ question, history, sessionId: currentSessionId.value }),
    })
    if (!resp.ok || !resp.body) throw new Error('请求失败(' + resp.status + ')')
    const ct = resp.headers.get('content-type') || ''
    if (!ct.includes('text/event-stream')) {
      const text = await resp.text().catch(() => '')
      throw new Error('服务返回异常：' + (text.slice(0, 120) || resp.status))
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let receivedDone = false
    while (!receivedDone) {
      const { done, value } = await reader.read()
      if (done) {
        // 连接关闭时冲刷尾部残余帧，避免丢失最后一段 delta
        if (buffer.trim()) applyEvent(parseEvent(buffer), messages.value[aiIdx])
        break
      }
      // CRLF 归一化放在拼接后的 buffer 上，且只处理成对 \r\n，避免跨包孤立 \r 造出假的 \n\n
      buffer += decoder.decode(value, { stream: true })
      buffer = buffer.replace(/\r\n/g, '\n')
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx); buffer = buffer.slice(idx + 2)
        const ev = parseEvent(raw)
        const m = messages.value[aiIdx]
        // 流式期间切换/删除会话后，aiIdx 可能已指向别的消息，立即停止写入
        if (!m || m.question !== question) { receivedDone = true; break }
        if (ev.type === 'done') { receivedDone = true; break }
        if (ev.type === 'error') { applyEvent(ev, m); receivedDone = true; break }
        applyEvent(ev, m)
      }
    }
    loadSessions()
  } catch (e: any) {
    const m = messages.value[aiIdx]
    if (e.name !== 'AbortError') { m.content = '出错了：' + (e.message || ''); m.html = renderAnswer(m.content) }
    else if (!m.content) { m.content = '（已停止生成）'; m.html = renderAnswer(m.content) }
  } finally {
    const m = messages.value[aiIdx]
    if (m) {
      // 收尾去重并重渲染：保证实时答案与落库历史显示一致，且引用编号可点击
      m.content = dedupeCitations(m.content)
      m.html = renderAnswer(m.content)
      filterCitedSources(m)
    }
    loading.value = false; abortCtrl = null; scrollBottom(true)
  }
}

/**
 * 用户点击「停止」：abort 当前 fetch，触发 send 的 AbortError 分支收尾，不在此额外清理状态。
 */
function stop() { abortCtrl?.abort() }

const speaking = ref<any>(null)
function cleanForSpeech(text: string) {
  return text
    .replace(/\[(?:来源)?\d+\]/g, '')
    .replace(/\*\*/g, '')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/^\s*[-*]\s+/gm, '')
    .replace(/\n{2,}/g, '。')
}
function speak(m: any) {
  if (!('speechSynthesis' in window)) { ElMessage.warning('浏览器不支持朗读'); return }
  if (speaking.value === m) { speechSynthesis.cancel(); speaking.value = null; return }
  speechSynthesis.cancel()
  const utter = new SpeechSynthesisUtterance(cleanForSpeech(m.content))
  utter.lang = 'zh-CN'; utter.rate = 1
  utter.onend = () => { speaking.value = null }
  speechSynthesis.speak(utter)
  speaking.value = m
}

function copy(m: any) { navigator.clipboard?.writeText(m.content); ElMessage.success('已复制') }

function regenerate(m: any) {
  const idx = messages.value.indexOf(m)
  const prev = messages.value[idx - 1]
  if (prev && prev.role === 'user') {
    messages.value = messages.value.slice(0, idx)
    send(prev.content)
  }
}


/**
 * 正文 [N] 脚注点击定位：找到第 N 条来源卡片并平滑滚动到视口中部。
 * 参数 e：冒泡到气泡容器的点击事件；idx 越界或为 0 时回落到第一条卡片。
 */
function onCiteClick(e: Event) {
  const t = (e.target as HTMLElement).closest('.cite') as HTMLElement | null
  if (!t) return
  const idx = Number(t.getAttribute('data-idx') || '0')
  const all = Array.from(document.querySelectorAll('.src')) as HTMLElement[]
  const el = (idx > 0 ? all[idx - 1] : null) || all[0]
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

/** 同一来源编号全文只保留首次标注；模型偶尔会每句话都重复 [来源N]。 */
function dedupeCitations(text: string) {
  const seen = new Set<string>()
  return text.replace(/\[来源(\d+)\]/g, (all, n: string) => {
    if (seen.has(n)) return ''
    seen.add(n)
    return all
  })
}

function filterCitedSources(m: any) {
  if (!m.content || !m.sources || !m.sources.length) return
  const cited = new Set<number>()
  const re = /\[(?:来源)?(\d+)\]/g
  let mm
  while ((mm = re.exec(m.content)) !== null) cited.add(Number(mm[1]))
  if (cited.size > 0) m.sources = m.sources.filter((s: any) => cited.has(s.index))
}

function parseEvent(raw: string): any {
  const lines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('data:')) lines.push(line.slice(5).trimStart())
  }
  // 心跳行/注释行不带 data，不应被当作流结束
  if (lines.length === 0) return { type: 'noop' }
  const dataStr = lines.join('\n')
  try { return JSON.parse(dataStr) } catch { return { type: 'error', data: dataStr } }
}

function applyEvent(ev: any, m: any) {
  if (!m) return
  if (ev.type === 'sources') m.sources = ev.data || []
  else if (ev.type === 'delta') { m.content += (ev.data || ''); m.html = renderAnswer(m.content); scrollBottom() }
  else if (ev.type === 'error') { m.content = '出错了：' + (ev.data || ''); m.html = renderAnswer(m.content) }
  // trace 等事件前端不展示，显式忽略
}

function groupedSources(sources: any[]) {
  const groups: any[] = []
  const map = new Map<string, any>()
  for (const s of sources) {
    const key = s.fileName + '|' + (s.clauseRef || '')
    if (!map.has(key)) {
      const rawUrl = typeof s.sourceUrl === 'string' ? s.sourceUrl : ''
      // 只放行 http(s) 绝对外链，拦截 javascript: 等协议，避免来源链接成为注入载体
      const safeUrl = /^https?:\/\//i.test(rawUrl) ? rawUrl : ''
      const g = { fileName: s.fileName, clauseRef: s.clauseRef, sourceUrl: safeUrl, snippet: s.snippet, indices: [s.index] }
      map.set(key, g); groups.push(g)
    } else { map.get(key).indices.push(s.index) }
  }
  return groups
}
function fmtIndices(indices: number[]) { return indices.map(i => '[' + i + ']').join(' ') }

function scrollBottom(smooth = false) { nextTick(() => { const el = listRef.value; if (!el) return; if (smooth) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' }); else el.scrollTop = el.scrollHeight }) }

onMounted(async () => {
  loadNotices(); loadHot()
  await loadSessions()
  const sid = route.query.sessionId
  if (sid && auth.token) openSession(Number(sid))
})
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--cp-bg);
  background-image: var(--cp-atmosphere);
}
.main { flex: 1; display: flex; min-height: 0; }

/* 侧栏：会话即目录 */
.sidebar {
  width: 248px;
  flex: 0 0 248px;
  display: flex;
  flex-direction: column;
  background: var(--cp-card-bg);
  border-right: 1px solid var(--cp-border);
}
.sidebar-head { padding: var(--cp-sp-3); border-bottom: 1px solid var(--cp-border-soft); }
.sidebar-head :deep(.el-button) { height: 36px; font-weight: var(--cp-fw-semi); }
.sidebar-search { padding: var(--cp-sp-2) var(--cp-sp-3); }
.session-list { flex: 1; overflow-y: auto; padding: var(--cp-sp-2); }
.session-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--cp-sp-2) var(--cp-sp-3);
  border-radius: var(--cp-r-sm);
  border-left: 2px solid transparent;
  cursor: pointer;
  font-size: var(--cp-fs-sm);
  color: var(--cp-text-2);
  transition: background-color var(--cp-dur-fast) ease-out,
              color var(--cp-dur-fast) ease-out,
              border-color var(--cp-dur-fast) ease-out;
}
.session-item:hover { background: var(--cp-hover-bg); }
.session-item.active {
  background: var(--cp-emphasis-soft);
  color: var(--cp-emphasis);
  border-left-color: var(--cp-accent);
  font-weight: var(--cp-fw-semi);
}
.stitle { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.sops { display: none; gap: var(--cp-sp-1); flex: 0 0 auto; }
.session-item:hover .sops { display: flex; }
/* 编辑/删除：32x32 点击区 + 18px 矢量图标，原 14px 图标难以点准 */
.sop {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cp-r-sm);
  color: var(--cp-text-3);
  cursor: pointer;
  font-size: var(--cp-fs-base);
  transition: color var(--cp-dur-fast) ease-out, background-color var(--cp-dur-fast) ease-out;
}
.sop :deep(svg) { width: 18px; height: 18px; }
.sop:hover { color: var(--cp-emphasis); background: var(--cp-emphasis-soft); }
.sop.del:hover { color: var(--cp-danger); background: var(--cp-danger-soft); }
.empty { color: var(--cp-text-3); font-size: var(--cp-fs-sm); text-align: center; padding: var(--cp-sp-5) 0; }

.chat-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* 状态反馈条 */
.notice-banner {
  background: var(--cp-notice-bg);
  color: var(--cp-notice-fg);
  font-size: var(--cp-fs-sm);
  padding: var(--cp-sp-2) var(--cp-sp-5);
  display: flex;
  gap: var(--cp-sp-5);
  overflow-x: auto;
  white-space: nowrap;
  border-bottom: 1px solid var(--cp-accent-line);
}
.notice-item { display: inline-flex; align-items: center; gap: var(--cp-sp-1); }
.notice-item b { font-weight: var(--cp-fw-semi); }
.guest-banner {
  background: var(--cp-warning-soft);
  color: var(--cp-warning);
  font-size: var(--cp-fs-sm);
  text-align: center;
  padding: var(--cp-sp-2);
  border-bottom: 1px solid var(--cp-accent-line);
}

/* 消息区：限制阅读栏宽，中文一行约 28–40 字 */
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--cp-sp-6) max(var(--cp-sp-5), calc((100% - var(--cp-read-max)) / 2));
  /* 浏览器滚动锚定会与脚本的 scrollTop 互相拉扯，是流式下拉顿挫的主因 */
  overflow-anchor: none;
  overscroll-behavior: contain;
  scroll-behavior: auto;
  contain: layout paint;
}

/* 欢迎区 */
.welcome { text-align: center; margin: var(--cp-sp-7) auto var(--cp-sp-5); max-width: 620px; }
.welcome-logo {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--cp-sp-4);
  border-radius: var(--cp-r-lg);
  background: var(--cp-accent);
  color: var(--cp-on-accent);
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-3xl);
  font-weight: var(--cp-fw-bold);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--cp-shadow-2);
}
.welcome-title {
  margin: 0 0 var(--cp-sp-2);
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-xl);
  font-weight: var(--cp-fw-bold);
  color: var(--cp-text-1);
  letter-spacing: .02em;
}
.welcome-sub { margin: 0 0 var(--cp-sp-5); color: var(--cp-text-3); font-size: var(--cp-fs-base); }
.hint { text-align: center; margin-top: var(--cp-sp-6); }
.hint-title { color: var(--cp-text-3); font-size: var(--cp-fs-sm); margin: var(--cp-sp-2) 0; display: block; letter-spacing: .12em; }
.hint-btns { display: flex; gap: var(--cp-sp-2); justify-content: center; flex-wrap: wrap; align-items: center; }
.suggest-btn {
  border-radius: var(--cp-r-pill);
  border: 1px solid var(--cp-border-strong);
  color: var(--cp-emphasis);
  background: var(--cp-card-bg);
  padding: 0 var(--cp-sp-4);
  font-size: var(--cp-fs-sm);
  transition: border-color var(--cp-dur-fast) ease-out,
              background-color var(--cp-dur-fast) ease-out,
              transform var(--cp-dur-fast) ease-out;
}
.suggest-btn:hover { border-color: var(--cp-accent); background: var(--cp-emphasis-soft); transform: translateY(-1px); }

/* 消息与气泡 */
.msg { display: flex; gap: var(--cp-sp-3); margin-bottom: var(--cp-sp-5); }
.msg.user { flex-direction: row-reverse; }
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--cp-accent);
  color: var(--cp-on-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--cp-font-display);
  font-size: var(--cp-fs-base);
  font-weight: var(--cp-fw-semi);
  flex-shrink: 0;
  box-shadow: var(--cp-shadow-1);
}
.body { max-width: 84%; min-width: 0; }
.bubble {
  width: fit-content;
  max-width: 100%;
  padding: var(--cp-sp-3) var(--cp-sp-4);
  border-radius: var(--cp-r-md);
  background: var(--cp-bubble-bg);
  border: 1px solid var(--cp-border);
  color: var(--cp-text-2);
  font-size: var(--cp-fs-base);
  line-height: var(--cp-lh-body);
  letter-spacing: var(--cp-tracking);
  word-break: break-word;
  box-shadow: var(--cp-shadow-1);
}
.msg.user .bubble {
  background: var(--cp-bubble-user-bg);
  color: var(--cp-bubble-user-fg);
  border-color: transparent;
}
/* AI 答案与来源条限制阅读宽度；窄屏下 min() 退化为 100%，不影响原有响应式 */
.msg.assistant .bubble,
.msg.assistant .sources { max-width: min(100%, var(--cp-answer-max)); }

/* Markdown 答案的杂志式排版（原项目完全缺失，v-html 内容靠 :deep 命中） */
.bubble :deep(p) { margin: 0 0 var(--cp-sp-3); }
.bubble :deep(p:last-child) { margin-bottom: 0; }
.bubble :deep(h1), .bubble :deep(h2), .bubble :deep(h3), .bubble :deep(h4) {
  font-family: var(--cp-font-display);
  color: var(--cp-text-1);
  margin: var(--cp-sp-4) 0 var(--cp-sp-2);
  line-height: var(--cp-lh-head);
}
.bubble :deep(h1) { font-size: var(--cp-fs-lg); }
.bubble :deep(h2) { font-size: var(--cp-fs-md); }
.bubble :deep(h3), .bubble :deep(h4) { font-size: var(--cp-fs-base); }
.bubble :deep(ul), .bubble :deep(ol) { margin: 0 0 var(--cp-sp-3); padding-left: var(--cp-sp-5); }
.bubble :deep(li) { margin-bottom: var(--cp-sp-1); }
.bubble :deep(li::marker) { color: var(--cp-accent); }
.bubble :deep(strong) { color: var(--cp-text-1); font-weight: var(--cp-fw-semi); }
.bubble :deep(a) { color: var(--cp-emphasis); text-decoration: underline; text-underline-offset: 2px; }
.bubble :deep(code) {
  font-family: var(--cp-font-mono);
  font-size: .92em;
  background: var(--cp-surface-3);
  border: 1px solid var(--cp-border-soft);
  border-radius: var(--cp-r-xs);
  padding: 1px 5px;
}
.bubble :deep(pre) {
  background: var(--cp-surface-3);
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-r-sm);
  padding: var(--cp-sp-3);
  overflow-x: auto;
  margin: 0 0 var(--cp-sp-3);
}
.bubble :deep(pre code) { background: none; border: none; padding: 0; }
.bubble :deep(blockquote) {
  margin: 0 0 var(--cp-sp-3);
  padding: var(--cp-sp-2) var(--cp-sp-3);
  border-left: 3px solid var(--cp-accent);
  background: var(--cp-emphasis-soft);
  color: var(--cp-text-2);
  border-radius: 0 var(--cp-r-sm) var(--cp-r-sm) 0;
}
.bubble :deep(table) { border-collapse: collapse; width: 100%; margin-bottom: var(--cp-sp-3); font-size: var(--cp-fs-sm); }
.bubble :deep(th), .bubble :deep(td) { border: 1px solid var(--cp-border); padding: var(--cp-sp-2); text-align: left; }
.bubble :deep(th) { background: var(--cp-surface-2); font-weight: var(--cp-fw-semi); color: var(--cp-text-1); }
.bubble :deep(hr) { border: none; border-top: 1px solid var(--cp-border); margin: var(--cp-sp-4) 0; }

/* 脚注引用 */
.bubble :deep(.cite) {
  color: var(--cp-emphasis);
  font-weight: var(--cp-fw-bold);
  cursor: pointer;
  padding: 0 2px;
  border-radius: var(--cp-r-xs);
  font-variant-numeric: tabular-nums;
  transition: background-color var(--cp-dur-fast) ease-out;
}
.bubble :deep(.cite:hover) { background: var(--cp-emphasis-soft); }
.msg.user .bubble :deep(.cite) { color: var(--cp-bubble-user-fg); }

/* 加载态 */
.thinking { display: inline-flex; align-items: center; gap: var(--cp-sp-2); color: var(--cp-text-3); font-size: var(--cp-fs-sm); font-style: italic; }
.thinking::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--cp-accent);
  animation: cp-pulse 1.2s ease-in-out infinite;
}
@keyframes cp-pulse {
  0%, 100% { opacity: .35; transform: scale(.8); }
  50% { opacity: 1; transform: scale(1.15); }
}

/* 操作条 */
.op-bar { display: flex; align-items: center; gap: var(--cp-sp-1); margin-top: var(--cp-sp-2); }
.op-bar :deep(.el-button.is-text) { color: var(--cp-text-3); font-size: var(--cp-fs-xs); padding: 2px 6px; }
.op-bar :deep(.el-button.is-text:hover) { color: var(--cp-emphasis); background: var(--cp-emphasis-soft); }
.op-bar .sep { width: 1px; height: 12px; background: var(--cp-border); margin: 0 6px; }

/* 参考来源：脚注式卡片 */
.sources {
  margin-top: var(--cp-sp-3);
  background: var(--cp-source-bg);
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-r-md);
  overflow: hidden;
}
.sources :deep(.el-collapse-item__header) { padding: 0 var(--cp-sp-3); border-bottom-color: transparent; }
.sources :deep(.el-collapse-item__header.is-active) { border-bottom-color: var(--cp-border-soft); }
.sources :deep(.el-collapse-item__content) { padding-bottom: var(--cp-sp-2); }
.src {
  background: var(--cp-card-bg);
  border: 1px solid var(--cp-border-soft);
  border-left: 3px solid var(--cp-accent);
  border-radius: var(--cp-r-sm);
  padding: var(--cp-sp-2) var(--cp-sp-3);
  margin: var(--cp-sp-2) var(--cp-sp-2);
}
.src-head { display: flex; align-items: center; gap: var(--cp-sp-2); flex-wrap: wrap; font-size: var(--cp-fs-sm); }
.src-no { color: var(--cp-emphasis); font-weight: var(--cp-fw-bold); font-variant-numeric: tabular-nums; }
.src a { color: var(--cp-emphasis); text-decoration: none; font-weight: var(--cp-fw-semi); }
.src-name { font-weight: var(--cp-fw-medium); color: var(--cp-text-1); }
.src .clause { color: var(--cp-text-3); font-size: var(--cp-fs-xs); }
.src-snippet {
  margin-top: var(--cp-sp-2);
  font-size: var(--cp-fs-xs);
  color: var(--cp-text-3);
  line-height: 1.65;
  border-left: 2px solid var(--cp-border-strong);
  padding-left: var(--cp-sp-2);
}
.trace { background: var(--cp-source-bg); border-color: var(--cp-border); }

/* 输入栏 */
.input-bar {
  display: flex;
  gap: var(--cp-sp-3);
  padding: var(--cp-sp-3) var(--cp-sp-5);
  background: var(--cp-card-bg);
  border-top: 1px solid var(--cp-border);
  box-shadow: 0 -4px 16px rgba(26, 34, 43, .03);
}
.input-bar :deep(.el-input__wrapper) {
  border-radius: var(--cp-r-md);
  background-color: transparent;
  box-shadow: 0 0 0 1px var(--cp-border-strong) inset;
}
.input-bar :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cp-accent) inset;
}
.input-bar .el-button { border-radius: var(--cp-r-md); padding: 0 var(--cp-sp-5); height: 40px; flex: 0 0 auto; }

/* 移动端：侧栏改为顶部紧凑「会话目录条」，保留全部功能，仅改变布局 */
@media (max-width: 768px) {
  .main { flex-direction: column; }
  .sidebar {
    width: 100%;
    flex: 0 0 auto;
    border-right: none;
    border-bottom: 1px solid var(--cp-border);
    max-height: 148px;
  }
  .sidebar-head { padding: var(--cp-sp-2) var(--cp-sp-3) 0; border-bottom: none; }
  .sidebar-search { padding: var(--cp-sp-1) var(--cp-sp-3); }
  .session-list {
    display: flex;
    gap: var(--cp-sp-2);
    overflow-x: auto;
    overflow-y: hidden;
    padding: var(--cp-sp-1) var(--cp-sp-3) var(--cp-sp-2);
  }
  .session-item {
    flex: 0 0 auto;
    min-width: 132px;
    max-width: 200px;
    padding: var(--cp-sp-2);
    border-left: none;
    border-bottom: 2px solid transparent;
  }
  .session-item.active { border-left: none; border-bottom-color: var(--cp-accent); }
  .sops { display: flex; }
  .empty { padding: var(--cp-sp-2) 0; }
  .msg-list { padding: var(--cp-sp-4) var(--cp-sp-3); }
  .body { max-width: 88%; }
  .input-bar { padding: var(--cp-sp-2) var(--cp-sp-3); gap: var(--cp-sp-2); }
  .input-bar .el-button { padding: 0 var(--cp-sp-3); }
  .notice-banner { padding: var(--cp-sp-2) var(--cp-sp-3); gap: var(--cp-sp-4); }
  .welcome { margin-top: var(--cp-sp-5); }
  .welcome-logo { width: 60px; height: 60px; font-size: var(--cp-fs-2xl); }
}
@media (max-width: 420px) {
  .body { max-width: 92%; }
  .bubble { padding: var(--cp-sp-2) var(--cp-sp-3); }
  .hint-btns { gap: var(--cp-sp-1); }
  .suggest-btn { padding: 0 var(--cp-sp-3); }
}
</style>