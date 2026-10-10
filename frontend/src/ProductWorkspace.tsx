import { useRef, useState } from 'react'
import { requestJson } from './api'
import Icon from './Icon'

type Money = { amount: string | number; currency: string }
type SourceVariant = {
  sourceVariantId: string
  sku: string
  title: string
  price: Money
  inventoryAvailable?: number | null
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
  inventoryStatus?: 'IN_STOCK' | 'OUT_OF_STOCK' | 'UNKNOWN'
  inventoryTotal?: number | null
  matchedQuery?: string
}
type ProductSelectionSearch = {
  selectionId: string
  query: string
  matchedQuery: string
  outcome: 'CANDIDATES_FOUND' | 'NO_CANDIDATES'
  status: 'AWAITING_CONFIRMATION' | 'CONFIRMED'
  candidates: SourceProduct[]
}
type CopySeoContent = {
  title: string
  bulletPoints: string[]
  description: string
  seoTitle: string
  metaDescription: string
  tags: string[]
}
type AgentTrace = {
  currentStep: string
  outcome: 'COMPLETED' | 'NEEDS_HUMAN_INPUT'
  steps: string[]
  toolCalls: Array<{
    tool: string
    status: 'SUCCEEDED' | 'FAILED'
    durationMs: number
    summary: Record<string, string | number | boolean | null>
    errorType?: string
  }>
  modelAttempts: number
  revisionCount: number
  validationIssues: string[]
  nextAction?: string
}
type CopySeoGeneration = {
  generationId: string
  sourceProductId: string
  targetMarket: string
  language: string
  status: 'SUCCEEDED' | 'FAILED'
  reviewStatus: 'PENDING_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'NOT_APPLICABLE'
  content: CopySeoContent | null
  execution: {
    agent: string
    agentVersion: string
    promptVersion: string
    model: string
    durationMs: number
  }
  trace?: AgentTrace | null
  failure?: { errorType: string; retryable: boolean } | null
}
type ShopifyLocation = { id: string; name: string; active: boolean }
type ShopifyPublication = { id: string; title: string; autoPublish: boolean }
type ListingStatus =
  | 'CONFIGURED' | 'CREATING_DRAFT' | 'DRAFT_CREATED' | 'WRITING_VARIANTS'
  | 'MEDIA_PENDING' | 'READY_TO_PUBLISH' | 'ACTIVATING' | 'PUBLISHING'
  | 'PUBLISHED' | 'FAILED' | 'EDITING'
type ShopifyListing = {
  generationId: string
  reviewMode: 'MANUAL' | 'AUTO'
  publishMode: 'MANUAL' | 'AUTO'
  locationId: string
  publicationId: string
  autoPublishAuthorized: boolean
  status: ListingStatus
  currentStep: string
  shopifyProductId?: string
  imageUrls: string[]
  mediaReady: boolean
  lastErrorCode?: string
  lastErrorMessage?: string
  variants: Array<{
    sourceVariantId: string
    sku: string
    optionValue: string
    sellingPrice: number
    sourceInventory: number
    shopifyVariantId?: string
  }>
}

const emptyContent: CopySeoContent = {
  title: '',
  bulletPoints: [],
  description: '',
  seoTitle: '',
  metaDescription: '',
  tags: [],
}

type ProductTask = {
  language: 'en' | 'zh'
  product: SourceProduct
  selectionId: string
  generation: CopySeoGeneration | null
  listing: ShopifyListing | null
  draft: CopySeoContent
  variantPrices: Record<string, string>
  imageUrls: string[]
  locationId: string
  publicationId: string
  reviewMode: 'MANUAL' | 'AUTO'
}

