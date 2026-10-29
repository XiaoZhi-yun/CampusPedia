import { useRef, useState } from 'react'

interface Source {
  index: number
  fileName: string
  category: string
  clauseRef: string
  snippet: string
  similarity: number
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
}

interface SseEvent {
  type: string
  data?: unknown
}

/** 解析单个 SSE 事件块（data: {...}） */
function parseSse(raw: string): SseEvent {
  let dataStr = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('data:')) dataStr += line.slice(5).trimStart()
  }
  if (!dataStr) return { type: 'done' }
  try {
    return JSON.parse(dataStr) as SseEvent
  } catch {
    return { type: 'error', data: dataStr }
  }
}

export default function App() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)

  async function ask() {
    const q = input.trim()
    if (!q || loading) return
    setInput('')
    setError('')
    setLoading(true)
    setMessages((prev) => [...prev, { role: 'user', content: q }, { role: 'assistant', content: '' }])

    try {
      const resp = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: q }),
      })
      if (!resp.ok || !resp.body) throw new Error('请求失败（' + resp.status + '）')

      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let sources: Source[] = []

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

        let idx: number
        while ((idx = buffer.indexOf('\n\n')) >= 0) {
          const raw = buffer.slice(0, idx)
          buffer = buffer.slice(idx + 2)
          const ev = parseSse(raw)

          if (ev.type === 'sources') {
            sources = (ev.data as Source[]) ?? []
            updateLast((m) => ({ ...m, sources }))
          } else if (ev.type === 'delta') {
            const chunk = String(ev.data ?? '')
            updateLast((m) => ({ ...m, content: m.content + chunk }))
          } else if (ev.type === 'error') {
            setError(String(ev.data ?? '出错了'))
          }
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '出错了')
    } finally {
      setLoading(false)
    }
  }

  function updateLast(fn: (m: Message) => Message) {
    setMessages((prev) => {
      if (prev.length === 0) return prev
      const next = [...prev]
      next[next.length - 1] = fn(next[next.length - 1])
      return next
    })
    requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }))
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>学事通</h1>
        <p>CampusPedia · 校园学生事务智能问答</p>
      </header>

      <div className="chat" ref={listRef}>
        {messages.length === 0 && (
          <div className="hint">
            试试问我：<button onClick={() => setInput('转专业需要什么条件？')}>转专业需要什么条件？</button>
            <button onClick={() => setInput('国家奖学金的申请条件？')}>国家奖学金的申请条件？</button>
          </div>
        )}
        {messages.map((m, i) => (
          <div key={i} className={'msg ' + m.role}>
            <div className="bubble">
              {m.content || (loading && i === messages.length - 1 ? '思考中…' : '')}
            </div>
            {m.sources && m.sources.length > 0 && (
              <div className="sources">
                {m.sources.map((s) => (
                  <div key={s.index} className="source">
                    <span className="src-badge">[来源{s.index}]</span>《{s.fileName}》{s.clauseRef}
                    <span className="src-sim">相似度 {s.similarity.toFixed(2)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {error && <div className="error">⚠️ {error}</div>}

      <form
        className="input-bar"
        onSubmit={(e) => {
          e.preventDefault()
          ask()
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="输入你的校园问题…"
          disabled={loading}
        />
        <button type="submit" disabled={loading || !input.trim()}>
          {loading ? '…' : '发送'}
        </button>
      </form>
    </div>
  )
}
