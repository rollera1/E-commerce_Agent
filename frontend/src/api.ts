type ErrorPayload = { message?: string }

export async function readJson<T>(response: Response): Promise<T> {
  const text = await response.text()
  let payload: (T & ErrorPayload) | null = null

  if (text.trim()) {
    try {
      payload = JSON.parse(text) as T & ErrorPayload
    } catch {
      throw new Error(response.ok ? '服务返回了无法识别的数据' : `请求失败（${response.status}）`)
    }
  }

  if (!response.ok) throw new Error(payload?.message ?? `请求失败（${response.status}）`)
  if (payload === null) throw new Error('服务暂未返回数据，请确认后端服务已启动')
  return payload
}

export async function requestJson<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  return readJson<T>(await fetch(input, init))
}
