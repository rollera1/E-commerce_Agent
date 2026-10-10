import { useEffect, useState, type ReactNode } from 'react'
import { requestJson } from './api'
import Icon, { type IconName } from './Icon'
import ProductWorkspace from './ProductWorkspace'

type CheckState = 'idle' | 'loading' | 'ok' | 'error'
type ShopifyConnectionState = 'NOT_CONFIGURED' | 'DISCONNECTED' | 'CONNECTED'
type HealthPayload = { status: string; downstream?: { status: string } }
type ShopifyStore = { myshopifyDomain: string; shopName: string; primaryDomain?: string; currencyCode: string; authorizationStatus: string; lastVerifiedAt: string }
type ShopifyConnectionPayload = { enabled: boolean; configured: boolean; configuredShopDomain?: string; state: ShopifyConnectionState; store?: ShopifyStore }

const statusLabel: Record<CheckState, string> = { idle: '待检查', loading: '检查中', ok: '正常', error: '异常' }

function App() {
  const [javaStatus, setJavaStatus] = useState<CheckState>('idle')
  const [pythonStatus, setPythonStatus] = useState<CheckState>('idle')
  const [databaseStatus, setDatabaseStatus] = useState<CheckState>('idle')
  const [details, setDetails] = useState('点击检查 Java、Python 与 MySQL 服务。')
  const [shopifyConnection, setShopifyConnection] = useState<ShopifyConnectionPayload | null>(null)
  const [shopifyLoading, setShopifyLoading] = useState(false)
  const [shopifyMessage, setShopifyMessage] = useState('正在读取 Shopify 连接状态……')

  async function checkServices() {
    let javaHealthy = false
    let databaseHealthy = false
    setJavaStatus('loading'); setPythonStatus('loading'); setDatabaseStatus('loading')
    setDetails('正在检查服务间连接……')
    try {
      const javaData = await requestJson<HealthPayload>('/api/health')
      if (javaData.status !== 'ok') throw new Error('Java 业务后端返回异常状态')
      setJavaStatus('ok'); javaHealthy = true
      const databaseData = await requestJson<HealthPayload>('/api/health/database')
      if (databaseData.status !== 'ok') throw new Error('Java 无法连接 MySQL 数据库')
      setDatabaseStatus('ok'); databaseHealthy = true
      const pythonData = await requestJson<HealthPayload>('/api/health/python')
      if (pythonData.downstream?.status !== 'ok') throw new Error('Java 无法连接 Python 能力服务')
      setPythonStatus('ok')
      setDetails('Java、Python 与 MySQL 均运行正常，服务间通信已打通。')
    } catch (error) {
      if (!javaHealthy) setJavaStatus('error')
      if (!databaseHealthy) setDatabaseStatus('error')
      setPythonStatus('error')
      setDetails(error instanceof Error ? error.message : '服务检查失败')
    }
  }

  async function loadShopifyConnection() {
    try {
      const payload = await requestJson<ShopifyConnectionPayload>('/api/shopify/connection')
      setShopifyConnection(payload); setShopifyMessage(shopifyStatusMessage(payload))
    } catch (error) { setShopifyMessage(error instanceof Error ? error.message : '读取 Shopify 状态失败') }
  }

  async function connectShopify() {
    setShopifyLoading(true); setShopifyMessage('正在申请短期令牌并验证店铺……')
    try {
      const payload = await requestJson<ShopifyConnectionPayload>('/api/shopify/connection', { method: 'POST' })
      setShopifyConnection(payload)
      setShopifyMessage(`已连接 ${payload.store?.shopName ?? 'Shopify 店铺'}，店铺信息已安全保存。`)
    } catch (error) { setShopifyMessage(error instanceof Error ? error.message : 'Shopify 连接验证失败') }
    finally { setShopifyLoading(false) }
  }

  useEffect(() => { void checkServices(); void loadShopifyConnection() }, [])

  const healthyServices = [javaStatus, pythonStatus, databaseStatus].filter((state) => state === 'ok').length
  const storeConnected = shopifyConnection?.state === 'CONNECTED'

  return (
    <div className="app-shell">
      <aside className="sidebar" aria-label="主导航">
        <a className="brand" href="#overview" aria-label="E-commerce Agent 首页">
          <span className="brand-mark">EA</span>
          <span><strong>E-commerce</strong><small>Agent Console</small></span>
        </a>
        <nav className="side-nav">
          <p className="nav-label">工作台</p>
          <a href="#overview" className="is-active"><Icon name="overview" />运营概览</a>
          <a href="#workflow"><Icon name="workflow" />商品工作流</a>
          <span className="nav-disabled" aria-disabled="true"><Icon name="orders" />订单中心<small>规划中</small></span>
          <span className="nav-disabled" aria-disabled="true"><Icon name="support" />客服助手<small>规划中</small></span>
        </nav>
        <div className="sidebar-note"><Icon name="shield" /><div><strong>安全边界</strong><span>Agent 不接触 Shopify 凭据</span></div></div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div><p className="breadcrumb">商家后台 <span>/</span> 运营概览</p><h1>今天的运营工作台</h1></div>
          <div className="topbar-meta"><span className="environment-badge">开发环境</span><span className={`store-badge ${storeConnected ? 'is-online' : ''}`}><span className="live-dot" />{storeConnected ? shopifyConnection?.store?.shopName : 'Shopify 未连接'}</span></div>
        </header>

        <div className="content-frame">
          <section className="welcome-panel" id="overview">
            <div><p className="eyebrow">MULTI-AGENT COMMERCE OPERATIONS</p><h2>从选品到发布，每个关键步骤都可见、可审核。</h2><p>选品 Agent 检索候选商品，文案 Agent 生成内容，业务工作流负责安全上架。</p></div>
            <a className="primary-link" href="#workflow">开始商品任务 <Icon name="arrow" /></a>
          </section>

          <section className="metrics-grid" aria-label="系统概览">
            <MetricCard label="核心服务" value={`${healthyServices}/3`} note={healthyServices === 3 ? '全部运行正常' : '需要检查'} tone={healthyServices === 3 ? 'success' : 'warning'} icon="server" />
            <MetricCard label="Shopify 店铺" value={storeConnected ? '已连接' : '待验证'} note={shopifyConnection?.store?.currencyCode ? `币种 ${shopifyConnection.store.currencyCode}` : '开发店铺'} tone={storeConnected ? 'success' : 'neutral'} icon="store" />
            <MetricCard label="Agent 链路" value="2 个" note="选品 + 文案/SEO" tone="accent" icon="sparkles" />
            <MetricCard label="当前阶段" value="上架闭环" note="草稿与发布联调" tone="neutral" icon="workflow" />
          </section>

          <section className="overview-grid">
            <article className="surface store-surface" aria-labelledby="shopify-status-title">
              <SectionHeading icon="store" kicker="销售渠道" title="Shopify 开发店铺" id="shopify-status-title">
                <button className="button-secondary" type="button" onClick={connectShopify} disabled={shopifyLoading || !shopifyConnection?.configured}><Icon name="refresh" size={18} />{shopifyLoading ? '正在验证' : '连接并验证'}</button>
              </SectionHeading>
              <div className="store-details">
                <div className="store-state-row"><span className={`large-status-dot status-${shopifyConnection?.state ?? 'loading'}`} /><div><strong>{shopifyConnection ? shopifyStateLabel[shopifyConnection.state] : '读取中'}</strong><span>{shopifyConnection?.configuredShopDomain ?? '需要本地配置'}</span></div></div>
                <dl><div><dt>店铺名称</dt><dd>{shopifyConnection?.store?.shopName ?? '—'}</dd></div><div><dt>主域名</dt><dd>{shopifyConnection?.store?.primaryDomain ?? '—'}</dd></div><div><dt>最近验证</dt><dd>{formatDate(shopifyConnection?.store?.lastVerifiedAt)}</dd></div></dl>
              </div>
              <StatusMessage>{shopifyMessage}</StatusMessage>
            </article>

            <article className="surface service-surface" aria-labelledby="system-status-title">
              <SectionHeading icon="server" kicker="基础设施" title="系统服务" id="system-status-title">
                <button className="icon-button" type="button" onClick={checkServices} disabled={javaStatus === 'loading'} aria-label="重新检查服务" title="重新检查服务"><Icon name="refresh" /></button>
              </SectionHeading>
              <div className="service-list"><ServiceRow title="Java 业务后端" meta="localhost:8085" state={javaStatus} icon="server" /><ServiceRow title="Python 能力服务" meta="localhost:8005" state={pythonStatus} icon="sparkles" /><ServiceRow title="MySQL 数据库" meta="SSH 隧道·localhost:3307" state={databaseStatus} icon="database" /></div>
              <StatusMessage>{details}</StatusMessage>
            </article>
          </section>

          <section className="workflow-map" aria-labelledby="workflow-map-title">
            <div className="workflow-map-copy"><p className="section-kicker">核心闭环</p><h2 id="workflow-map-title">商品上架流程</h2><p>高影响操作保留人工确认。</p></div>
            <ol>{['Agent 选品', '配置上架', '文案生成', '内容审核', 'Shopify 发布'].map((label, index) => <li key={label}><span>{index + 1}</span>{label}</li>)}</ol>
          </section>

          <ProductWorkspace />

          <section className="planned-grid" aria-label="后续规划"><article><Icon name="orders" /><div><span>后续规划</span><h3>订单同步与支付状态</h3><p>展示 Webhook 同步、金额、支付与异常恢复。</p></div></article><article><Icon name="support" /><div><span>后续规划</span><h3>客服 Agent 回复建议</h3><p>基于商品、订单和政策生成建议，不自动发送。</p></div></article></section>
        </div>
      </main>
    </div>
  )
}

