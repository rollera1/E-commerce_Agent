import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { transformWithOxc } from 'vite'
import { requestJson } from '../src/api.ts'

// 使用现有 Vite 转译组件；仅替代 Hook 和 DOM，不连接真实店铺或模型。
const source = readFileSync(process.env.PRODUCT_WORKSPACE_SOURCE ?? new URL('../src/ProductWorkspace.tsx', import.meta.url), 'utf8')
const { code } = await transformWithOxc(source.replace(/^import .*$/gm, ''), 'ProductWorkspace.tsx', {
  jsx: { runtime: 'classic', pragma: 'jsx', pragmaFrag: 'Fragment' },
})
const tick = () => new Promise((resolve) => setImmediate(resolve))

function workspace(t) {
  const state = []
  let cursor = 0
  const useState = (initial) => {
    const index = cursor++
    if (!(index in state)) state[index] = initial
    return [state[index], (value) => { state[index] = typeof value === 'function' ? value(state[index]) : value }]
  }
  const useRef = (value) => useState({ current: value })[0]
  const jsx = (type, props, ...children) => ({ type, props: { ...props, children } })
  const document = { createElement: () => ({
    set innerHTML(value) { this.content.textContent = value },
    content: { textContent: '', querySelectorAll: () => [] },
  }) }
  const calls = []
  let respond = () => { throw new Error('未设置响应') }
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    calls.push({ url, init })
    return new Response(JSON.stringify(await respond(url, init)))
  })
  const Component = new Function('useState', 'useRef', 'requestJson', 'Icon', 'jsx', 'Fragment', 'document', 'window',
    code.replace('export default function', 'function') + '; return ProductWorkspace')(
      useState, useRef, requestJson, () => null, jsx, 'fragment', document, { confirm: () => false },
    )
  function nodes() {
    cursor = 0
    const result = []
    function walk(node) {
      if (Array.isArray(node)) return node.forEach(walk)
      if (!node || typeof node !== 'object') return
      result.push(node)
      walk(node.props?.children)
    }
    walk(Component())
    return result
  }
  const text = (node) => Array.isArray(node) ? node.map(text).join('') : typeof node === 'object' && node ? text(node.props?.children) : node ?? ''
  const find = (predicate) => {
    const node = nodes().find(predicate)
    assert.ok(node, '目标控件必须存在')
    return node
  }
  return {
    calls,
    respond: (fn) => { respond = fn },
    message: () => text(find((n) => n.props?.className === 'workspace-message')),
    busy: () => nodes().some((n) => n.props?.className === 'message-pulse'),
    change: (id, value) => find((n) => id ? n.props?.id === id : n.type === 'input' && n.props?.type === 'number' && n.props?.max === '100').props.onChange({ target: { value } }),
    choose: (label, value) => find((n) => n.type === 'select' && text(n).includes(label)).props.onChange({ target: { value } }),
    price: (value) => find((n) => n.props?.['aria-label'] === 'Black Shopify 售价').props.onChange({ target: { value } }),
    async search(query = 'lamp') {
      this.change('product-keyword', query)
      find((n) => n.type === 'form').props.onSubmit({ preventDefault() {} })
      await tick()
    },
    async click(label) {
      const button = find((n) => n.type === 'button' && text(n).includes(label))
      assert.equal(Boolean(button.props.disabled), false, '操作前按钮应可用')
      await button.props.onClick()
      await tick()
    },
  }
}

const product = {
  source: 'FAKE', sourceProductId: 'source-1', title: 'Lamp', description: 'Desk lamp',
  images: ['https://example.invalid/lamp.png'], price: { amount: '10', currency: 'USD' },
  variants: [{ sourceVariantId: 'v1', sku: 'lamp-1', title: 'Black', price: { amount: '10', currency: 'USD' }, inventoryAvailable: 3 }],
  sourceUrl: '', fetchedAt: '2026-10-06T00:00:00Z', inventoryTotal: 3,
}
const searchResult = { selectionId: 'search-1', matchedQuery: 'lamp', candidates: [product] }

async function confirmedWorkspace(t) {
  const ui = workspace(t)
  ui.respond((url) => url.endsWith('/confirm') ? { selectionId: 'task-1' }
    : url.includes('/source-products/') ? product : searchResult)
  await ui.search()
  await ui.click('Lamp')
  await ui.click('确认选择此商品')
  assert.equal(ui.calls.length, 3)
  return ui
}

