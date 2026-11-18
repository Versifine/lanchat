import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { ChatSocket } from '../api/ws'
import { useAuthStore } from './auth'

/** 群聊会话 key；私聊 key 形如 private:<peerId> */
export const GROUP_KEY = 'group'
export function privateKeyOf(peerId) {
  return `private:${peerId}`
}

/** GET /api/messages/* 的 limit：契约默认 50、最大 200 */
export const HISTORY_LIMIT = 50
/** 单条消息最大字符数（docs/SPEC.md SEC-05：消息 ≤2000 字符） */
export const MAX_MESSAGE_LENGTH = 2000
/** 连续消息合并 / 时间分隔阈值 */
const TIME_GAP = 5 * 60 * 1000
/** 单会话本地消息上限，防止超长会话占内存 */
const MAX_LOCAL_MESSAGES = 500

function normalizeUser(raw) {
  if (!raw || raw.id == null) return null
  return {
    id: raw.id,
    username: raw.username || '',
    nickname: raw.nickname || raw.username || '',
    online: Boolean(raw.online)
  }
}

/** 把服务端 message 推送 / 历史记录统一成前端渲染模型 */
function normalizeChatMessage(raw, selfId) {
  const sender = normalizeUser(raw?.sender)
  return {
    kind: 'chat',
    uid: `chat-${raw?.id}`,
    id: raw?.id,
    sender,
    receiverId: raw?.receiverId == null ? null : raw.receiverId,
    content: typeof raw?.content === 'string' ? raw.content : '',
    createdAt: Number(raw?.createdAt) || Date.now(),
    offline: Boolean(raw?.offline)
  }
}

function normalizeFileRecord(raw) {
  if (!raw || raw.id == null) return null
  return {
    id: raw.id,
    originalName: raw.originalName || '未命名文件',
    size: Number(raw.size) || 0,
    sha256: raw.sha256 || '',
    downloadUrl: raw.downloadUrl || `/api/files/${raw.id}/download`
  }
}

/**
 * 会话归属 key：
 * - receiverId 为 null → 群聊
 * - 私聊：发送者是自己 → 对端为 receiverId；否则对端为 sender.id
 */
function conversationKeyOf(record, selfId) {
  if (record?.receiverId == null) return GROUP_KEY
  const senderId = record?.sender?.id
  const peerId = senderId === selfId ? record.receiverId : senderId
  return privateKeyOf(peerId)
}

