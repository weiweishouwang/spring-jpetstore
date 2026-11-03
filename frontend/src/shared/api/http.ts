import { csrfHeaders } from './csrf'

/**
 * T318: 统一同源 HTTP 客户端。
 *
 * <p>浏览器与后端同源：Cookie 随请求发送（`credentials: 'same-origin'`），不引入跨域凭据
 * 模式，也不开放宽泛 CORS（plan §3.1、§5.2）。请求一律带 API-Version，写请求带内存中的
 * CSRF token（plan §6.1）。</p>
 *
 * <p>副作用请求绝不自动重试：写操作由调用方决定是否重试，且重试必须复用同一个幂等键
 * （plan §5.7、AT-R02）。失败一律抛出 {@link HttpError}，携带状态、错误码与 requestId；
 * 401 与 409 不被吞掉，也不静默重放。</p>
 */

/** 当前 API 版本；只支持已冻结的 1.0（plan §6.1）。 */
export const API_VERSION = '1.0'

/** 字段级校验信息；不含被拒绝的原值（NFR-04）。 */
export interface Violation {
  readonly field: string
  readonly message: string
}

/** 服务端返回的问题详情。 */
export interface HttpProblem {
  readonly status: number
  readonly code: string
  readonly title: string
  readonly detail?: string
  readonly requestId?: string
  readonly violations: readonly Violation[]
}

/** 请求失败：状态、错误码与 requestId 都来自服务端，不自行编造。 */
export class HttpError extends Error {
  readonly status: number

  readonly code: string

  readonly requestId?: string

  readonly violations: readonly Violation[]

  constructor(problem: HttpProblem) {
    super(problem.title || '请求失败')
    this.name = 'HttpError'
    this.status = problem.status
    this.code = problem.code
    this.requestId = problem.requestId
    this.violations = problem.violations
  }
}

export interface RequestOptions {
  /** 请求方法；默认 GET。 */
  readonly method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  /** 请求体；对象会被序列化为 JSON，字符串按原样发送（例如表单编码）。 */
  readonly body?: unknown
  /** 额外请求头。 */
  readonly headers?: Record<string, string>
  /** 是否附带 CSRF token；写请求默认附带。 */
  readonly csrf?: boolean
}

export interface HttpResponse<T> {
  readonly status: number
  readonly headers: Headers
  readonly body: T
}

export interface HttpClient {
  /** 发送请求并返回响应体。 */
  request<T>(path: string, options?: RequestOptions): Promise<T>
  /** 发送请求并返回状态、响应头与响应体。 */
  requestWithResponse<T>(path: string, options?: RequestOptions): Promise<HttpResponse<T>>
}

/**
 * 传输接口：客户端总是以站内路径与初始化参数调用它。
 *
 * <p>比 `typeof fetch` 更窄，便于测试注入，也保证 URL 一定是站内路径字符串。</p>
 */
export type FetchLike = (url: string, init: RequestInit) => Promise<Response>

export interface HttpClientOptions {
  /** 后端基路径；开发时由 Vite 代理保持同源。 */
  readonly baseUrl?: string
  /** 传输实现；测试注入，生产用全局 fetch。 */
  readonly fetchImpl?: FetchLike
}

/** 创建同源 HTTP 客户端。 */
export function createHttpClient(options: HttpClientOptions = {}): HttpClient {
  const baseUrl = options.baseUrl ?? ''
  const transport: FetchLike = options.fetchImpl ?? ((url, init) => fetch(url, init))

  async function send<T>(path: string, requestOptions: RequestOptions = {}): Promise<HttpResponse<T>> {
    const method = requestOptions.method ?? 'GET'
    const headers: Record<string, string> = {
      'API-Version': API_VERSION,
      Accept: 'application/json',
      ...requestOptions.headers,
    }
    if (requestOptions.body !== undefined && headers['Content-Type'] === undefined) {
      headers['Content-Type'] = 'application/json'
    }
    if (method !== 'GET' && requestOptions.csrf !== false) {
      Object.assign(headers, csrfHeaders())
    }
    const init: RequestInit = { method, headers, credentials: 'same-origin' }
    if (requestOptions.body !== undefined) {
      init.body = typeof requestOptions.body === 'string' ? requestOptions.body : JSON.stringify(requestOptions.body)
    }
    const response = await transport(`${baseUrl}${path}`, init)
    const text = await response.text()
    const payload: unknown = text.length > 0 ? parseJson(text) : undefined
    if (!response.ok) {
      throw new HttpError(toProblem(response.status, payload))
    }
    return { status: response.status, headers: response.headers, body: payload as T }
  }

  return {
    request: async <T>(path: string, requestOptions?: RequestOptions): Promise<T> =>
      (await send<T>(path, requestOptions)).body,
    requestWithResponse: send,
  }
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

/** 服务端错误响应 → 问题详情；缺字段时给出安全默认值，不透传原始内容。 */
function toProblem(status: number, payload: unknown): HttpProblem {
  const body = (payload ?? {}) as Record<string, unknown>
  const violations = Array.isArray(body.violations)
    ? body.violations
        .filter((entry): entry is Record<string, unknown> => typeof entry === 'object' && entry !== null)
        .map((entry) => ({
          field: typeof entry.field === 'string' ? entry.field : '',
          message: typeof entry.message === 'string' ? entry.message : '',
        }))
    : []
  return {
    status,
    code: typeof body.code === 'string' ? body.code : 'UNKNOWN',
    title: typeof body.title === 'string' ? body.title : '请求失败',
    detail: typeof body.detail === 'string' ? body.detail : undefined,
    requestId: typeof body.requestId === 'string' ? body.requestId : undefined,
    violations,
  }
}

/** 默认客户端：同源、无凭据参数以外的特殊设置；页面直接使用它。 */
export const httpClient: HttpClient = createHttpClient()
