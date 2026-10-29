<template>
  <div class="chat-page">
    <header class="topbar">
      <div class="brand">学事通 <span class="en">CampusPedia</span></div>
      <div class="actions">
        <template v-if="auth.token">
          <el-button v-if="auth.role === 'admin'" text @click="$router.push('/admin')">管理后台</el-button>
          <span class="user">{{ auth.username }}</span>
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
        <div class="session-list">
          <div v-for="s in sessions" :key="s.id" class="session-item" :class="{ active: s.id === currentSessionId }" @click="openSession(s.id)">
            <span class="stitle">{{ s.title }}</span>
            <el-icon class="sdel" @click.stop="confirmDel(s)"><Delete /></el-icon>
          </div>
          <div v-if="sessions.length === 0" class="empty">暂无会话，点「新对话」开始</div>
        </div>
      </aside>

      <div class="chat-area">
        <div v-if="!auth.token" class="guest-banner">👋 游客模式：聊天不保存记录 · 登录后保存历史、管理员可进后台</div>
        <div class="msg-list" ref="listRef">
          <div v-if="messages.length === 0" class="hint">
            <p>试试问我：</p>
            <el-button size="small" @click="send('转专业需要什么条件？')">转专业需要什么条件？</el-button>
            <el-button size="small" @click="send('国家奖学金的申请条件？')">国家奖学金的申请条件？</el-button>
          </div>

          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="avatar">{{ m.role === 'user' ? '我' : '通' }}</div>
            <div class="body">
              <div class="bubble">
                <div v-if="m.role === 'assistant' && m.content" v-html="m.html"></div>
                <div v-else-if="m.role === 'assistant' && loading" class="thinking">思考中…</div>
                <div v-else>{{ m.content }}</div>
              </div>

              <el-collapse v-if="m.trace" class="trace">
                <el-collapse-item title="🔍 思考过程" name="1">
                  <div class="trace-body">
                    <div class="trace-row"><span class="label">改写 query</span><span>{{ m.trace.rewriteQuery || '—' }}</span></div>
                    <div class="trace-row"><span class="label">检索</span><span>候选 {{ m.trace.candidateCount }} 条 → 保留 {{ m.trace.finalCount }} 条（阈值 {{ m.trace.threshold }}）</span></div>
                    <div v-if="m.trace.note" class="trace-row"><span class="label">说明</span><span>{{ m.trace.note }}</span></div>
                  </div>
                </el-collapse-item>
              </el-collapse>

              <el-collapse v-if="m.sources && m.sources.length" class="sources">
                <el-collapse-item :title="'📎 参考来源（' + m.sources.length + ' 条）'" name="1">
                  <div v-for="g in groupedSources(m.sources)" :key="g.fileName + g.clauseRef" class="src">
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
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { useAuthStore } from '../stores/auth'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const md = new MarkdownIt()
const messages = ref<any[]>([])
const sessions = ref<any[]>([])
const currentSessionId = ref<number | null>(null)
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)

function logout() { auth.logout(); router.push('/login') }

async function loadSessions() {
  if (!auth.token) return
  try {
    const res = await request.get('/sessions') as any
    if (res.code === 200) sessions.value = res.data || []
  } catch {}
}

async function newSession() {
  if (currentSessionId.value && messages.value.length === 0) {
    ElMessage.info('请在当前对话框进行对话')
    return
  }
  try {
    const res = await request.post('/sessions', { title: '新对话' }) as any
    if (res.code === 200) {
      sessions.value.unshift(res.data)
      currentSessionId.value = res.data.id
      messages.value = []
    }
  } catch {}
}

async function openSession(id: number) {
  currentSessionId.value = id
  try {
    const res = await request.get('/sessions/' + id + '/messages') as any
    if (res.code === 200) {
      messages.value = (res.data || []).map((m: any) => ({
        role: m.role,
        content: m.content || '',
        html: m.role === 'assistant' ? md.render(m.content || '') : '',
        sources: m.sources ? safeJson(m.sources) : [],
        trace: null,
      }))
      scrollBottom()
    }
  } catch {}
}

