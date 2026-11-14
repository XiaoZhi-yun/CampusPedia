<template>
  <div class="chat-page">
    <header class="topbar">
      <div class="brand">学事通 <span class="en">CampusPedia</span></div>
      <div class="actions">
        <template v-if="auth.token">
          <el-button text @click="$router.push('/profile')">个人中心</el-button>
          <el-button v-if="auth.role === 'admin'" text @click="$router.push('/admin')">管理后台</el-button>
          <el-button text @click="toggleDark">{{ dark ? "☀️" : "🌙" }}</el-button>
          <span class="user">{{ auth.realName || auth.studentNo }}</span>
          <el-button text @click="logout">退出</el-button>
        </template>
        <template v-else>
          <el-button text @click="$router.push('/login')">登录 / 注册</el-button>
        </template>
      </div>
    </header>

    <div class="main">
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
        <div v-if="!auth.token" class="guest-banner">👋 游客模式：聊天不保存记录 · 登录后保存历史、管理员可进后台</div>
        <div v-if="notices.length" class="notice-banner">
          <span v-for="n in notices" :key="n.id" class="notice-item">📢 {{ n.title }}</span>
        </div>
        <div class="msg-list" ref="listRef">
          <div v-if="messages.length === 0" class="hint">
            <p class="hint-title">常见问题</p>
            <div class="hint-btns">
              <el-button v-for="f in faqs" :key="f.id" size="small" @click="send(f.question)">{{ f.question }}</el-button>
            </div>
            <p class="hint-title" style="margin-top:16px">猜你想问</p>
            <div class="hint-btns">
              <el-button size="small" @click="send('转专业需要什么条件？')">转专业需要什么条件？</el-button>
              <el-button size="small" @click="send('国家奖学金怎么申请？')">国家奖学金怎么申请？</el-button>
              <el-button size="small" @click="send('宿舍怎么申请调换？')">宿舍怎么申请调换？</el-button>
            </div>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="avatar" :style="m.role === 'user' ? { background: AVATAR_COLORS[auth.avatar] || AVATAR_COLORS.p1 } : {}">{{ m.role === 'user' ? (auth.realName ? auth.realName[0] : '我') : '通' }}</div>
            <div class="body">
              <div class="bubble" @click="onCiteClick($event)">
                <div v-if="m.role === 'assistant' && m.content" v-html="m.html"></div>
                <div v-else-if="m.role === 'assistant' && loading" class="thinking">思考中…</div>
                <div v-else>{{ m.content }}</div>
              </div>

              <div v-if="m.role === 'assistant' && m.content && !loading" class="op-bar">
                <el-button size="small" text @click="copy(m)">复制</el-button>
                <el-button size="small" text @click="regenerate(m)">重新生成</el-button>
                <span class="sep"></span>
                <el-button size="small" text :type="m.fb === 'up' ? 'primary' : ''" @click="feedback(m, 1)">👍 有用</el-button>
                <el-button size="small" text :type="m.fb === 'down' ? 'danger' : ''" @click="feedback(m, 0)">👎 无用</el-button>
              </div>
              <div v-if="loading && i === messages.length - 1" class="op-bar">
                <el-button size="small" text @click="stop()">⏹ 停止生成</el-button>
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

        <div class="input-bar">
          <el-input v-model="input" placeholder="输入你的校园问题…" :disabled="loading" @keyup.enter="send()" />
          <el-button type="primary" :disabled="loading || !input.trim()" @click="send()">{{ loading ? '…' : '发送' }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
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
const faqs = ref<any[]>([])
const notices = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const sessionKw = ref('')
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)
let abortCtrl: AbortController | null = null
const dark = ref(false)
const AVATAR_COLORS: Record<string, string> = { p1: '#4f46e5', p2: '#0ea5e9', p3: '#10b981', p4: '#f59e0b', p5: '#ef4444', p6: '#8b5cf6', p7: '#ec4899', p8: '#14b8a6' }
function toggleDark() { dark.value = !dark.value; document.documentElement.classList.toggle('dark', dark.value) }

const filteredSessions = computed(() => {
  const kw = sessionKw.value.trim()
  if (!kw) return sessions.value
  return sessions.value.filter((s: any) => (s.title || '').includes(kw))
})

function logout() { auth.logout(); router.push('/login') }

function renderAnswer(content: string) {
  let html = md.render(content)
  html = html.replace(/\[(\d+)\]/g, '<span class="cite" data-idx="$1">[$1]</span>')
  return html
}

