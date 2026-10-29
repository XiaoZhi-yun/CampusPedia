import { useEffect, useState } from 'react'

interface Doc {
  id: string
  fileName: string
  category: string
  sourceUrl?: string | null
  charCount: number
  chunkCount: number
  createdAt: string
}

const CATEGORIES = ['学籍与教学管理', '学生管理', '学生奖助', '素质拓展', '学生就业', '医疗保障']

export default function AdminPanel() {
  const [docs, setDocs] = useState<Doc[]>([])
  const [category, setCategory] = useState(CATEGORIES[0])
  const [sourceUrl, setSourceUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)

  async function refresh() {
    try {
      const resp = await fetch('/api/documents')
      if (resp.ok) setDocs(await resp.json())
    } catch {
      /* ignore */
    }
  }

  useEffect(() => {
    refresh()
  }, [])

  async function upload() {
    if (!file) {
      setMsg('请先选择文件')
      return
    }
    setBusy(true)
    setMsg('')
    const fd = new FormData()
    fd.append('file', file)
    fd.append('category', category)
    if (sourceUrl.trim()) fd.append('sourceUrl', sourceUrl.trim())
    try {
      const resp = await fetch('/api/documents', { method: 'POST', body: fd })
      const data = await resp.json()
      if (resp.ok) {
        setMsg('✅ 已上传《' + data.fileName + '》，切片 ' + data.chunkCount + ' 块')
        setFile(null)
        setSourceUrl('')
        refresh()
      } else {
        setMsg('❌ ' + (data.error || '上传失败'))
      }
    } catch (e) {
      setMsg('❌ ' + (e instanceof Error ? e.message : '上传失败'))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: string, fileName: string) {
    if (!window.confirm('确定删除《' + fileName + '》及其切片？')) return
    await fetch('/api/documents/' + id, { method: 'DELETE' })
    refresh()
  }

  return (
    <div className="admin">
      <h2>管理端 · 语料上传</h2>
      <p className="admin-tip">支持 .md / .txt / .pdf / .docx（.doc 请另存为 .docx）。上传后立即可问答。</p>

      <div className="upload-form">
        <input type="file" accept=".md,.txt,.pdf,.docx,.json,.csv" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <input placeholder="官网原文链接（可选）" value={sourceUrl} onChange={(e) => setSourceUrl(e.target.value)} />
        <button onClick={upload} disabled={busy || !file}>
          {busy ? '上传中…' : '上传'}
        </button>
      </div>

      {msg && <div className="admin-msg">{msg}</div>}

      <div className="doc-list">
        {docs.length === 0 && <div className="doc-empty">暂无文档，请上传。</div>}
        {docs.map((d) => (
          <div key={d.id} className="doc-item">
            <div className="doc-info">
              <span className="doc-name">《{d.fileName}》</span>
              <span className="doc-meta">{d.category} · {d.charCount} 字 · {d.chunkCount} 块</span>
              {d.sourceUrl && (
                <a href={d.sourceUrl} target="_blank" rel="noreferrer" className="doc-link">官网 ↗</a>
              )}
            </div>
            <button className="doc-del" onClick={() => remove(d.id, d.fileName)}>删除</button>
          </div>
        ))}
      </div>
    </div>
  )
}