export default function ProductWorkspace() {
  const [language, setLanguage] = useState<'en' | 'zh'>('en')
  const [products, setProducts] = useState<SourceProduct[]>([])
  const [searchId, setSearchId] = useState<string | null>(null)
  const tasks = useRef(new Map<string, ProductTask>())
  const [selectionId, setSelectionId] = useState<string | null>(null)
  const [selectionConfirmed, setSelectionConfirmed] = useState(false)
  const [selected, setSelected] = useState<SourceProduct | null>(null)
  const [generation, setGeneration] = useState<CopySeoGeneration | null>(null)
  const [listing, setListing] = useState<ShopifyListing | null>(null)
  const [locations, setLocations] = useState<ShopifyLocation[]>([])
  const [publications, setPublications] = useState<ShopifyPublication[]>([])
  const [reviewMode, setReviewMode] = useState<'MANUAL' | 'AUTO'>('MANUAL')
  const [publishMode, setPublishMode] = useState<'MANUAL' | 'AUTO'>('MANUAL')
  const [locationId, setLocationId] = useState('')
  const [publicationId, setPublicationId] = useState('')
  const [variantPrices, setVariantPrices] = useState<Record<string, string>>({})
  const [imageUrls, setImageUrls] = useState<string[]>([])
  const [autoPublishAuthorized, setAutoPublishAuthorized] = useState(false)
  const [draft, setDraft] = useState<CopySeoContent>(emptyContent)
  const [keyword, setKeyword] = useState('')
  const [limitOption, setLimitOption] = useState('10')
  const [customLimit, setCustomLimit] = useState('30')
  const [hasSearched, setHasSearched] = useState(false)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('请输入商品意图，由选品 Agent 检索候选商品。')

  async function runAction(action: () => Promise<void>, fallback: string, pendingMessage?: string) {
    setLoading(true)
    if (pendingMessage) setMessage(pendingMessage)
    try {
      await action()
    } catch (error) {
      setMessage(errorMessage(error, fallback))
    } finally {
      setLoading(false)
    }
  }

  async function searchProducts(searchKeyword: string) {
    if (!searchKeyword.trim()) {
      setMessage('请输入商品关键词，例如“宠物牵引绳”。')
      return
    }
    const limit = Number(limitOption === 'custom' ? customLimit : limitOption)
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
      setMessage('查询数量请输入 1–100 的整数。')
      return
    }
    await runAction(async () => {
      const payload = await requestJson<ProductSelectionSearch>('/api/product-selections/searches', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: searchKeyword.trim(), limit }),
      })
      setProducts(payload.candidates)
      rememberTask()
      setSearchId(payload.selectionId)
      setSelectionId(null)
      setHasSearched(true)
      setSelected(null)
      setSelectionConfirmed(false)
      setGeneration(null)
      setListing(null)
      setLocations([])
      setPublications([])
      setLocationId('')
      setPublicationId('')
      setImageUrls([])
      setReviewMode('MANUAL')
      setPublishMode('MANUAL')
      setAutoPublishAuthorized(false)
      setMessage(payload.candidates.length
        ? `Agent 使用“${payload.matchedQuery}”找到 ${payload.candidates.length} 个候选商品，请选择并确认。`
        : '暂无该商品')
    }, '读取来源商品失败', '选品 Agent 正在检索候选商品……')
  }

  async function generate() {
    if (!selected || !selectionId || !selectionConfirmed) return
    if (generation?.reviewStatus === 'ACCEPTED' || listing) return
    await runAction(async () => {
      let payload = await requestJson<CopySeoGeneration>(
        `/api/product-selections/${encodeURIComponent(selectionId)}/copy-seo-generations`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ targetMarket: 'US', language }),
        },
      )
      setGeneration(payload)
      setListing(null)
      if (payload.content) setDraft(payload.content)
      if (payload.status === 'SUCCEEDED' && payload.content && reviewMode === 'AUTO') {
        payload = await requestJson<CopySeoGeneration>(
          `/api/copy-seo-generations/${encodeURIComponent(payload.generationId)}/accept`,
          { method: 'POST' },
        )
      }
      setGeneration(payload)
      setListing(null)
      if (payload.status === 'FAILED' || !payload.content) {
        setDraft(emptyContent)
        setMessage(`Agent 已停止：${payload.failure?.errorType ?? '未知错误'}，需要人工处理。`)
      } else {
        setDraft(payload.content)
        setMessage(payload.reviewStatus === 'ACCEPTED'
          ? '通过校验的文案已自动接受；尚未创建或发布 Shopify 商品。'
          : '生成完成，请审核内容；接受文案不会创建或发布 Shopify 商品。')
      }
    }, 'Agent 生成失败', '文案与 SEO Agent 正在生成内容……')
  }

  async function confirmSelection() {
    if (!selected || !searchId) return
    await runAction(async () => {
      const rawDetail = await requestJson<SourceProduct>(
        `/api/source-products/${encodeURIComponent(selected.sourceProductId)}`,
      )
      const detail = { ...rawDetail, inventoryTotal: rawDetail.inventoryTotal ?? selected.inventoryTotal }
      setSelected(detail)
      setProducts((items) => items.map((item) => item.sourceProductId === detail.sourceProductId ? detail : item))
      const task = await requestJson<{ selectionId: string }>(
        `/api/product-selections/${encodeURIComponent(searchId)}/confirm`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sourceProductId: selected.sourceProductId }),
        },
      )
      setSelectionId(task.selectionId)
      setSelectionConfirmed(true)
      setVariantPrices(Object.fromEntries(detail.variants.map((variant) => [variant.sourceVariantId, ''])))
      setImageUrls(detail.images.filter((value) => value.startsWith('https://')))
      setAutoPublishAuthorized(false)
      setMessage(`已为 ${detail.title} 确认独立商品任务，可以生成文案。可继续选择其他商品；上架前会重新校验真实变体库存。`)
    }, '选品确认失败', '正在读取商品详情并确认选品……')
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
        ? '已接受生成内容。完成上架配置后，可单独创建 Shopify 草稿。'
        : '已拒绝生成内容。',
    )
  }

  async function loadShopifyChoices() {
    await runAction(async () => {
      const [locationPayload, publicationPayload] = await Promise.all([
        requestJson<ShopifyLocation[]>('/api/shopify/locations'),
        requestJson<ShopifyPublication[]>('/api/shopify/publications'),
      ])
      setLocations(locationPayload)
      setPublications(publicationPayload)
      setMessage('Shopify 配置选项已更新。')
    }, '读取 Shopify 配置失败；不影响文案生成与审核。', '正在读取 Shopify 库存地点和销售渠道……')
  }

  async function createShopifyDraft() {
    if (!generation || generation.reviewStatus !== 'ACCEPTED') return
    const settings = buildListingSettings()
    if (!settings) return
    await runAction(async () => {
      const payload = await requestJson<ShopifyListing>(
        `/api/copy-seo-generations/${encodeURIComponent(generation.generationId)}/shopify-listing/draft`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(settings) },
      )
      setListing(payload)
      setMessage(listingMessage(payload))
    }, '创建 Shopify 草稿失败', '正在创建或更新 Shopify 草稿……')
  }

  function buildListingSettings() {
    if (!selected) return null
    if (!locationId || !publicationId) {
      setMessage('请选择 Shopify 库存地点和销售渠道。')
      return null
    }
    const prices = selected.variants.map((variant) => ({
      sourceVariantId: variant.sourceVariantId,
      price: Number(variantPrices[variant.sourceVariantId]),
    }))
    if (prices.some((value) => !Number.isFinite(value.price) || value.price <= 0)) {
      setMessage('请为所有变体填写大于零的 Shopify 售价。')
      return null
    }
    if (selected.variants.some((variant) => variant.inventoryAvailable == null || variant.inventoryAvailable <= 0)) {
      setMessage('存在库存未知或库存不足的变体，不能进入自动上架流程。')
      return null
    }
    if (publishMode === 'AUTO' && !autoPublishAuthorized) {
      setMessage('请先确认自动发布授权。')
      return null
    }
    const normalizedImages = imageUrls.map((value) => value.trim()).filter(Boolean)
    if (normalizedImages.length === 0 || normalizedImages.some((value) => !value.startsWith('https://'))) {
      setMessage('请至少填写一张有效的 HTTPS 商品图片。')
      return null
    }
    if (new Set(normalizedImages).size !== normalizedImages.length) {
      setMessage('商品图片地址不能重复。')
      return null
    }
    return {
      reviewMode,
      publishMode,
      locationId,
      publicationId,
      autoPublishAuthorized,
      imageUrls: normalizedImages,
      variantPrices: prices,
    }
  }

  function toggleAutoPublish() {
    if (publishMode === 'AUTO') {
      setPublishMode('MANUAL')
      setAutoPublishAuthorized(false)
      return
    }
    confirmAutoPublishAuthorization()
  }

  function confirmAutoPublishAuthorization() {
    const location = locations.find((value) => value.id === locationId)?.name ?? '未选择'
    const publication = publications.find((value) => value.id === publicationId)?.title ?? '未选择'
    const priceSummary = selected?.variants.map((variant) =>
      `${variant.sku}: ${variantPrices[variant.sourceVariantId] || '未填写'}`).join('\n') ?? ''
    if (!locationId || !publicationId || !selected) {
      setMessage('请先选择库存地点、销售渠道并填写售价。')
      return
    }
    if (imageUrls.length === 0 || imageUrls.some((value) => !value.trim().startsWith('https://'))) {
      setMessage('请先填写有效的 HTTPS 商品图片。')
      return
    }
    if (selected.variants.some((variant) => {
      const price = Number(variantPrices[variant.sourceVariantId])
      return !Number.isFinite(price) || price <= 0 || variant.inventoryAvailable == null || variant.inventoryAvailable <= 0
    })) {
      setMessage('请先填写全部变体售价，并确认来源库存大于零。')
      return
    }
    if (window.confirm(`确认允许当前商品自动发布？\n库存地点：${location}\n销售渠道：${publication}\n${priceSummary}`)) {
      setPublishMode('AUTO')
      setAutoPublishAuthorized(true)
    }
  }

  async function listingAction(action: 'publish' | 'retry' | 'reopen') {
    if (!generation) return
    await runAction(async () => {
      const payload = await requestJson<ShopifyListing>(
        `/api/copy-seo-generations/${encodeURIComponent(generation.generationId)}/shopify-listing/${action}`,
        { method: 'POST' },
      )
      setListing(payload)
      if (action === 'reopen') {
        setGeneration({ ...generation, reviewStatus: 'PENDING_REVIEW' })
        setAutoPublishAuthorized(false)
        setImageUrls(payload.imageUrls)
      }
      setMessage(listingMessage(payload))
    }, 'Shopify 上架操作失败')
  }

  async function updateGeneration(
    action: string,
    method: 'PUT' | 'POST',
    body: CopySeoContent | undefined,
    successMessage: string,
  ) {
    if (!generation) return
    await runAction(async () => {
      const payload = await requestJson<CopySeoGeneration>(
        `/api/copy-seo-generations/${encodeURIComponent(generation.generationId)}/${action}`,
        {
          method,
          headers: body ? { 'Content-Type': 'application/json' } : undefined,
          body: body ? JSON.stringify(body) : undefined,
        },
      )
      setGeneration(payload)
      if (payload.content) setDraft(payload.content)
      setMessage(successMessage)
    }, '审核操作失败')
  }

  function rememberTask() {
    if (searchId && selected && selectionId && selectionConfirmed) {
      tasks.current.set(`${searchId}:${selected.sourceProductId}`, {
        product: selected, selectionId, generation, listing, draft,
        variantPrices, imageUrls, locationId, publicationId, reviewMode, language,
      })
    }
  }

  function chooseProduct(product: SourceProduct) {
    rememberTask()
    const task = tasks.current.get(`${searchId}:${product.sourceProductId}`)
    setSelected(task?.product ?? product)
    setSelectionId(task?.selectionId ?? null)
    setSelectionConfirmed(Boolean(task))
    setGeneration(task?.generation ?? null)
    setLanguage(task?.language ?? 'en')
    setListing(task?.listing ?? null)
    setDraft(task?.draft ?? emptyContent)
    setVariantPrices(task?.variantPrices ?? {})
    setImageUrls(task?.imageUrls ?? [])
    setLocationId(task?.locationId ?? '')
    setPublicationId(task?.publicationId ?? '')
    setReviewMode(task?.reviewMode ?? 'MANUAL')
    setPublishMode(task?.listing?.publishMode ?? 'MANUAL')
    setAutoPublishAuthorized(false)
    setMessage(task ? `已切换到 ${product.title} 的独立商品任务。` : `已选择 ${product.title}，请核对商品信息后确认选品。`)
  }

  const pending = generation?.reviewStatus === 'PENDING_REVIEW'
  const languageLocked = loading || generation?.reviewStatus === 'ACCEPTED' || Boolean(listing)
  const languageChanged = Boolean(generation?.content && generation.language !== language)
  function languageControl(id: string) {
    return <div className="copy-language"><label htmlFor={id}>文案语言<select id={id} value={language} disabled={languageLocked} onChange={(event) => setLanguage(event.target.value as 'en' | 'zh')}><option value="en">英文（店铺区域语言）</option><option value="zh">简体中文</option></select></label><small>适用于标题、卖点、描述、SEO 与标签。{languageChanged ? '当前内容仍为原语言，请重新生成。' : '切换语言后点击生成，不会自动翻译已有内容。'}</small></div>
  }
  const configLocked = Boolean(listing && !['CONFIGURED', 'EDITING'].includes(listing.status))
  const listingConfigReady = Boolean(
    selectionConfirmed && locationId && publicationId && imageUrls.some((url) => url.trim().startsWith('https://'))
      && selected?.variants.every((variant) => Number(variantPrices[variant.sourceVariantId]) > 0)
      && selected?.variants.length && selected.variants.every((variant) => variant.inventoryAvailable != null && variant.inventoryAvailable > 0),
  )
  const workflowSteps = [
    { label: '确认选品', done: selectionConfirmed },
    { label: '生成内容', done: Boolean(generation?.content) },
    { label: '内容审核', done: generation?.reviewStatus === 'ACCEPTED' },
    { label: '上架配置', done: listingConfigReady },
    { label: '正式发布', done: listing?.status === 'PUBLISHED' },
  ]
  const firstPendingStep = workflowSteps.findIndex((step) => !step.done)
  const activeStep = firstPendingStep === -1 ? workflowSteps.length - 1 : firstPendingStep

  return (
    <section id="workflow" className="workspace-section" aria-labelledby="product-workspace-title">
      <div className="workspace-header">
        <div>
          <p className="section-kicker">核心业务闭环</p>
          <h2 id="product-workspace-title">商品自动化工作流</h2>
          <p className="section-description">从来源选品、Agent 文案生成到 Shopify 发布，关键动作均保留人工确认。</p>
        </div>
        <form className="product-search" onSubmit={(event) => { event.preventDefault(); void searchProducts(keyword) }}>
          <label htmlFor="product-keyword">搜索商品意图</label>
          <div className="search-control">
            <Icon name="search" size={19} />
            <input id="product-keyword" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="例如：宠物牵引绳" />
            <button type="submit" disabled={loading}>{loading ? '检索中' : '开始选品'}</button>
          </div>
          <div className="search-quantity">
            <label htmlFor="product-limit">查询数量</label>
            <select id="product-limit" value={limitOption} disabled={loading} onChange={(event) => setLimitOption(event.target.value)}>
              {[10, 20, 50, 100].map((limit) => <option key={limit} value={limit}>{limit} 条</option>)}
              <option value="custom">自定义</option>
            </select>
            {limitOption === 'custom' && <label className="custom-quantity">自定义数量<input type="number" min="1" max="100" step="1" required value={customLimit} disabled={loading} onChange={(event) => setCustomLimit(event.target.value)} /></label>}
          </div>
          <small className="search-hint">仅展示汇总库存大于 0、来源资料完整的商品；过滤后数量可能减少。</small>
        </form>
      </div>

      <ol className="workflow-steps" aria-label="商品上架流程">
        {workflowSteps.map((step, index) => (
          <li key={step.label} className={`${step.done ? 'is-done' : ''} ${index === activeStep ? 'is-current' : ''}`}>
            <span>{step.done ? <Icon name="check" size={16} /> : index + 1}</span>
            <small>{step.label}</small>
          </li>
        ))}
      </ol>

      <div className="workspace-message" role="status" aria-live="polite">
        <span className={loading ? 'message-pulse' : ''}><Icon name={loading ? 'refresh' : 'sparkles'} size={18} /></span>
        <p>{message}</p>
      </div>

      <div className={`product-layout ${products.length === 0 ? 'is-empty' : ''}`}>
        {products.length > 0 && (
          <aside className="product-list" aria-label="来源商品候选列表">
            <div className="subsection-heading"><div><p className="field-label">候选商品</p><h3>{products.length} 个匹配结果</h3></div></div>
            <div className="candidate-list">
              {products.map((product, index) => (
                <button className={`product-list-item ${selected?.sourceProductId === product.sourceProductId ? 'is-selected' : ''}`} key={product.sourceProductId} type="button" disabled={loading} aria-pressed={selected?.sourceProductId === product.sourceProductId} onClick={() => chooseProduct(product)}>
                  <ProductImage product={product} />
                  <span className="candidate-copy"><small>候选 {String(index + 1).padStart(2, '0')}</small><strong>{product.title}</strong><span>{formatMoney(product.price)}</span></span>
                  <span className={`inventory-state is-${inventoryState(product)}`}><i className={`inventory-dot is-${inventoryState(product)}`} />{inventoryLabel(product)}</span>
                </button>
              ))}
            </div>
          </aside>
        )}

        <article className="product-detail">
          {selected ? (
            <>
              <div className="detail-main">
                <ProductImage product={selected} large />
                <div className="detail-copy">
                  <div className="detail-badges"><span className="source-badge">{selected.source}</span>{selectionConfirmed && <span className="confirmed-badge"><Icon name="check" size={14} /> 已确认</span>}</div>
                  <h3>{selected.title}</h3>
                  <p>{descriptionText(selected.description) || '来源商品暂未提供描述。'}</p>
                  {selected.sourceUrl && <a className="source-link" href={selected.sourceUrl} target="_blank" rel="noreferrer">查看来源商品 <Icon name="external" size={15} /></a>}
                </div>
              </div>
              <dl className="product-facts">
                <div><dt>来源价格</dt><dd>{formatMoney(selected.price)}</dd></div>
                <div><dt>变体数量</dt><dd>{selected.variants.length}</dd></div>
                <div><dt>可用库存（商品汇总）</dt><dd>{selected.inventoryTotal ?? '未知'}</dd></div>
                <div><dt>数据时间</dt><dd>{new Date(selected.fetchedAt).toLocaleString('zh-CN')}</dd></div>
              </dl>
              {languageControl('product-copy-language')}
              <div className="review-actions primary-actions">
                <button type="button" onClick={confirmSelection} disabled={loading || selectionConfirmed}>{selectionConfirmed ? '商品已确认' : '确认选择此商品'}</button>
                <button type="button" className="secondary-button" onClick={generate} disabled={languageLocked || !selectionConfirmed}><Icon name="sparkles" size={17} />{loading ? '处理中' : generation?.content ? '重新生成文案与 SEO' : '生成文案与 SEO'}</button>
              </div>
              <p className="action-feedback" role="status" aria-live="polite" aria-busy={loading}>{message}</p>
              <label className="switch-card"><span><strong>自动接受通过校验的文案</strong><small>只接受内容，不创建或发布商品；默认由商家人工审核。</small></span><input type="checkbox" aria-label="自动接受文案" checked={reviewMode === 'AUTO'} disabled={loading || Boolean(generation)} onChange={(event) => setReviewMode(event.target.checked ? 'AUTO' : 'MANUAL')} /></label>
            </>
          ) : (
            <div className="empty-state"><span><Icon name="search" size={28} /></span><h3>{products.length ? '请选择一个候选商品' : hasSearched ? '暂无该商品' : '输入关键词开始选品'}</h3><p>{products.length ? '可分别确认多个商品，每个商品的文案和上架任务互不覆盖。' : hasSearched ? '未找到汇总库存大于 0 且来源资料完整的商品，可调整关键词重试。' : '选品 Agent 将检索真实来源商品，并返回价格、汇总库存与变体信息。'}</p></div>
          )}
        </article>
      </div>

      {selected && generation?.content && (
        <section className="generation-section" aria-labelledby="generation-title">
          <div className="subsection-heading"><div><p className="field-label">步骤 2–3 · Agent 生成与审核</p><h3 id="generation-title">原始内容与优化结果</h3></div><span className={`review-state review-state--${generation.reviewStatus}`}>{reviewLabel(generation.reviewStatus)}</span></div>
          <div className="generation-language-toolbar">
            {languageControl('review-copy-language')}
            <button type="button" className="secondary-button" disabled={languageLocked} onClick={generate}>{loading ? '处理中' : '按所选语言重新生成'}</button>
            <small>当前内容：{generation.language === 'zh' ? '简体中文' : '英文'}。重新生成将替换本次编辑内容，并重新进入审核。</small>
          </div>
          <div className="comparison" role="group" aria-label="原始内容与生成内容比较">
            <article className="copy-column original-copy"><p className="field-label">来源原文</p><h4>{selected.title}</h4><p>{descriptionText(selected.description) || '暂无来源描述。'}</p></article>
            <article className="copy-column generated-copy"><p className="field-label">Agent 生成内容</p><CopyEditor content={draft} disabled={!pending || loading} onChange={setDraft} /><div className="review-actions"><button type="button" className="secondary-button" disabled={!pending || loading} onClick={saveDraft}>保存修改</button><button type="button" disabled={!pending || loading} onClick={() => void decide('accept')}>接受内容</button><button type="button" className="danger-button" disabled={!pending || loading} onClick={() => void decide('reject')}>拒绝内容</button></div><p className="action-feedback" role="status" aria-live="polite">{message}</p><small className="execution-note">{generation.execution.agent} · {generation.execution.model} · 提示词 {generation.execution.promptVersion}</small></article>
          </div>
          {generation.trace && <AgentTracePanel trace={generation.trace} failure={generation.failure} />}
        </section>
      )}

      {selected && generation?.reviewStatus === 'ACCEPTED' && (
        <section className="listing-config" aria-labelledby="listing-config-title">
          <div className="subsection-heading">
            <div><p className="field-label">步骤 4 · Shopify 配置</p><h3 id="listing-config-title">配置并创建商品草稿</h3></div>
            {configLocked && <span className="review-state">草稿已锁定配置</span>}
          </div>
          <div className="listing-switches">
            <p>文案已人工接受。创建草稿前需确认库存、售价、图片和销售渠道。</p>
            <label className="switch-card"><span><strong>自动发布 Shopify</strong><small>需逐个商品确认授权，不保存长期授权</small></span><input type="checkbox" checked={publishMode === 'AUTO'} disabled={configLocked || loading} onChange={toggleAutoPublish} /></label>
          </div>
          <button type="button" className="secondary-button" disabled={loading} onClick={loadShopifyChoices}>读取 / 刷新 Shopify 配置</button>
          <div className="listing-selects">
            <label>库存地点<select value={locationId} disabled={configLocked || loading || Boolean(listing?.shopifyProductId)} onChange={(event) => { setLocationId(event.target.value); setAutoPublishAuthorized(false) }}><option value="">请选择 Location</option>{locations.map((location) => <option key={location.id} value={location.id}>{location.name}</option>)}</select></label>
            <label>销售渠道<select value={publicationId} disabled={configLocked || loading} onChange={(event) => { setPublicationId(event.target.value); setAutoPublishAuthorized(false) }}><option value="">请选择 Publication</option>{publications.map((publication) => <option key={publication.id} value={publication.id}>{publication.title}</option>)}</select></label>
          </div>
          <div className="config-block">
            <div className="config-title"><span><Icon name="store" size={18} /></span><div><strong>变体售价</strong><small>所有变体必须有可用库存和大于零的售价</small></div></div>
            <div className="variant-pricing">{selected.variants.map((variant) => (
              <label key={variant.sourceVariantId}><span><strong>{variant.title}</strong><small>{variant.sku} · 来源价 {formatMoney(variant.price)}</small></span><input type="number" min="0.01" step="0.01" placeholder="售价" aria-label={`${variant.title} Shopify 售价`} value={variantPrices[variant.sourceVariantId] ?? ''} disabled={configLocked || loading} onChange={(event) => { setVariantPrices({ ...variantPrices, [variant.sourceVariantId]: event.target.value }); setAutoPublishAuthorized(false) }} /></label>
            ))}</div>
          </div>
          <label className="listing-images"><span className="config-title"><span><Icon name="image" size={18} /></span><span><strong>商品图片</strong><small>每行一个 HTTPS 地址，至少保留一张</small></span></span><textarea rows={4} value={imageUrls.join('\n')} disabled={configLocked || loading} onChange={(event) => { setImageUrls(event.target.value.split(/\r?\n/)); setAutoPublishAuthorized(false) }} /><small>退回编辑后可更新图片，并继续使用同一个 Shopify Product。</small></label>
          {publishMode === 'AUTO' && <div className={`authorization-panel ${autoPublishAuthorized ? 'is-authorized' : ''}`}><Icon name="shield" size={20} /><div><strong>{autoPublishAuthorized ? '当前商品已授权自动发布' : '需要确认当前商品的发布授权'}</strong><p>{autoPublishAuthorized ? '配置发生变化后，授权会自动失效。' : '确认库存地点、销售渠道、售价和图片后再授权。'}</p></div>{!autoPublishAuthorized && <button type="button" onClick={confirmAutoPublishAuthorization}>确认授权</button>}</div>}
          <div className="review-actions"><button type="button" disabled={loading || Boolean(listing && listing.status !== 'EDITING')} onClick={createShopifyDraft}>创建 / 更新 Shopify 草稿</button></div>
          <p className="action-feedback" role="status" aria-live="polite">{message}</p>
        </section>
      )}

      {listing && (
        <section className="listing-progress" aria-label="Shopify 上架进度">
          <div className="subsection-heading"><div><p className="field-label">步骤 5 · Shopify 上架任务</p><h3>{listingStatusLabel(listing.status)}</h3></div><span className={`review-state review-state--${listing.status}`}>{listingStepLabel(listing.currentStep)}</span></div>
          <ol className="listing-steps">{['DRAFT', 'VARIANTS', 'MEDIA', 'ACTIVATE', 'PUBLISH', 'COMPLETE'].map((step) => <li key={step} className={step === listing.currentStep ? 'is-current' : ''}><span />{listingStepLabel(step)}</li>)}</ol>
          {listing.shopifyProductId && <p className="resource-id">Shopify Product GID：{listing.shopifyProductId}</p>}
          {listing.lastErrorMessage && <p className="trace-error">{listing.lastErrorCode}：{listing.lastErrorMessage}</p>}
          <div className="review-actions"><button type="button" disabled={loading || listing.status !== 'READY_TO_PUBLISH'} onClick={() => void listingAction('publish')}>确认正式发布</button><button type="button" className="secondary-button" disabled={loading || !['FAILED', 'MEDIA_PENDING'].includes(listing.status)} onClick={() => void listingAction('retry')}>重试失败步骤</button><button type="button" className="secondary-button" disabled={loading || listing.status === 'PUBLISHED'} onClick={() => void listingAction('reopen')}>退回编辑</button></div>
        </section>
      )}
      {generation?.status === 'FAILED' && generation.trace && <AgentTracePanel trace={generation.trace} failure={generation.failure} />}
    </section>
  )
}