async function loadSessions() {
  if (!auth.token) return
  try { const res = await request.get('/sessions') as any; if (res.code === 200) sessions.value = res.data || [] } catch {}
}
async function loadFaqs() {
  try { const r = await request.get('/faq') as any; if (r.code === 200) faqs.value = r.data || [] } catch {}
}
async function loadNotices() {
  try { const r = await request.get('/notice') as any; if (r.code === 200) notices.value = r.data || [] } catch {}
}

async function newSession() {
  if (currentSessionId.value && messages.value.length === 0) { ElMessage.info('请在当前对话框进行对话'); return }
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

async function openSession(id: number) {
  currentSessionId.value = id
  try {
    const res = await request.get('/sessions/' + id + '/messages') as any
    if (res.code === 200) {
      messages.value = (res.data || []).map((m: any) => ({
        role: m.role, content: m.content || '',
        html: m.role === 'assistant' ? renderAnswer(m.content || '') : '',
        sources: m.sources ? safeJson(m.sources) : [], trace: null, fb: null,
      }))
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

async function send(q?: string) {
  const question = (q || input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  loading.value = true
  const history = messages.value.slice(-6).map(m => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: question, html: '', sources: [], trace: null, fb: null })
  messages.value.push({ role: 'assistant', content: '', html: '', sources: [], trace: null, fb: null })
  const aiIdx = messages.value.length - 1

  if (auth.token && !currentSessionId.value) {
    try {
      const res = await request.post('/sessions', { title: question.slice(0, 15) }) as any
      if (res.code === 200) { currentSessionId.value = res.data.id; sessions.value.unshift(res.data) }
    } catch {}
  }

  abortCtrl = new AbortController()
  try {
    const token = localStorage.getItem('token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    const resp = await fetch('/api/chat/stream', {
      method: 'POST', headers, signal: abortCtrl.signal,
      body: JSON.stringify({ question, history, sessionId: currentSessionId.value }),
    })
    if (!resp.ok || !resp.body) throw new Error('请求失败(' + resp.status + ')')
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n').replace(/\r/g, '\n')
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, idx); buffer = buffer.slice(idx + 2)
        const ev = parseEvent(raw); const m = messages.value[aiIdx]
        if (ev.type === 'sources') m.sources = ev.data || []
        else if (ev.type === 'error') { m.content = '出错了：' + (ev.data || ''); m.html = renderAnswer(m.content) }
        else if (ev.type === 'delta') { m.content += (ev.data || ''); m.html = renderAnswer(m.content); scrollBottom() }
      }
    }
    loadSessions()
  } catch (e: any) {
    const m = messages.value[aiIdx]
    if (e.name !== 'AbortError') { m.content = '出错了：' + (e.message || ''); m.html = renderAnswer(m.content) }
    else if (!m.content) { m.content = '（已停止生成）'; m.html = renderAnswer(m.content) }
  } finally {
    loading.value = false; abortCtrl = null; scrollBottom()
  }
}

function stop() { abortCtrl?.abort() }

function copy(m: any) { navigator.clipboard?.writeText(m.content); ElMessage.success('已复制') }

function regenerate(m: any) {
  const idx = messages.value.indexOf(m)
  const prev = messages.value[idx - 1]
  if (prev && prev.role === 'user') {
    messages.value = messages.value.slice(0, idx)
    send(prev.content)
  }
}

function feedback(m: any, rating: number) {
  if (!auth.token) { ElMessage.warning('请先登录后再反馈'); return }
  m.fb = rating === 1 ? 'up' : 'down'
  request.post('/feedback', { rating }).then(() => ElMessage.success('感谢反馈')).catch(() => { ElMessage.error('反馈失败'); m.fb = null })
}

function onCiteClick(e: Event) {
  const t = (e.target as HTMLElement).closest('.cite') as HTMLElement | null
  if (t) {
    const idx = t.getAttribute('data-idx')
    const el = document.querySelector('.src') as HTMLElement | null
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function parseEvent(raw: string): any {
  let dataStr = ''
  for (const line of raw.split('\n')) { if (line.startsWith('data:')) dataStr += line.slice(5).trimStart() }
  if (!dataStr) return { type: 'done' }
  try { return JSON.parse(dataStr) } catch { return { type: 'error', data: dataStr } }
}

function groupedSources(sources: any[]) {
  const groups: any[] = []
  const map = new Map<string, any>()
  for (const s of sources) {
    const key = s.fileName + '|' + (s.clauseRef || '')
    if (!map.has(key)) {
      const g = { fileName: s.fileName, clauseRef: s.clauseRef, sourceUrl: s.sourceUrl, snippet: s.snippet, indices: [s.index] }
      map.set(key, g); groups.push(g)
    } else { map.get(key).indices.push(s.index) }
  }
  return groups
}
function fmtIndices(indices: number[]) { return indices.map(i => '[' + i + ']').join(' ') }

function scrollBottom() { nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight }) }

