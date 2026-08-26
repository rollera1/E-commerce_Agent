import { useState } from 'react'

type CheckState = 'idle' | 'loading' | 'ok' | 'error'

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

const statusLabel: Record<CheckState, string> = {
  idle: '等待检查',
  loading: '检查中',
  ok: '运行正常',
  error: '连接失败',
}

function App() {
  const [javaStatus, setJavaStatus] = useState<CheckState>('idle')
  const [pythonStatus, setPythonStatus] = useState<CheckState>('idle')
  const [details, setDetails] = useState('点击按钮检查 Java 与 Python 服务。')

  async function checkServices() {
    let javaHealthy = false
    setJavaStatus('loading')
    setPythonStatus('loading')
    setDetails('正在请求 Java 健康检查……')

    try {
      const javaResponse = await fetch('/api/health')
      const javaData = (await javaResponse.json()) as HealthPayload
      if (!javaResponse.ok || javaData.status !== 'ok') {
        throw new Error('Java 服务返回异常状态')
      }
      setJavaStatus('ok')
      javaHealthy = true

      const pythonResponse = await fetch('/api/health/python')
      const pythonData = (await pythonResponse.json()) as HealthPayload
      if (!pythonResponse.ok || pythonData.downstream?.status !== 'ok') {
        throw new Error('Java 无法连接 Python 服务')
      }
      setPythonStatus('ok')
      setDetails('Java 服务正常，并已通过 REST 成功访问 Python 服务。')
    } catch (error) {
      if (!javaHealthy) {
        setJavaStatus('error')
      }
      setPythonStatus('error')
      setDetails(error instanceof Error ? error.message : '服务检查失败')
    }
  }

  return (
    <main className="page-shell">
      <section className="hero">
        <p className="eyebrow">ECOMMERCE AGENT · MVP</p>
        <h1>电商自动化运营系统</h1>
        <p className="subtitle">
          面向 Shopify 商家的商品导入、上架与订单同步工作台。
        </p>
      </section>

      <section className="panel" aria-labelledby="system-status-title">
        <div className="panel-heading">
          <div>
            <p className="section-kicker">第一周工程验证</p>
            <h2 id="system-status-title">系统服务状态</h2>
          </div>
          <button type="button" onClick={checkServices} disabled={javaStatus === 'loading'}>
            检查服务
          </button>
        </div>

        <div className="status-grid">
          <StatusCard title="Java 业务后端" port="8085" state={javaStatus} />
          <StatusCard title="Python 能力服务" port="8005" state={pythonStatus} />
          <StatusCard title="MySQL 数据库" port="3307" state="idle" note="由 Docker 管理" />
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
