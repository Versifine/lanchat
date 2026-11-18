import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一会话存储键（REST 拦截器 / WS 封装 / 路由守卫共用，避免各处硬编码）。
 * 字段与 docs/api.md 契约无关，仅本前端内部使用。
 */
export const TOKEN_KEY = 'lanchat_token'
export const USER_KEY = 'lanchat_user'
export const FLASH_KEY = 'lanchat_flash'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function saveSession(token, user) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 强制跳回登录页时的提示语：暂存到 sessionStorage，由登录页读取展示 */
export function flash(message) {
  if (message) sessionStorage.setItem(FLASH_KEY, message)
}

export function takeFlash() {
  const message = sessionStorage.getItem(FLASH_KEY)
  if (message) sessionStorage.removeItem(FLASH_KEY)
  return message || ''
}

/**
 * token 失效（REST 401 / WS 4401）时清缓存并回到登录页。
 * 采用整页刷新，确保 Pinia、WebSocket、心跳定时器等状态全部重建，避免残留连接。
 */
export function forceLogout(message = '登录已过期，请重新登录') {
  clearSession()
  flash(message)
  const hash = String(window.location.hash || '')
  if (!hash.startsWith('#/login')) {
    window.location.hash = '#/login'
    window.location.reload()
  }
}

/** 全局 axios 实例：baseURL 为空，开发态走 Vite 代理，生产态由后端同端口托管 */
const http = axios.create({
  baseURL: '',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

const AUTH_ENDPOINT_PATTERN = /\/api\/auth\/(login|register)(\?.*)?$/

function isAuthEndpoint(url = '') {
  return AUTH_ENDPOINT_PATTERN.test(url)
}

function fallbackMessage(status) {
  switch (status) {
    case 400:
      return '请求参数有误'
    case 401:
      return '登录状态已失效，请重新登录'
    case 403:
      return '没有权限执行该操作'
    case 404:
      return '请求的资源不存在'
    case 409:
      return '数据冲突，请刷新后重试'
    case 413:
      return '文件过大'
    case 429:
      return '请求过于频繁，请稍后再试'
    default:
      return status >= 500 ? '服务器内部错误，请稍后重试' : '请求失败，请稍后重试'
  }
}

/** 错误响应可能是 JSON，也可能是 Blob（下载接口），统一还原成 {code,message} */
async function readErrorBody(data) {
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      return text ? JSON.parse(text) : null
    } catch {
      return null
    }
  }
  return data && typeof data === 'object' ? data : null
}

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const response = error.response
    let code
    let message = ''

    if (response) {
      const body = await readErrorBody(response.data)
      if (body) {
        code = body.code
        if (typeof body.message === 'string' && body.message) message = body.message
      }
      if (!message) {
        message = error.code === 'ECONNABORTED' ? '请求超时，请检查网络或后端服务' : fallbackMessage(response.status)
      }
    } else {
      message = '网络异常，请确认后端服务已启动'
    }

    const silent = Boolean(error.config?.silent)
    const authExpired = response && response.status === 401 && !isAuthEndpoint(error.config?.url)
    if (authExpired) {
      // 未认证（1001）：清 token 回登录页；登录接口自身的 401（1003）只提示不跳转
      forceLogout(message || '登录已过期，请重新登录')
    } else if (!silent) {
      ElMessage.error(message)
    }

    const normalized = new Error(message)
    normalized.code = code
    normalized.status = response ? response.status : 0
    normalized.data = response ? response.data : null
    return Promise.reject(normalized)
  }
)

export default http