export const useChatStore = defineStore('chat', () => {
  const auth = useAuthStore()

  /** GET /api/users + WS userList 推送：全部用户及在线状态 */
  const users = ref([])

  /** 会话类型：group | private */
  const activeType = ref('group')
  /** 私聊对端用户对象（null 表示群聊） */
  const activePeer = ref(null)

  /** key -> 消息数组（chat/file/system 三种 kind） */
  const conversations = ref({})
  /** key -> 未读数 */
  const unread = ref({})

  /** WS 状态：idle|connecting|connected|reconnecting|closed|auth-failed */
  const connectionState = ref('idle')
  const connectionAttempt = ref(0)
  const historyLoading = ref(false)

  /** 非响应式内部状态 */
  let socket = null
  let probingToken = false

  const selfId = computed(() => auth.user?.id ?? null)
  const activeKey = computed(() =>
    activeType.value === 'group' ? GROUP_KEY : privateKeyOf(activePeer.value?.id)
  )
  const activeMessages = computed(() => conversations.value[activeKey.value] || [])
  const onlineCount = computed(() => users.value.filter((user) => user.online).length)
  const totalUnread = computed(() =>
    Object.values(unread.value).reduce((sum, count) => sum + (Number(count) || 0), 0)
  )
  const connected = computed(() => connectionState.value === 'connected')
  const reconnecting = computed(
    () => connectionState.value === 'reconnecting' || connectionState.value === 'connecting'
  )

  /** 侧栏用户列表：排除自己；在线优先、昵称排序（离线置灰仍可私聊） */
  const otherUsers = computed(() => {
    const self = selfId.value
    return users.value
      .filter((user) => user.id !== self)
      .slice()
      .sort((a, b) => {
        if (a.online !== b.online) return a.online ? -1 : 1
        return String(a.nickname || a.username).localeCompare(String(b.nickname || b.username), 'zh-Hans-CN')
      })
  })

  const activeTitle = computed(() => {
    if (activeType.value === 'group') return '群聊'
    return activePeer.value ? activePeer.value.nickname || activePeer.value.username : '私聊'
  })

  const activeSubtitle = computed(() => {
    if (activeType.value === 'group') return `${onlineCount.value} 人在线`
    if (!activePeer.value) return ''
    return activePeer.value.online ? '在线' : '离线，消息将在对方上线后送达'
  })

  const activePeerOnline = computed(() => Boolean(activePeer.value?.online))

  function ensureList(key) {
    if (!conversations.value[key]) conversations.value[key] = []
    return conversations.value[key]
  }

  /** 插入消息：chat 按服务端 id 去重，保持时间升序 */
  function insertMessage(key, message) {
    if (!key) return false
    const list = ensureList(key)
    if (message.kind === 'chat' && message.id != null) {
      if (list.some((item) => item.kind === 'chat' && item.id === message.id)) return false
    }
    list.push(message)
    if (list.length > 1 && list[list.length - 2].createdAt > message.createdAt) {
      list.sort((a, b) => a.createdAt - b.createdAt)
    }
    if (list.length > MAX_LOCAL_MESSAGES) list.splice(0, list.length - MAX_LOCAL_MESSAGES)
    return true
  }

  /** 把历史消息合并进本地会话（保留本地文件/系统消息，chat 按 id 去重） */
  function mergeHistory(key, historyMessages) {
    const list = ensureList(key)
    const existingIds = new Set(
      list.filter((item) => item.kind === 'chat' && item.id != null).map((item) => item.id)
    )
    const incoming = historyMessages.filter(
      (item) => item.kind !== 'chat' || item.id == null || !existingIds.has(item.id)
    )
    if (!incoming.length) return
    const merged = list.concat(incoming).sort((a, b) => a.createdAt - b.createdAt)
    conversations.value[key] = merged.slice(-MAX_LOCAL_MESSAGES)
  }

  function bumpUnread(key) {
    unread.value[key] = (Number(unread.value[key]) || 0) + 1
  }

  function clearUnread(key) {
    if (unread.value[key]) unread.value[key] = 0
  }

  function unreadOf(key) {
    return Number(unread.value[key]) || 0
  }

  // ---------------------------------------------------------------------------
  // REST 数据加载
  // ---------------------------------------------------------------------------

  /** GET /api/users：全部用户及在线状态 */
  async function loadUsers() {
    try {
      const list = await http.get('/api/users')
      users.value = Array.isArray(list) ? list.map(normalizeUser).filter(Boolean) : []
      syncActivePeer()
    } catch {
      /* 错误已由拦截器统一提示 */
    }
  }

  /** 用户列表更新后同步当前私聊对端的在线状态 */
  function syncActivePeer() {
    if (activeType.value !== 'private' || !activePeer.value) return
    const latest = users.value.find((user) => user.id === activePeer.value.id)
    if (latest) activePeer.value = { ...activePeer.value, online: latest.online }
  }

  /** 拉取当前会话最近 HISTORY_LIMIT 条历史（群聊或私聊） */
  async function loadActiveHistory() {
    if (activeType.value === 'private' && !activePeer.value) return
    historyLoading.value = true
    try {
      if (activeType.value === 'group') {
        const list = await http.get('/api/messages/group', { params: { limit: HISTORY_LIMIT } })
        const normalized = (Array.isArray(list) ? list : []).map((raw) =>
          normalizeChatMessage(raw, selfId.value)
        )
        mergeHistory(GROUP_KEY, normalized)
      } else {
        const peerId = activePeer.value.id
        const list = await http.get(`/api/messages/private/${peerId}`, {
          params: { limit: HISTORY_LIMIT }
        })
        const normalized = (Array.isArray(list) ? list : []).map((raw) =>
          normalizeChatMessage(raw, selfId.value)
        )
        mergeHistory(privateKeyOf(peerId), normalized)
      }
    } catch {
      /* 错误已由拦截器统一提示 */
    } finally {
      historyLoading.value = false
    }
  }

  // ---------------------------------------------------------------------------
  // 会话切换
  // ---------------------------------------------------------------------------

  async function openGroup() {
    activeType.value = 'group'
    activePeer.value = null
    clearUnread(GROUP_KEY)
    await loadActiveHistory()
  }

  async function openPrivate(user) {
    if (!user || user.id == null) return
    const normalized = normalizeUser(user)
    activeType.value = 'private'
    activePeer.value = normalized
    clearUnread(privateKeyOf(normalized.id))
    await loadActiveHistory()
  }

  // ---------------------------------------------------------------------------
  // 发送
  // ---------------------------------------------------------------------------

  /** 发送文本：群聊 to=null，私聊 to=对端 id；成功返回 true（输入框据此清空） */
  function sendText(rawContent) {
    const content = String(rawContent ?? '').trim()
    if (!content) return false
    if (content.length > MAX_MESSAGE_LENGTH) {
      ElMessage.warning(`消息不能超过 ${MAX_MESSAGE_LENGTH} 个字符`)
      return false
    }
    const to = activeType.value === 'private' ? activePeer.value?.id ?? null : null
    const sent = socket?.sendChat(to, content)
    if (!sent) {
      ElMessage.warning('连接已断开，正在重连，请稍后再发送')
      return false
    }
    return true
  }

  /**
   * 上传文件：POST /api/files（multipart/form-data，字段 file + 可选 to）。
   * 成功后把文件消息加入当前会话（契约未提供文件历史接口，文件消息仅在本次会话展示）。
   */
  async function uploadFile(file, onProgress) {
    const to = activeType.value === 'private' ? activePeer.value?.id ?? null : null
    const form = new FormData()
    form.append('file', file)
    if (to != null) form.append('to', String(to))

    const response = await http.post('/api/files', form, {
      timeout: 0, // 20MB 大文件不设超时
      onUploadProgress: (event) => {
        if (typeof onProgress !== 'function') return
        const total = event.total || file.size || 0
        const percent = total ? Math.min(99, Math.round((event.loaded / total) * 100)) : 0
        onProgress(percent)
      }
    })

    const record = normalizeFileRecord(response?.file)
    if (!record) throw new Error('上传响应缺少 file 字段')
    if (typeof onProgress === 'function') onProgress(100)

    const receiverId = to == null ? null : Number(to)
    const message = {
      kind: 'file',
      uid: `file-${record.id}-${Date.now()}`,
      file: record,
      sender: normalizeUser(auth.user),
      receiverId,
      createdAt: Date.now()
    }
    insertMessage(conversationKeyOf({ receiverId, sender: { id: selfId.value } }, selfId.value), message)
    return { file: record, message }
  }

  /** 下载文件（REST 需要 Bearer 头，因此用 axios 取 blob 再触发浏览器下载） */
  async function downloadFile(file) {
    if (!file) return
    const url = file.downloadUrl || `/api/files/${file.id}/download`
    const blob = await http.get(url, { responseType: 'blob', timeout: 0 })
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = file.originalName || 'download'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
  }

  // ---------------------------------------------------------------------------
  // WebSocket 事件处理
  // ---------------------------------------------------------------------------

  function handleSocketMessage(payload) {
    const message = normalizeChatMessage(payload, selfId.value)
    const key = conversationKeyOf(payload, selfId.value)
    if (!insertMessage(key, message)) return
    if (key !== activeKey.value) bumpUnread(key)
    // 陌生人发来消息（用户列表未包含）时刷新列表
    if (message.sender && !users.value.some((user) => user.id === message.sender.id)) {
      loadUsers()
    }
  }

  function handleUserList(payload) {
    const list = Array.isArray(payload?.users) ? payload.users : []
    users.value = list.map(normalizeUser).filter(Boolean)
    syncActivePeer()
  }

  function handleFilePush(payload) {
    const file = normalizeFileRecord(payload?.file)
    const sender = normalizeUser(payload?.sender)
    if (!file || !sender) return
    const key = privateKeyOf(sender.id)
    const message = {
      kind: 'file',
      uid: `file-${file.id}-${Date.now()}`,
      file,
      sender,
      receiverId: payload?.receiverId == null ? selfId.value : payload.receiverId,
      createdAt: Date.now()
    }
    insertMessage(key, message)
    if (key !== activeKey.value) bumpUnread(key)
  }

  function handleSystem(payload) {
    const text = typeof payload?.message === 'string' ? payload.message : ''
    if (!text) return
    insertMessage(activeKey.value, {
      kind: 'system',
      uid: `system-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      message: text,
      createdAt: Date.now()
    })
  }

  function handleSocketError(payload) {
    const text = typeof payload?.message === 'string' ? payload.message : '服务端返回错误'
    ElMessage.error(text)
  }

  /**
   * 重连期间探测 token 是否仍然有效：
   * 401 → 拦截器强制回登录页；网络错误 → 说明只是后端未就绪，继续退避重连。
   */
  async function probeToken() {
    if (probingToken || !auth.token) return
    probingToken = true
    try {
      await http.get('/api/auth/me', { silent: true })
    } catch {
      /* 401 已由拦截器处理；其他错误忽略，继续重连 */
    } finally {
      probingToken = false
    }
  }

  async function refreshAfterReconnect() {
    await Promise.allSettled([loadUsers(), loadActiveHistory()])
    ElMessage.success('连接已恢复')
  }

  // ---------------------------------------------------------------------------
  // 生命周期
  // ---------------------------------------------------------------------------

  function connectSocket() {
    if (socket) socket.close()
    socket = new ChatSocket({
      message: handleSocketMessage,
      userList: handleUserList,
      file: handleFilePush,
      system: handleSystem,
      pong: () => {},
      error: handleSocketError,
      onOpen: ({ reconnected }) => {
        if (reconnected) refreshAfterReconnect()
      },
      onStatus: (state, info) => {
        connectionState.value = state
        connectionAttempt.value = Number(info?.attempt) || 0
        // 每 3 次重连失败探测一次 token（后端重启期间不会误踢，token 真过期则回登录页）
        if (state === 'reconnecting' && connectionAttempt.value > 0 && connectionAttempt.value % 3 === 0) {
          probeToken()
        }
      }
    })
    socket.connect(auth.token)
  }

  /** 进入聊天页：校验会话 → 建立 WS → 拉在线列表 → 拉群聊历史 */
  async function start() {
    if (!auth.user && auth.token) {
      try {
        await auth.fetchMe()
      } catch {
        /* 401 由拦截器处理，网络错误则继续尝试连接 */
      }
    }
    connectSocket()
    await Promise.allSettled([loadUsers(), openGroup()])
  }

  /** 退出登录 / 清理：断开 WS 并重置全部会话状态 */
  function reset() {
    if (socket) {
      socket.close()
      socket = null
    }
    users.value = []
    conversations.value = {}
    unread.value = {}
    activeType.value = 'group'
    activePeer.value = null
    connectionState.value = 'idle'
    connectionAttempt.value = 0
    historyLoading.value = false
  }

  return {
    // 状态
    users,
    activeType,
    activePeer,
    conversations,
    unread,
    connectionState,
    connectionAttempt,
    historyLoading,
    // 计算属性
    selfId,
    activeKey,
    activeMessages,
    onlineCount,
    totalUnread,
    connected,
    reconnecting,
    otherUsers,
    activeTitle,
    activeSubtitle,
    activePeerOnline,
    // 方法
    loadUsers,
    loadActiveHistory,
    openGroup,
    openPrivate,
    sendText,
    uploadFile,
    downloadFile,
    unreadOf,
    connectSocket,
    start,
    reset
  }
})

export default useChatStore
