<template>
  <div ref="scrollRef" class="message-list">
    <div v-if="historyLoading && !items.length" class="list-state">
      <el-icon class="is-loading" :size="14"><Loading /></el-icon>
      <span>正在加载历史消息…</span>
    </div>

    <div v-else-if="!items.length" class="list-state empty">
      <div class="empty-avatar">
        <el-icon :size="20"><ChatDotRound /></el-icon>
      </div>
      <p class="empty-title">{{ chat.activeTitle }}</p>
      <p class="empty-tip">NO MESSAGES YET — 发送第一条消息</p>
    </div>

    <template v-for="item in items" :key="item.key">
      <div v-if="item.type === 'separator'" class="time-separator">
        <span class="separator-label">{{ item.label }}</span>
      </div>

      <div v-else-if="item.type === 'system'" class="system-message">
        <span class="system-mark">*</span>
        <span>{{ item.message.message }}</span>
      </div>

      <div
        v-else
        class="message-row"
        :class="{ self: item.isSelf, grouped: item.grouped }"
      >
        <UserAvatar
          v-if="!item.grouped"
          class="message-avatar"
          :user="item.message.sender"
          :size="30"
        />

        <div class="message-main">
          <div v-if="!item.grouped" class="message-meta">
            <span v-if="!item.isSelf" class="message-sender">
              {{ senderName(item.message.sender) }}
            </span>
            <span v-if="item.message.offline" class="offline-tag">OFFLINE</span>
            <span class="message-time">{{ formatTime(item.message.createdAt) }}</span>
          </div>

          <FileMessage
            v-if="item.message.kind === 'file'"
            :file="item.message.file"
            :self="item.isSelf"
          />
          <div v-else class="message-bubble" :class="item.isSelf ? 'bubble-self' : 'bubble-other'">
            {{ item.message.content }}
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import UserAvatar from './UserAvatar.vue'
import FileMessage from './FileMessage.vue'
import { useChatStore } from '../stores/chat'
import { formatTime, formatTimeSeparator } from '../utils/format'

const chat = useChatStore()
const scrollRef = ref(null)

/** 连续消息合并阈值（5 分钟） */
const TIME_GAP = 5 * 60 * 1000
/** 距底部该距离内才自动滚动，避免打断用户回看历史 */
const AUTO_SCROLL_THRESHOLD = 140

/**
 * 把消息流展开成可渲染项：时间分隔行、系统消息、消息行。
 * 时间分隔：与上一条间隔超过 5 分钟时插入；连续同一发送者消息合并间距。
 */
const items = computed(() => {
  const list = chat.activeMessages
  const result = []
  let lastTimestamp = 0
  let lastSenderId = null
  let lastKind = ''

  for (const message of list) {
    if (message.kind === 'system') {
      result.push({ type: 'system', key: message.uid, message })
      lastSenderId = null
      lastKind = 'system'
      lastTimestamp = message.createdAt
      continue
    }

    const showSeparator = !lastTimestamp || message.createdAt - lastTimestamp > TIME_GAP
    if (showSeparator) {
      result.push({
        type: 'separator',
        key: `separator-${message.uid}`,
        label: formatTimeSeparator(message.createdAt)
      })
    }

    const isSelf = message.sender?.id === chat.selfId
    const grouped =
      !showSeparator && lastKind !== 'system' && lastSenderId === message.sender?.id

    result.push({ type: 'message', key: message.uid, message, isSelf, grouped })

    lastTimestamp = message.createdAt
    lastSenderId = message.sender?.id
    lastKind = message.kind
  }

  return result
})

function senderName(sender) {
  if (!sender) return '未知用户'
  return sender.nickname || sender.username || `用户${sender.id}`
}

function isNearBottom() {
  const element = scrollRef.value
  if (!element) return true
  return element.scrollHeight - element.scrollTop - element.clientHeight < AUTO_SCROLL_THRESHOLD
}

function scrollToBottom() {
  const element = scrollRef.value
  if (!element) return
  element.scrollTop = element.scrollHeight
}

watch(
  () => chat.activeMessages.length,
  async () => {
    const nearBottom = isNearBottom()
    await nextTick()
    if (nearBottom) scrollToBottom()
  }
)

watch(
  () => chat.activeKey,
  async () => {
    await nextTick()
    scrollToBottom()
  }
)

onMounted(async () => {
  await nextTick()
  scrollToBottom()
})
</script>

<style scoped>
.message-list {
  flex: 1 1 auto;
  min-height: 0;
  padding: 22px 26px 14px;
  overflow-y: auto;
  background: var(--paper);
}

.list-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  min-height: 160px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.06em;
}

.list-state.empty {
  flex-direction: column;
  gap: 6px;
}

.empty-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 8px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  color: var(--muted);
}

.empty-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 500;
}

.empty-tip {
  margin: 0;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.time-separator {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 18px 0 10px;
}

.time-separator::before,
.time-separator::after {
  content: '';
  flex: 1 1 auto;
  height: 1px;
  background: var(--line);
}

.separator-label {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
}

.system-message {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 10px 0;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.06em;
  text-align: center;
}

.system-mark {
  color: var(--accent);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 16px;
  animation: lanchat-fade-in 0.16s ease;
}

.message-row.grouped {
  margin-top: 4px;
}

.message-row.self {
  flex-direction: row-reverse;
}

.message-avatar {
  margin-top: 1px;
}

.message-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(70%, 620px);
  min-width: 0;
}

.message-row.self .message-main {
  align-items: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 2px 4px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.message-row.self .message-meta {
  flex-direction: row-reverse;
}

.message-sender {
  color: var(--muted);
  letter-spacing: 0.08em;
}

.message-time {
  color: var(--faint);
}

.offline-tag {
  padding: 0 3px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  color: var(--accent);
  font-size: 8px;
  letter-spacing: 0.1em;
}

.message-bubble {
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: 13.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.bubble-other {
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink);
}

.bubble-self {
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--paper);
}

.message-row.grouped .message-main {
  margin-left: 40px;
}

.message-row.grouped.self .message-main {
  margin-right: 40px;
  margin-left: 0;
}

@media (max-width: 640px) {
  .message-list {
    padding: 14px 12px 8px;
  }
  .message-main {
    max-width: 82%;
  }
  .message-row.grouped .message-main,
  .message-row.grouped.self .message-main {
    margin-right: 0;
    margin-left: 0;
  }
}
</style>
