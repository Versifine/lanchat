<template>
  <div ref="scrollRef" class="message-list">
    <div v-if="historyLoading && !items.length" class="list-state">
      <el-icon class="is-loading" :size="18"><Loading /></el-icon>
      <span>正在加载历史消息…</span>
    </div>

    <div v-else-if="!items.length" class="list-state empty">
      <div class="empty-avatar">
        <el-icon :size="30"><ChatDotRound /></el-icon>
      </div>
      <p class="empty-title">{{ chat.activeTitle }}</p>
      <p class="empty-tip">还没有消息，发送第一条吧</p>
    </div>

    <template v-for="item in items" :key="item.key">
      <div v-if="item.type === 'separator'" class="time-separator">
        <span class="separator-label">{{ item.label }}</span>
      </div>

      <div v-else-if="item.type === 'system'" class="system-message">
        <el-icon :size="13"><InfoFilled /></el-icon>
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
          :size="34"
        />

        <div class="message-main">
          <div v-if="!item.grouped" class="message-meta">
            <span v-if="!item.isSelf" class="message-sender">
              {{ senderName(item.message.sender) }}
            </span>
            <span v-if="item.message.offline" class="offline-tag">离线消息</span>
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
import { ChatDotRound, InfoFilled, Loading } from '@element-plus/icons-vue'
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
  overflow-y: auto;
  padding: 18px 22px 10px;
}

.list-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  min-height: 160px;
  color: var(--lanchat-text-secondary);
  font-size: 13px;
}

.list-state.empty {
  flex-direction: column;
  gap: 4px;
}

.empty-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 6px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.1);
  color: var(--lanchat-primary);
}

.empty-title {
  margin: 0;
  color: var(--lanchat-text);
  font-size: 15px;
  font-weight: 600;
}

.empty-tip {
  margin: 0;
}

.time-separator {
  display: flex;
  justify-content: center;
  margin: 14px 0 12px;
}

.separator-label {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(144, 147, 153, 0.12);
  color: var(--lanchat-text-secondary);
  font-size: 11px;
}

.system-message {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  margin: 10px 0;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  text-align: center;
}

.system-message span {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(144, 147, 153, 0.1);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  animation: lanchat-fade-in 0.18s ease;
}

.message-row.grouped {
  margin-top: 4px;
}

.message-row.self {
  flex-direction: row-reverse;
}

.message-avatar {
  margin-top: 2px;
}

.message-main {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: min(72%, 640px);
  min-width: 0;
}

.message-row.self .message-main {
  align-items: flex-end;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 4px 4px;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
}

.message-row.self .message-meta {
  flex-direction: row-reverse;
}

.message-sender {
  color: var(--lanchat-text-secondary);
  font-weight: 500;
}

.message-time {
  font-size: 11px;
  opacity: 0.85;
}

.offline-tag {
  padding: 0 5px;
  border-radius: 6px;
  background: rgba(230, 162, 60, 0.16);
  color: #b88230;
  font-size: 11px;
}

.message-bubble {
  padding: 9px 13px;
  border-radius: var(--lanchat-radius-bubble);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.bubble-other {
  border: 1px solid var(--lanchat-border);
  border-top-left-radius: 4px;
  background: var(--lanchat-bubble-other-bg);
  color: var(--lanchat-bubble-other-text);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.bubble-self {
  border-top-right-radius: 4px;
  background: var(--lanchat-bubble-self);
  color: var(--lanchat-bubble-self-text);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.28);
}

.message-row.grouped .message-main {
  margin-left: 44px;
}

.message-row.grouped.self .message-main {
  margin-left: 0;
  margin-right: 44px;
}

@media (max-width: 640px) {
  .message-list {
    padding: 12px 12px 8px;
  }
  .message-main {
    max-width: 82%;
  }
  .message-row.grouped .message-main,
  .message-row.grouped.self .message-main {
    margin-left: 0;
    margin-right: 0;
  }
}
</style>
