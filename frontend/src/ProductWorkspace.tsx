import { useEffect, useState } from 'react'

type Money = { amount: string | number; currency: string }
type SourceVariant = {
  sourceVariantId: string
  sku: string
  title: string
  price: Money
  inventoryAvailable?: number
}
type SourceProduct = {
  source: string
  sourceProductId: string
  title: string
  description: string
  images: string[]
  price: Money
  variants: SourceVariant[]
  sourceUrl: string
  fetchedAt: string
}
type SourceProductPage = { items: SourceProduct[]; total: number }
type CopySeoContent = {
  title: string
  bulletPoints: string[]
  description: string
  seoTitle: string
  metaDescription: string
  tags: string[]
}
type CopySeoGeneration = {
  generationId: string
  sourceProductId: string
  targetMarket: string
  language: string
  status: 'SUCCEEDED' | 'FAILED'
  reviewStatus: 'PENDING_REVIEW' | 'ACCEPTED' | 'REJECTED'
  content: CopySeoContent
  execution: {
    agent: string
    agentVersion: string
    promptVersion: string
    model: string
    durationMs: number
  }
}

const emptyContent: CopySeoContent = {
  title: '',
  bulletPoints: [],
  description: '',
  seoTitle: '',
  metaDescription: '',
  tags: [],
}