onMounted(async () => {
  loadFaqs(); loadNotices()
  await loadSessions()
  const sid = route.query.sessionId
  if (sid && auth.token) openSession(Number(sid))
})
</script>

<style scoped>
.chat-page { height: 100vh; display: flex; flex-direction: column; }
.topbar { display: flex; justify-content: space-between; align-items: center; padding: 12px 24px; background: linear-gradient(90deg, #4f46e5, #7c3aed); color: #fff; box-shadow: 0 2px 8px rgba(79,70,229,.15); }
.topbar .brand { color: #fff; }
.topbar .brand .en { color: rgba(255,255,255,.75); }
.topbar .user { color: rgba(255,255,255,.85); }
.topbar :deep(.el-button.is-text) { color: rgba(255,255,255,.9); }
.main { flex: 1; display: flex; min-height: 0; }
.sidebar { width: 230px; background: #fff; border-right: 1px solid #eee; display: flex; flex-direction: column; }
.sidebar-head { padding: 12px; border-bottom: 1px solid #f0f0f0; }
.sidebar-search { padding: 8px 12px; }
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 13px; color: #444; }
.session-item:hover { background: #f5f6f8; }
.session-item.active { background: #eef2ff; color: #4338ca; }
.stitle { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.sops { display: none; gap: 4px; }
.session-item:hover .sops { display: flex; }
.sop { color: #bbb; cursor: pointer; font-size: 14px; }
.sop.del:hover { color: #e74c3c; }
.empty { color: #bbb; font-size: 12px; text-align: center; padding: 20px 0; }
.chat-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.guest-banner { background: #fff7e6; color: #ad6800; font-size: 12px; text-align: center; padding: 6px; border-bottom: 1px solid #ffe7ba; }
.notice-banner { background: #eef2ff; color: #4338ca; font-size: 13px; padding: 6px 20px; display: flex; gap: 20px; overflow-x: auto; white-space: nowrap; }
.msg-list { flex: 1; overflow-y: auto; padding: 20px; }
.hint { text-align: center; margin-top: 30px; }
.hint-title { color: #999; font-size: 14px; margin: 8px 0; }
.hint-btns { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.avatar { width: 34px; height: 34px; border-radius: 50%; background: #eef2ff; color: #4338ca; display: flex; align-items: center; justify-content: center; font-size: 13px; flex-shrink: 0; font-weight: 600; }
.msg.user .avatar { background: #4f46e5; color: #fff; }
.body { max-width: 78%; min-width: 0; }
.bubble { padding: 11px 15px; border-radius: 12px; background: #fff; line-height: 1.7; word-break: break-word; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid #f0f0f0; }
.msg.user .bubble { background: #4f46e5; color: #fff; border: none; }
.bubble :deep(.cite) { color: #4f46e5; font-weight: 700; cursor: pointer; padding: 0 2px; }
.msg.user .bubble :deep(.cite) { color: #fff; }
.op-bar { display: flex; align-items: center; gap: 2px; margin-top: 6px; }
.op-bar .sep { width: 1px; height: 12px; background: #ddd; margin: 0 6px; }
.sources { margin-top: 8px; background: #f7f8fa; border-radius: 12px; border: 1px solid #f0f0f0; overflow: hidden; }
.src { background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; padding: 8px 10px; margin: 6px 4px; }
.src-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 13px; }
.src-no { color: #4f46e5; font-weight: 700; }
.src a { color: #4f46e5; text-decoration: none; font-weight: 500; }
.src-name { font-weight: 500; }
.src .clause { color: #888; font-size: 12px; }
.src-snippet { margin-top: 6px; font-size: 12px; color: #666; line-height: 1.6; border-left: 2px solid #e5e7eb; padding-left: 8px; }
.input-bar { display: flex; gap: 8px; padding: 12px 20px; background: #fff; border-top: 1px solid #eee; }
:global(.dark) .chat-page { background: #1f2937; }
:global(.dark) .chat-area, :global(.dark) .msg-list { background: #111827; }
:global(.dark) .bubble { background: #1f2937; color: #e5e7eb; border-color: #374151; }
:global(.dark) .sidebar, :global(.dark) .input-bar { background: #1f2937; border-color: #374151; }
:global(.dark) .session-item { color: #d1d5db; }
:global(.dark) .src { background: #1f2937; border-color: #374151; color: #d1d5db; }
@media (max-width: 768px) {
  .sidebar { display: none; }
  .body { max-width: 92%; }
  .topbar { padding: 10px 14px; }
  .msg-list { padding: 12px; }
}
</style>
