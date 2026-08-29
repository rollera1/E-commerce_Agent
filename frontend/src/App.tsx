import { useEffect, useState } from 'react'

type CheckState = 'idle' | 'loading' | 'ok' | 'error'
type ShopifyConnectionState = 'NOT_CONFIGURED' | 'DISCONNECTED' | 'CONNECTED'

type HealthPayload = {
  status: string
  service?: string
  caller?: string
  downstream?: {
    status: string
    service: string
    version: string
  }
}

type ShopifyStore = {
  myshopifyDomain: string
  shopName: string
  primaryDomain?: string
  currencyCode: string
  authorizationStatus: string
  lastVerifiedAt: string
}

type ShopifyConnectionPayload = {
  enabled: boolean
  configured: boolean
  configuredShopDomain?: string
  state: ShopifyConnectionState
  store?: ShopifyStore
}

type ApiErrorPayload = {
  message?: string
}

const statusLabel: Record<CheckState, string> = {
  idle: '等待检查',
  loading: '检查中',
  ok: '运行正常',
  error: '连接失败',
}

function App() {
  const [javaStatus, setJavaStatus] = useState<CheckState>('idle')
  const [pythonStatus, setPythonStatus] = useState<CheckState>('idle')
  const [databaseStatus, setDatabaseStatus] = useState<CheckState>('idle')
  const [details, setDetails] = useState('点击按钮检查 Java、Python 与 MySQL。')
  const [shopifyConnection, setShopifyConnection] = useState<ShopifyConnectionPayload | null>(null)
  const [shopifyLoading, setShopifyLoading] = useState(false)
  const [shopifyMessage, setShopifyMessage] = useState('正在读取 Shopify 连接状态……')

  async function checkServices() {
    let javaHealthy = false
    let databaseHealthy = false
    setJavaStatus('loading')
    setPythonStatus('loading')
    setDatabaseStatus('loading')
    setDetails('正在请求 Java 健康检查……')

    try {
      const javaResponse = await fetch('/api/health')
      const javaData = (await javaResponse.json()) as HealthPayload
      if (!javaResponse.ok || javaData.status !== 'ok') {
        throw new Error('Java 服务返回异常状态')
      }
      setJavaStatus('ok')
      javaHealthy = true

      const databaseResponse = await fetch('/api/health/database')
      const databaseData = (await databaseResponse.json()) as HealthPayload
      if (!databaseResponse.ok || databaseData.status !== 'ok') {
        throw new Error('Java 无法连接 MySQL 数据库')
      }
      setDatabaseStatus('ok')
      databaseHealthy = true

      const pythonResponse = await fetch('/api/health/python')
      const pythonData = (await pythonResponse.json()) as HealthPayload
      if (!pythonResponse.ok || pythonData.downstream?.status !== 'ok') {
        throw new Error('Java 无法连接 Python 服务')
      }
      setPythonStatus('ok')
      setDetails('Java、Python 与服务器 MySQL 均运行正常，服务间通信已打通。')
    } catch (error) {
      if (!javaHealthy) {
        setJavaStatus('error')
      }
      if (!databaseHealthy) {
        setDatabaseStatus('error')
      }
      setPythonStatus('error')
      setDetails(error instanceof Error ? error.message : '服务检查失败')
    }
  }

  async function loadShopifyConnection() {
    try {
      const response = await fetch('/api/shopify/connection')
      if (!response.ok) {
        throw new Error('暂时无法读取 Shopify 连接状态')
      }
      const payload = (await response.json()) as ShopifyConnectionPayload
      setShopifyConnection(payload)
      setShopifyMessage(shopifyStatusMessage(payload))
    } catch (error) {
      setShopifyMessage(error instanceof Error ? error.message : '读取 Shopify 状态失败')
    }
  }

  async function connectShopify() {
    setShopifyLoading(true)
    setShopifyMessage('正在向 Shopify 申请短期令牌并验证店铺……')
    try {
      const response = await fetch('/api/shopify/connection', { method: 'POST' })
      const payload = (await response.json()) as ShopifyConnectionPayload & ApiErrorPayload
      if (!response.ok) {
        throw new Error(payload.message ?? 'Shopify 连接验证失败')
      }
      setShopifyConnection(payload)
      setShopifyMessage(`已连接 ${payload.store?.shopName ?? 'Shopify 店铺'}，店铺信息已写入数据库。`)
    } catch (error) {
      setShopifyMessage(error instanceof Error ? error.message : 'Shopify 连接验证失败')
    } finally {
      setShopifyLoading(false)
    }
  }

  useEffect(() => {
    void checkServices()
    void loadShopifyConnection()
  }, [])

  return (
    <main className="page-shell">
      <section className="hero">
        <p className="eyebrow">ECOMMERCE AGENT · MVP</p>
        <h1>电商自动化运营系统</h1>
        <p className="subtitle">
          面向 Shopify 商家的商品导入、上架与订单同步工作台。
        </p>
      </section>

      <section className="panel shopify-panel" aria-labelledby="shopify-status-title">
        <div className="panel-heading">
          <div>
            <p className="section-kicker">第二周 Shopify 接入</p>
            <h2 id="shopify-status-title">开发店铺连接</h2>
          </div>
          <button
            type="button"
            onClick={connectShopify}
            disabled={shopifyLoading || !shopifyConnection?.configured}
          >
            {shopifyLoading ? '正在验证' : '连接并验证'}
          </button>
        </div>

        <div className="shopify-summary">
          <div>
            <span className="field-label">当前状态</span>
            <strong className={`connection-state connection-state--${shopifyConnection?.state ?? 'loading'}`}>
              {shopifyConnection ? shopifyStateLabel[shopifyConnection.state] : '读取中'}
            </strong>
          </div>
          <div>
            <span className="field-label">开发店铺</span>
            <strong>{shopifyConnection?.store?.shopName ?? '尚未读取'}</strong>
            <small>{shopifyConnection?.configuredShopDomain ?? '需要在本地 .env 中配置'}</small>
          </div>
          <div>
            <span className="field-label">币种 / 主域名</span>
            <strong>{shopifyConnection?.store?.currencyCode ?? '—'}</strong>
            <small>{shopifyConnection?.store?.primaryDomain ?? '验证成功后显示'}</small>
          </div>
        </div>

        <p className="result-message" role="status">{shopifyMessage}</p>
        <p className="security-note">
          页面只显示店铺元数据；Client Secret 与短期访问令牌不会返回前端，也不会写入数据库。
        </p>
      </section>

      <section className="panel" aria-labelledby="system-status-title">
        <div className="panel-heading">
          <div>
            <p className="section-kicker">第一周基础设施</p>
            <h2 id="system-status-title">系统服务状态</h2>
          </div>
          <button type="button" onClick={checkServices} disabled={javaStatus === 'loading'}>
            检查服务
          </button>
        </div>

        <div className="status-grid">
          <StatusCard title="Java 业务后端" port="8085" state={javaStatus} />
          <StatusCard title="Python 能力服务" port="8005" state={pythonStatus} />
          <StatusCard
            title="MySQL 数据库"
            port="3307"
            state={databaseStatus}
            note="服务器 · SSH 隧道"
          />
        </div>

        <p className="result-message" role="status">{details}</p>
      </section>

      <section className="roadmap" aria-labelledby="mvp-title">
        <p className="section-kicker">MVP 主线</p>
        <h2 id="mvp-title">从选品到订单同步</h2>
        <ol>
          <li>读取商品来源</li>
          <li>编辑并创建 Shopify 草稿</li>
          <li>确认发布到在线商店</li>
          <li>买家测试下单与支付</li>
          <li>Webhook 同步订单状态</li>
        </ol>
      </section>
    </main>
  )
}

