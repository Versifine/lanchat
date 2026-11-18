import { forceLogout } from './http'

/** 指数退避重连间隔（毫秒）：1s / 2s / 4s / 8s / 16s / 30s 封顶 */
export const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000]
/** 心跳间隔：10s（docs/api.md：客户端每 10s ping，服务端 35s 无消息判离线） */
export const PING_INTERVAL = 10000

/**
 * WebSocket 地址：ws(s)://<当前 host>/ws/chat?token=<JWT>
 * 开发态由 Vite 代理 /ws 到 :8080，生产态前端由后端同端口托管，因此都用 location.host。
 */
export function buildWebSocketUrl(token) {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/chat?token=${encodeURIComponent(token || '')}`
}

/**
 * 聊天 WebSocket 封装。
 *
 * handlers 支持的键（与 docs/api.md 服务端消息 type 一一对应，按 type 分发）：
 *   message / userList / file / system / pong / error  → (payload) => void
 * 生命周期回调：
 *   onOpen({ reconnected })          握手成功
 *   onStatus(state, info)            状态变化：idle|connecting|connected|reconnecting|closed|auth-failed
 *   onAuthFailed()                   token 失效（关闭码 4401）
 */
export class ChatSocket {
  constructor(handlers = {}) {
    this.handlers = handlers
    this.token = ''
    this.socket = null
    this.pingTimer = null
    this.reconnectTimer = null
    this.reconnectAttempt = 0
    this.manualClosed = false
    this.state = 'idle'
  }

  get isConnected() {
    return Boolean(this.socket) && this.socket.readyState === WebSocket.OPEN
  }

  /** 建立连接（token 通过查询参数完成握手） */
  connect(token) {
    if (token) this.token = token
    if (!this.token) return
    this.manualClosed = false
    this._clearReconnectTimer()
    if (this.socket) {
      this._detach(this.socket)
      try {
        this.socket.close(1000, 'reconnect')
      } catch {
        /* ignore */
      }
      this.socket = null
    }
    this._open()
  }

  /** 发送 JSON 文本帧；未连接返回 false（由调用方提示，避免消息静默丢失） */
  send(payload) {
    if (!this.isConnected) return false
    try {
      this.socket.send(JSON.stringify(payload))
      return true
    } catch {
      return false
    }
  }

  /** 发送聊天消息：to 为 null 表示群聊，否则私聊 */
  sendChat(to, content) {
    return this.send({
      type: 'chat',
      to: to == null ? null : Number(to),
      content: String(content ?? '')
    })
  }

  /** 主动关闭：停止重连与心跳 */
  close() {
    this.manualClosed = true
    this._clearReconnectTimer()
    this._stopPing()
    const socket = this.socket
    this.socket = null
    if (socket) {
      this._detach(socket)
      try {
        socket.close(1000, 'client close')
      } catch {
        /* ignore */
      }
    }
    this._setState('closed', { code: 1000 })
  }

  _open() {
    if (this.manualClosed || !this.token) return
    if (this.reconnectAttempt === 0) this._setState('connecting', {})

    let socket
    try {
      socket = new WebSocket(buildWebSocketUrl(this.token))
    } catch {
      this._scheduleReconnect()
      return
    }
    this.socket = socket

    socket.onopen = () => {
      const reconnected = this.reconnectAttempt > 0
      this.reconnectAttempt = 0
      this._startPing()
      this._setState('connected', {})
      this.handlers.onOpen?.({ reconnected })
    }

    socket.onmessage = (event) => this._dispatch(event.data)

    // 错误后浏览器一定会触发 onclose，重连统一在 onclose 里处理，避免重复调度
    socket.onerror = () => {}

    socket.onclose = (event) => {
      this._stopPing()
      if (this.socket === socket) this.socket = null
      if (this.manualClosed) {
        this._setState('closed', { code: event.code })
        return
      }
      if (event.code === 4401) {
        // docs/api.md：token 无效/过期，回登录页
        this._setState('auth-failed', { code: event.code })
        this.handlers.onAuthFailed?.()
        forceLogout('登录已过期，请重新登录')
        return
      }
      this._scheduleReconnect(event.code)
    }
  }

  _scheduleReconnect(code) {
    if (this.manualClosed || !this.token) return
    this._clearReconnectTimer()
    const index = Math.min(this.reconnectAttempt, RECONNECT_DELAYS.length - 1)
    const delay = RECONNECT_DELAYS[index]
    this.reconnectAttempt += 1
    this._setState('reconnecting', { attempt: this.reconnectAttempt, delay, code: code || 0 })
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this._open()
    }, delay)
  }

  _startPing() {
    this._stopPing()
    this.pingTimer = window.setInterval(() => {
      this.send({ type: 'ping' })
    }, PING_INTERVAL)
  }

  _stopPing() {
    if (this.pingTimer) {
      window.clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  _clearReconnectTimer() {
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  _detach(socket) {
    socket.onopen = null
    socket.onmessage = null
    socket.onerror = null
    socket.onclose = null
  }

  _dispatch(raw) {
    let payload
    try {
      payload = JSON.parse(raw)
    } catch {
      return
    }
    if (!payload || typeof payload.type !== 'string') return
    const handler = this.handlers[payload.type]
    if (typeof handler === 'function') {
      try {
        handler(payload)
      } catch (error) {
        console.error('[ws] 处理消息失败', error)
      }
    }
  }

  _setState(state, info) {
    this.state = state
    this.handlers.onStatus?.(state, info || {})
  }
}

export default ChatSocket