export default function ProductWorkspace() {
  const [products, setProducts] = useState<SourceProduct[]>([])
  const [selected, setSelected] = useState<SourceProduct | null>(null)
  const [generation, setGeneration] = useState<CopySeoGeneration | null>(null)
  const [draft, setDraft] = useState<CopySeoContent>(emptyContent)
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('正在读取来源商品……')

  async function loadProducts(searchKeyword = '') {
    setLoading(true)
    try {
      const query = new URLSearchParams({ page: '1', pageSize: '20' })
      if (searchKeyword.trim()) query.set('keyword', searchKeyword.trim())
      const response = await fetch(`/api/source-products?${query}`)
      const payload = await readJson<SourceProductPage>(response)
      setProducts(payload.items)
      setSelected(payload.items[0] ?? null)
      setGeneration(null)
      setMessage(payload.total ? `读取到 ${payload.total} 个来源商品。` : '没有匹配的来源商品。')
    } catch (error) {
      setMessage(errorMessage(error, '读取来源商品失败'))
    } finally {
      setLoading(false)
    }
  }

  async function generate() {
    if (!selected) return
    setLoading(true)
    setMessage('文案与 SEO Agent 正在生成内容……')
    try {
      const response = await fetch(
        `/api/source-products/${encodeURIComponent(selected.sourceProductId)}/copy-seo-generations`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ targetMarket: 'US', language: 'en' }),
        },
      )
      const payload = await readJson<CopySeoGeneration>(response)
      setGeneration(payload)
      setDraft(payload.content)
      setMessage('生成完成，结果正在等待商家审核，不会自动创建 Shopify 商品。')
    } catch (error) {
      setMessage(errorMessage(error, 'Agent 生成失败'))
    } finally {
      setLoading(false)
    }
  }

  async function saveDraft() {
    if (!generation) return
    await updateGeneration('content', 'PUT', draft, '修改已保存，结果仍在等待审核。')
  }

  async function decide(decision: 'accept' | 'reject') {
    if (!generation) return
    await updateGeneration(
      decision,
      'POST',
      undefined,
      decision === 'accept'
        ? '已接受生成内容；本阶段不会自动创建 Shopify 商品。'
        : '已拒绝生成内容。',
    )
  }

  async function updateGeneration(
    action: string,
    method: 'PUT' | 'POST',
    body: CopySeoContent | undefined,
    successMessage: string,
  ) {
    if (!generation) return
    setLoading(true)
    try {
      const response = await fetch(
        `/api/copy-seo-generations/${encodeURIComponent(generation.generationId)}/${action}`,
        {
          method,
          headers: body ? { 'Content-Type': 'application/json' } : undefined,
          body: body ? JSON.stringify(body) : undefined,
        },
      )
      const payload = await readJson<CopySeoGeneration>(response)
      setGeneration(payload)
      setDraft(payload.content)
      setMessage(successMessage)
    } catch (error) {
      setMessage(errorMessage(error, '审核操作失败'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadProducts()
  }, [])

  const pending = generation?.reviewStatus === 'PENDING_REVIEW'

  return (
    <section className="panel product-workspace" aria-labelledby="product-workspace-title">
      <div className="panel-heading">
        <div>
          <p className="section-kicker">第三周 · 首个 Agent 用例</p>
          <h2 id="product-workspace-title">来源商品与文案审核</h2>
        </div>
        <form
          className="product-search"
          onSubmit={(event) => {
            event.preventDefault()
            void loadProducts(keyword)
          }}
        >
          <input
            aria-label="搜索来源商品"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索来源商品"
          />
          <button type="submit" disabled={loading}>搜索</button>
        </form>
      </div>

      <div className="product-layout">
        <aside className="product-list" aria-label="来源商品列表">
          {products.map((product) => (
            <button
              className={`product-list-item ${selected?.sourceProductId === product.sourceProductId ? 'is-selected' : ''}`}
              key={product.sourceProductId}
              type="button"
              onClick={() => {
                setSelected(product)
                setGeneration(null)
                setMessage(`已选择 ${product.title}。`)
              }}
            >
              <span>{product.title}</span>
              <small>{formatMoney(product.price)}</small>
            </button>
          ))}
        </aside>

        <div className="product-detail">
          {selected ? (
            <>
              <div className="detail-heading">
                <div>
                  <span className="source-badge">{selected.source}</span>
                  <h3>{selected.title}</h3>
                  <p>{selected.description}</p>
                </div>
                <button type="button" onClick={generate} disabled={loading}>
                  {loading ? '处理中' : 'AI 生成/优化'}
                </button>
              </div>
              <dl className="product-facts">
                <div><dt>价格</dt><dd>{formatMoney(selected.price)}</dd></div>
                <div><dt>SKU</dt><dd>{selected.variants[0]?.sku ?? '—'}</dd></div>
                <div><dt>库存</dt><dd>{selected.variants[0]?.inventoryAvailable ?? '未知'}</dd></div>
                <div><dt>抓取时间</dt><dd>{new Date(selected.fetchedAt).toLocaleString('zh-CN')}</dd></div>
              </dl>
            </>
          ) : <p>请选择一个来源商品。</p>}
        </div>
      </div>

      {selected && generation && (
        <div className="comparison" aria-label="原始内容与生成内容比较">
          <article className="copy-column original-copy">
            <p className="field-label">来源原文</p>
            <h3>{selected.title}</h3>
            <p>{selected.description}</p>
          </article>
          <article className="copy-column generated-copy">
            <div className="review-heading">
              <p className="field-label">Agent 生成内容</p>
              <span className={`review-state review-state--${generation.reviewStatus}`}>
                {reviewLabel(generation.reviewStatus)}
              </span>
            </div>
            <CopyEditor content={draft} disabled={!pending || loading} onChange={setDraft} />
            <div className="review-actions">
              <button type="button" className="secondary-button" disabled={!pending || loading} onClick={saveDraft}>
                保存修改
              </button>
              <button type="button" disabled={!pending || loading} onClick={() => void decide('accept')}>
                接受内容
              </button>
              <button type="button" className="danger-button" disabled={!pending || loading} onClick={() => void decide('reject')}>
                拒绝内容
              </button>
            </div>
            <small className="execution-note">
              {generation.execution.agent} · {generation.execution.model} · 提示词 {generation.execution.promptVersion}
            </small>
          </article>
        </div>
      )}

      <p className="result-message" role="status">{message}</p>
    </section>
  )
}

function CopyEditor({ content, disabled, onChange }: {
  content: CopySeoContent
  disabled: boolean
  onChange: (content: CopySeoContent) => void
}) {
  const set = <K extends keyof CopySeoContent>(key: K, value: CopySeoContent[K]) =>
    onChange({ ...content, [key]: value })
  return (
    <div className="copy-editor">
      <label>标题<input disabled={disabled} value={content.title} onChange={(e) => set('title', e.target.value)} /></label>
      <label>卖点（每行一个）<textarea disabled={disabled} value={content.bulletPoints.join('\n')} onChange={(e) => set('bulletPoints', lines(e.target.value))} /></label>
      <label>商品描述<textarea disabled={disabled} value={content.description} onChange={(e) => set('description', e.target.value)} /></label>
      <label>SEO 标题<input disabled={disabled} maxLength={70} value={content.seoTitle} onChange={(e) => set('seoTitle', e.target.value)} /></label>
      <label>Meta Description<textarea disabled={disabled} maxLength={160} value={content.metaDescription} onChange={(e) => set('metaDescription', e.target.value)} /></label>
      <label>标签（每行一个）<textarea disabled={disabled} value={content.tags.join('\n')} onChange={(e) => set('tags', lines(e.target.value))} /></label>
    </div>
  )
}

function lines(value: string) {
  return value.split('\n').map((line) => line.trim()).filter(Boolean)
}

function formatMoney(money: Money) {
  return `${money.currency} ${Number(money.amount).toFixed(2)}`
}

function reviewLabel(status: CopySeoGeneration['reviewStatus']) {
  return { PENDING_REVIEW: '等待审核', ACCEPTED: '已接受', REJECTED: '已拒绝' }[status]
}

async function readJson<T>(response: Response): Promise<T> {
  const payload = await response.json() as T & { message?: string }
  if (!response.ok) throw new Error(payload.message ?? `请求失败（${response.status}）`)
  return payload
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