const shopifyStateLabel: Record<ShopifyConnectionState, string> = {
  NOT_CONFIGURED: '等待配置',
  DISCONNECTED: '等待验证',
  CONNECTED: '已连接',
}

function shopifyStatusMessage(connection: ShopifyConnectionPayload) {
  if (connection.state === 'CONNECTED') {
    return `已连接 ${connection.store?.shopName ?? 'Shopify 店铺'}，最近验证时间：${formatDate(connection.store?.lastVerifiedAt)}`
  }
  if (connection.state === 'DISCONNECTED') {
    return '应用配置已读取。点击“连接并验证”执行真实 Shopify GraphQL 查询。'
  }
  return '尚未启用 Shopify。请先填写本地 .env，并在 Dev Dashboard 安装应用。'
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '未知'
}

function StatusCard({
  title,
  port,
  state,
  note,
}: {
  title: string
  port: string
  state: CheckState
  note?: string
}) {
  return (
    <article className="status-card">
      <div className={`status-dot status-dot--${state}`} aria-hidden="true" />
      <div>
        <h3>{title}</h3>
        <p>{note ?? `localhost:${port}`}</p>
      </div>
      <span className={`status-pill status-pill--${state}`}>{statusLabel[state]}</span>
    </article>
  )
}

export default App