function MetricCard({ label, value, note, tone, icon }: { label: string; value: string; note: string; tone: string; icon: IconName }) {
  return <article className={`metric-card tone-${tone}`}><span className="metric-icon"><Icon name={icon} /></span><div><p>{label}</p><strong>{value}</strong><small>{note}</small></div></article>
}

function ServiceRow({ title, meta, state, icon }: { title: string; meta: string; state: CheckState; icon: IconName }) {
  return <div className="service-row"><span className="service-icon"><Icon name={icon} /></span><div><strong>{title}</strong><small>{meta}</small></div><span className={`status-chip status-chip-${state}`}><span />{statusLabel[state]}</span></div>
}

function SectionHeading({ icon, kicker, title, id, children }: { icon: IconName; kicker: string; title: string; id: string; children: ReactNode }) {
  return <div className="section-heading"><div><span className="section-icon"><Icon name={icon} /></span><div><p className="section-kicker">{kicker}</p><h2 id={id}>{title}</h2></div></div>{children}</div>
}

function StatusMessage({ children }: { children: ReactNode }) {
  return <p className="status-message" role="status"><span className="status-message-icon"><Icon name="check" size={16} /></span>{children}</p>
}

const shopifyStateLabel: Record<ShopifyConnectionState, string> = { NOT_CONFIGURED: '等待配置', DISCONNECTED: '等待验证', CONNECTED: '店铺已连接' }
function shopifyStatusMessage(connection: ShopifyConnectionPayload) {
  if (connection.state === 'CONNECTED') return `店铺连接正常，最近验证：${formatDate(connection.store?.lastVerifiedAt)}`
  if (connection.state === 'DISCONNECTED') return '应用配置已读取，请执行真实 Shopify 连接验证。'
  return '尚未启用 Shopify，请先完成本地环境配置。'
}
function formatDate(value?: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }

export default App