test('空查询和越界数量不会调用后端', async (t) => {
  const ui = workspace(t)
  await ui.search('  ')
  assert.match(ui.message(), /请输入商品关键词/)
  ui.change('product-limit', 'custom')
  for (const limit of ['0', '101', '1.5']) {
    ui.change(undefined, limit)
    await ui.search()
    assert.match(ui.message(), /1–100 的整数/)
  }
  assert.equal(ui.calls.length, 0)
  assert.equal(ui.busy(), false)
})

test('成功搜索保留请求字段、提示并恢复加载状态', async (t) => {
  const ui = workspace(t)
  let release
  ui.respond(() => new Promise((resolve) => { release = resolve }))
  const pending = ui.search(' lamp ')
  assert.equal(ui.busy(), true)
  assert.match(ui.message(), /正在检索/)
  release(searchResult)
  await pending
  assert.deepEqual(JSON.parse(ui.calls[0].init.body), { query: 'lamp', limit: 10 })
  assert.match(ui.message(), /找到 1 个候选商品/)
  assert.equal(ui.busy(), false)
})

for (const [failure, expected] of [[new Error('网络中断'), /网络中断/], ['unknown', /读取来源商品失败/]]) {
  test(`搜索失败恢复加载状态：${String(failure)}`, async (t) => {
    const ui = workspace(t)
    ui.respond(() => { throw failure })
    await ui.search()
    assert.match(ui.message(), expected)
    assert.equal(ui.busy(), false)
    ui.respond(() => ({ ...searchResult, candidates: [] }))
    await ui.search()
    assert.match(ui.message(), /暂无该商品/)
  })
}

test('确认后生成只调用文案接口，失败时恢复按钮', async (t) => {
  const ui = await confirmedWorkspace(t)
  ui.respond(() => { throw new Error('模型超时') })
  await ui.click('生成文案与 SEO')
  assert.match(ui.message(), /模型超时/)
  assert.equal(ui.busy(), false)
  assert.equal(ui.calls[3].url, '/api/product-selections/task-1/copy-seo-generations')
  assert.deepEqual(JSON.parse(ui.calls[3].init.body), { targetMarket: 'US', language: 'en' })
  assert.ok(ui.calls.every((call) => !call.url.includes('shopify-listing')))
})

test('成功文案等待人工审核，接受内容不会创建或发布商品', async (t) => {
  const ui = await confirmedWorkspace(t)
  const generation = { generationId: 'g1', language: 'en', status: 'SUCCEEDED', reviewStatus: 'PENDING_REVIEW',
    content: { title: 'Lamp', bulletPoints: ['Light'], description: 'Desk lamp', seoTitle: 'Lamp', metaDescription: 'Desk lamp', tags: ['lamp'] },
    execution: { agent: 'COPY_SEO', model: 'fake', promptVersion: 'v1' } }
  ui.respond((url) => url.endsWith('/accept') ? { ...generation, reviewStatus: 'ACCEPTED' } : generation)
  await ui.click('生成文案与 SEO')
  assert.match(ui.message(), /请审核内容/)
  await ui.click('接受内容')
  assert.equal(ui.calls.at(-1).url, '/api/copy-seo-generations/g1/accept')
  assert.match(ui.message(), /已接受生成内容/)
  assert.equal(ui.busy(), false)
  assert.ok(ui.calls.every((call) => !call.url.includes('shopify-listing')))
})

test('异步文案任务轮询完成后进入人工审核', async (t) => {
  const ui = await confirmedWorkspace(t)
  const submitted = { generationId: 'g-async', language: 'en', status: 'SUBMITTED', reviewStatus: 'NOT_APPLICABLE',
    content: null, execution: { agent: 'COPY_SEO', model: 'pending', promptVersion: 'pending' } }
  const completed = { ...submitted, status: 'SUCCEEDED', reviewStatus: 'PENDING_REVIEW',
    content: { title: 'Lamp', bulletPoints: ['Light'], description: 'Desk lamp', seoTitle: 'Lamp', metaDescription: 'Desk lamp', tags: ['lamp'] },
    execution: { agent: 'COPY_SEO', model: 'fake', promptVersion: 'v1' } }
  ui.respond((url) => url.includes('/api/copy-seo-generations/g-async') ? completed : submitted)

  await ui.click('生成文案与 SEO')

  assert.equal(ui.calls[3].url, '/api/product-selections/task-1/copy-seo-generations')
  assert.equal(ui.calls[4].url, '/api/copy-seo-generations/g-async')
  assert.match(ui.message(), /请审核内容/)
  assert.equal(ui.busy(), false)
})

