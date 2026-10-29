<template>
  <div class="chat-page">
    <header class="topbar">
      <div class="brand">学事通 <span class="en">CampusPedia</span></div>
      <div class="actions">
        <el-button text @click="$router.push('/admin')">管理后台</el-button>
        <span class="user">{{ auth.username }}</span>
        <el-button text @click="logout">退出</el-button>
      </div>
    </header>

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
            <div v-if="m.role === 'assistant'" v-html="m.html"></div>
            <div v-else>{{ m.content }}</div>
          </div>

          <el-collapse v-if="m.trace" class="trace">
            <el-collapse-item title="思考过程" name="1">
              <p>改写后 query：{{ m.trace.rewriteQuery }}</p>
              <p>候选条数：{{ m.trace.candidateCount }}，Rerank：{{ m.trace.rerankEnabled }}</p>
              <pre class="prompt">{{ m.trace.userPrompt }}</pre>
            </el-collapse-item>
          </el-collapse>

          <div v-if="m.sources && m.sources.length" class="sources">
            <div v-for="s in m.sources" :key="s.index" class="src">
              <b>[来源{{ s.index }}]</b>
              <a v-if="s.sourceUrl" :href="s.sourceUrl" target="_blank">《{{ s.fileName }}》</a>
              <span v-else>《{{ s.fileName }}》</span>
              <span class="clause">{{ s.clauseRef }}</span>
              <span class="score">相似度 {{ s.score }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-bar">
      <el-input v-model="input" placeholder="输入你的校园问题…" :disabled="loading" @keyup.enter="send()" />
      <el-button type="primary" :disabled="loading || !input.trim()" @click="send()">
        {{ loading ? '…' : '发送' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const md = new MarkdownIt()
const messages = ref<any[]>([])
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)

function logout() { auth.logout(); router.push('/login') }

async function send(q?: string) {
  const question = (q || input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  loading.value = true
  messages.value.push({ role: 'user', content: question })
  const assistant = { role: 'assistant', content: '', html: '', sources: [] as any[], trace: null as any }
  messages.value.push(assistant)

  try {
    const resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    })
    if (!resp.ok || !resp.body) throw new Error('请求失败')
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
        if (ev.type === 'sources') assistant.sources = ev.data || []
        else if (ev.type === 'trace') assistant.trace = ev.data
        else if (ev.type === 'delta') {
          assistant.content += (ev.data || '')
          assistant.html = md.render(assistant.content)
        }
      }
    }
  } catch (e: any) {
    assistant.content = '出错了：' + (e.message || '')
    assistant.html = md.render(assistant.content)
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

function scrollBottom() {
  nextTick(() => { if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight })
}
</script>

<style scoped>
.chat-page { height: 100vh; display: flex; flex-direction: column; }
.topbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-size: 18px; font-weight: 600; }
.brand .en { font-size: 12px; color: #999; font-weight: normal; }
.actions { display: flex; align-items: center; gap: 8px; }
.user { font-size: 13px; color: #666; }
.msg-list { flex: 1; overflow-y: auto; padding: 20px; }
.hint { color: #999; text-align: center; margin-top: 40px; }
.msg { display: flex; gap: 10px; margin-bottom: 16px; }
.msg.user { flex-direction: row-reverse; }
.avatar { width: 32px; height: 32px; border-radius: 50%; background: #eef2ff; color: #4338ca; display: flex; align-items: center; justify-content: center; font-size: 13px; flex-shrink: 0; }
.body { max-width: 78%; }
.bubble { padding: 10px 14px; border-radius: 12px; background: #f3f4f6; line-height: 1.6; word-break: break-word; }
.msg.user .bubble { background: #4338ca; color: #fff; }
.trace { margin-top: 8px; }
.prompt { background: #fafafa; border: 1px solid #eee; border-radius: 6px; padding: 10px; font-size: 12px; white-space: pre-wrap; max-height: 240px; overflow-y: auto; }
.sources { margin-top: 8px; font-size: 12px; color: #666; }
.src { background: #fafafa; border: 1px solid #eee; border-radius: 6px; padding: 4px 8px; margin-top: 4px; }
.src a { color: #4338ca; text-decoration: none; }
.src .clause { margin-left: 4px; color: #666; }
.src .score { margin-left: 8px; color: #999; }
.input-bar { display: flex; gap: 8px; padding: 12px 20px; background: #fff; border-top: 1px solid #eee; }
</style>