async function confirmDel(s: any) {
  try {
    await ElMessageBox.confirm('是否删除：对话「' + s.title + '」？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await delSession(s.id)
  } catch {}
}

async function delSession(id: number) {
  try { await request.delete('/sessions/' + id) } catch {}
  sessions.value = sessions.value.filter(s => s.id !== id)
  if (currentSessionId.value === id) { currentSessionId.value = null; messages.value = [] }
}

function safeJson(s: string) { try { return JSON.parse(s) } catch { return [] } }

async function send(q?: string) {
  const question = (q || input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  loading.value = true
  const history = messages.value.slice(-6).map(m => ({ role: m.role, content: m.content }))
  messages.value.push({ role: 'user', content: question, html: '', sources: [], trace: null })
  messages.value.push({ role: 'assistant', content: '', html: '', sources: [], trace: null })
  const aiIdx = messages.value.length - 1

  // 登录用户且无当前会话 → 新建会话
  if (auth.token && !currentSessionId.value) {
    try {
      const res = await request.post('/sessions', { title: question.slice(0, 15) }) as any
      if (res.code === 200) { currentSessionId.value = res.data.id; sessions.value.unshift(res.data) }
    } catch {}
  }

  try {
    const token = localStorage.getItem('token')
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = 'Bearer ' + token
    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers,
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
        const raw = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        const ev = parseEvent(raw)
        const m = messages.value[aiIdx]
        if (ev.type === 'sources') m.sources = ev.data || []
        else if (ev.type === 'trace') m.trace = ev.data
        else if (ev.type === 'error') { m.content = '出错了：' + (ev.data || ''); m.html = md.render(m.content) }
        else if (ev.type === 'delta') { m.content += (ev.data || ''); m.html = md.render(m.content); scrollBottom() }
      }
    }
    loadSessions()
  } catch (e: any) {
    const m = messages.value[aiIdx]
    m.content = '出错了：' + (e.message || '')
    m.html = md.render(m.content)
  } finally {
    loading.value = false
    scrollBottom()
  }
}

function parseEvent(raw: string): any {
  let dataStr = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('data:')) dataStr += line.slice(5).trimStart()
  }
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

onMounted(loadSessions)
</script>

<style scoped>
.chat-page { height: 100vh; display: flex; flex-direction: column; }
.topbar { display: flex; justify-content: space-between; align-items: center; padding: 12px 24px; background: linear-gradient(90deg, #4f46e5, #7c3aed); color: #fff; box-shadow: 0 2px 8px rgba(79,70,229,.15); }
.topbar .brand { color: #fff; }
.topbar .brand .en { color: rgba(255,255,255,.75); }
.topbar .user { color: rgba(255,255,255,.85); }
.topbar :deep(.el-button.is-text) { color: rgba(255,255,255,.9); }
.main { flex: 1; display: flex; min-height: 0; }
.sidebar { width: 220px; background: #fff; border-right: 1px solid #eee; display: flex; flex-direction: column; }
.sidebar-head { padding: 12px; border-bottom: 1px solid #f0f0f0; }
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 13px; color: #444; }
.session-item:hover { background: #f5f6f8; }
.session-item.active { background: #eef2ff; color: #4338ca; }
.stitle { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sdel { color: #ccc; font-size: 12px; }
.sdel:hover { color: #e74c3c; }
.empty { color: #bbb; font-size: 12px; text-align: center; padding: 20px 0; }
.chat-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.guest-banner { background: #fff7e6; color: #ad6800; font-size: 12px; text-align: center; padding: 6px; border-bottom: 1px solid #ffe7ba; }
.msg-list { flex: 1; overflow-y: auto; padding: 20px; }
.hint { color: #999; text-align: center; margin-top: 40px; }
.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.avatar { width: 34px; height: 34px; border-radius: 50%; background: #eef2ff; color: #4338ca; display: flex; align-items: center; justify-content: center; font-size: 13px; flex-shrink: 0; font-weight: 600; }
.msg.user .avatar { background: #4f46e5; color: #fff; }
.body { max-width: 78%; min-width: 0; }
.bubble { padding: 11px 15px; border-radius: 12px; background: #fff; line-height: 1.7; word-break: break-word; box-shadow: 0 1px 3px rgba(0,0,0,.06); border: 1px solid #f0f0f0; }
.msg.user .bubble { background: #4f46e5; color: #fff; border: none; }
.trace { margin-top: 8px; background: #f7f8fa; border-radius: 12px; border: 1px solid #f0f0f0; overflow: hidden; }
.trace-body { padding: 2px 6px; font-size: 12px; color: #555; }
.trace-row { display: flex; gap: 8px; margin: 6px 0; align-items: baseline; }
.trace-row .label { color: #999; flex-shrink: 0; min-width: 64px; }
.sources { margin-top: 8px; background: #f7f8fa; border-radius: 12px; border: 1px solid #f0f0f0; overflow: hidden; }
.src { background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; padding: 8px 10px; margin: 6px 4px; }
.src-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 13px; }
.src-no { color: #4f46e5; font-weight: 700; }
.src a { color: #4f46e5; text-decoration: none; font-weight: 500; }
.src-name { font-weight: 500; }
.src .clause { color: #888; font-size: 12px; }
.src-snippet { margin-top: 6px; font-size: 12px; color: #666; line-height: 1.6; border-left: 2px solid #e5e7eb; padding-left: 8px; }
.input-bar { display: flex; gap: 8px; padding: 12px 20px; background: #fff; border-top: 1px solid #eee; }
</style>