function ProductImage({ product, large = false }: { product: SourceProduct; large?: boolean }) {
  const source = product.images.find((image) => image.startsWith('https://'))
  return <span className={`product-image ${large ? 'is-large' : ''}`}>{source ? <img src={source} alt={`${product.title} 商品图`} loading="lazy" /> : <Icon name="image" size={large ? 34 : 22} />}</span>
}

function inventoryState(product: SourceProduct) {
  if (product.inventoryTotal != null && product.inventoryTotal > 0) return 'stock'
  if (product.inventoryTotal == null) return 'unknown'
  return 'out'
}

function inventoryLabel(product: SourceProduct) {
  return { stock: `库存 ${product.inventoryTotal}`, unknown: '待核验', out: '缺货' }[inventoryState(product)]
}

function descriptionText(html: string) {
  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('script, style, iframe, object').forEach((node) => node.remove())
  template.content.querySelectorAll('br').forEach((node) => node.replaceWith('\n'))
  return (template.content.textContent ?? '').replace(/\s+/g, ' ').trim()
}

function AgentTracePanel({ trace, failure }: {
  trace: AgentTrace
  failure?: CopySeoGeneration['failure']
}) {
  return (
    <details className="agent-trace" open={trace.outcome === 'NEEDS_HUMAN_INPUT'}>
      <summary>
        Agent 执行轨迹 · {trace.outcome === 'COMPLETED' ? '已完成' : '需人工处理'}
      </summary>
      <div className="trace-facts">
        <span>当前步骤：{trace.currentStep}</span>
        <span>模型执行：{trace.modelAttempts} 次</span>
        <span>内容修订：{trace.revisionCount} 次</span>
        <span>下一步：{trace.nextAction ?? '—'}</span>
      </div>
      {failure && <p className="trace-error">失败类型：{failure.errorType} · {failure.retryable ? '可重试' : '需人工处理'}</p>}
      <ol className="tool-trace-list">
        {trace.toolCalls.map((call, index) => (
          <li key={`${call.tool}-${index}`}>
            <strong>{call.tool}</strong>
            <span>{call.status === 'SUCCEEDED' ? '成功' : `失败：${call.errorType ?? '未知'}`}</span>
            <small>{call.durationMs} ms</small>
          </li>
        ))}
      </ol>
      {trace.validationIssues.length > 0 && (
        <p className="trace-error">校验问题：{trace.validationIssues.join('；')}</p>
      )}
    </details>
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
  return {
    PENDING_REVIEW: '等待审核',
    ACCEPTED: '已接受',
    REJECTED: '已拒绝',
    NOT_APPLICABLE: '无需审核',
  }[status]
}

function listingStatusLabel(status: ListingStatus) {
  return {
    CONFIGURED: '等待文案审核',
    CREATING_DRAFT: '正在创建草稿',
    DRAFT_CREATED: '草稿已创建',
    WRITING_VARIANTS: '正在写入变体与库存',
    MEDIA_PENDING: '等待图片处理',
    READY_TO_PUBLISH: '草稿完整，等待发布',
    ACTIVATING: '正在激活商品',
    PUBLISHING: '正在发布到销售渠道',
    PUBLISHED: '已正式发布',
    FAILED: '上架失败',
    EDITING: '已退回编辑',
  }[status]
}

function listingStepLabel(step: string) {
  return {
    DRAFT: '创建草稿',
    VARIANTS: '变体/售价/库存',
    MEDIA: '图片处理',
    ACTIVATE: '激活商品',
    PUBLISH: '发布渠道',
    COMPLETE: '完成',
  }[step] ?? step
}

function listingMessage(listing: ShopifyListing) {
  if (listing.status === 'PUBLISHED') return 'Shopify 商品已激活并发布到所选销售渠道。'
  if (listing.status === 'READY_TO_PUBLISH') return 'Shopify 草稿已完整创建，等待人工确认发布。'
  if (listing.status === 'MEDIA_PENDING') return 'Shopify 正在处理图片，请稍后重试媒体检查。'
  if (listing.status === 'FAILED') return listing.lastErrorMessage ?? 'Shopify 上架失败，需要人工处理。'
  if (listing.status === 'EDITING') return '草稿已退回编辑，修改后请重新审核。'
  return listingStatusLabel(listing.status)
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}