test('异步查询中断后继续查询，不重复创建任务', async (t) => {
  const ui = await confirmedWorkspace(t)
  const submitted = { generationId: 'g-async', language: 'en', status: 'SUBMITTED', reviewStatus: 'NOT_APPLICABLE',
    content: null, execution: { agent: 'COPY_SEO', model: 'pending', promptVersion: 'pending' } }
  let queryFails = true
  ui.respond((url) => {
    if (!url.includes('/api/copy-seo-generations/g-async')) return submitted
    if (queryFails) throw new Error('查询暂时中断')
    return { ...submitted, status: 'FAILED', failure: { errorType: 'TIMEOUT', retryable: false } }
  })

  await ui.click('生成文案与 SEO')
  assert.match(ui.message(), /查询暂时中断/)
  queryFails = false
  await ui.click('继续查询生成状态')

  assert.equal(ui.calls.filter((call) => call.url.includes('/product-selections/task-1/copy-seo-generations')).length, 1)
  assert.match(ui.message(), /TIMEOUT/)
})


async function acceptedWorkspace(t) {
  const ui = await confirmedWorkspace(t)
  ui.respond(() => ({ generationId: 'g1', language: 'en', status: 'SUCCEEDED', reviewStatus: 'ACCEPTED',
    content: { title: 'Lamp', bulletPoints: ['Light'], description: 'Desk lamp', seoTitle: 'Lamp', metaDescription: 'Desk lamp', tags: ['lamp'] },
    execution: { agent: 'COPY_SEO', model: 'fake', promptVersion: 'v1' } }))
  await ui.click('生成文案与 SEO')
  return ui
}

test('读取 Shopify 配置失败恢复按钮，保留原错误提示', async (t) => {
  const ui = await acceptedWorkspace(t)
  ui.respond(() => { throw new Error('Shopify 权限不足') })
  await ui.click('读取 / 刷新 Shopify 配置')
  assert.match(ui.message(), /Shopify 权限不足/)
  assert.equal(ui.busy(), false)
  assert.deepEqual(ui.calls.slice(-2).map((call) => call.url), ['/api/shopify/locations', '/api/shopify/publications'])
})

test('配置不完整不提交，创建草稿和人工发布保留独立请求', async (t) => {
  const ui = await acceptedWorkspace(t)
  const count = ui.calls.length
  await ui.click('创建 / 更新 Shopify 草稿')
  assert.match(ui.message(), /请选择 Shopify 库存地点和销售渠道/)
  assert.equal(ui.calls.length, count)
  ui.respond((url) => url.endsWith('/locations') ? [{ id: 'loc-1', name: 'Main', active: true }]
    : [{ id: 'pub-1', title: 'Store', autoPublish: false }])
  await ui.click('读取 / 刷新 Shopify 配置')
  ui.choose('请选择 Location', 'loc-1')
  ui.choose('请选择 Publication', 'pub-1')
  ui.price('20')
  ui.respond(() => ({ generationId: 'g1', status: 'READY_TO_PUBLISH', currentStep: 'ACTIVATE',
    reviewMode: 'MANUAL', publishMode: 'MANUAL', imageUrls: product.images, variants: [] }))
  await ui.click('创建 / 更新 Shopify 草稿')
  assert.match(ui.message(), /等待人工确认发布/)
  assert.equal(ui.calls.at(-1).url, '/api/copy-seo-generations/g1/shopify-listing/draft')
  assert.deepEqual(JSON.parse(ui.calls.at(-1).init.body), {
    reviewMode: 'MANUAL', publishMode: 'MANUAL', locationId: 'loc-1', publicationId: 'pub-1',
    autoPublishAuthorized: false, imageUrls: product.images, variantPrices: [{ sourceVariantId: 'v1', price: 20 }],
  })
  assert.ok(ui.calls.every((call) => !call.url.endsWith('/publish')))
  ui.respond(() => { throw 'unknown' })
  await ui.click('确认正式发布')
  assert.equal(ui.calls.at(-1).url, '/api/copy-seo-generations/g1/shopify-listing/publish')
  assert.match(ui.message(), /Shopify 上架操作失败/)
  assert.equal(ui.busy(), false)
})
