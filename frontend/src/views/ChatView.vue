<template>
  <div class="chat-view">
    <AppHeader @toggle-sidebar="sidebarOpen = !sidebarOpen" />

    <el-alert
      v-if="showReconnectAlert"
      class="connection-alert"
      :title="reconnectTitle"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="chat-body">
      <SideBar :open="sidebarOpen" @navigate="sidebarOpen = false" />
      <div v-if="sidebarOpen" class="sidebar-mask" @click="sidebarOpen = false"></div>

      <main class="chat-main">
        <div class="conversation-header">
          <div class="conversation-heading">
            <UserAvatar
              v-if="chat.activeType === 'private' && chat.activePeer"
              :user="chat.activePeer"
              :size="28"
              show-dot
              :online="chat.activePeerOnline"
            />
            <span v-else class="group-avatar">
              <el-icon :size="14"><UserFilled /></el-icon>
            </span>
            <div class="conversation-texts">
              <div class="conversation-name">{{ chat.activeTitle }}</div>
              <div class="conversation-subtitle" :class="{ offline: chat.activeType === 'private' && !chat.activePeerOnline }">
                {{ chat.activeSubtitle }}
              </div>
            </div>
          </div>

          <div class="conversation-status">
            <span class="status-dot" :class="{ connected: chat.connected }"></span>
            <span class="status-label">{{ statusLabel }}</span>
          </div>
        </div>

        <MessageList />
        <MessageInput />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import AppHeader from '../components/AppHeader.vue'
import SideBar from '../components/SideBar.vue'
import MessageList from '../components/MessageList.vue'
import MessageInput from '../components/MessageInput.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { useChatStore } from '../stores/chat'

const chat = useChatStore()
/** 窄屏下侧栏抽屉开关 */
const sidebarOpen = ref(false)

/** 断线（重连中）时顶部提示条 */
const showReconnectAlert = computed(() => chat.connectionState === 'reconnecting')

const reconnectTitle = computed(() => {
  const attempt = chat.connectionAttempt
  return attempt > 0 ? `连接断开，正在重连…（第 ${attempt} 次）` : '连接断开，正在重连…'
})

const statusLabel = computed(() => {
  if (chat.connectionState === 'connected') return '已连接'
  if (chat.connectionState === 'connecting') return '连接中…'
  if (chat.connectionState === 'reconnecting') return '重连中…'
  if (chat.connectionState === 'auth-failed') return '认证失效'
  return '未连接'
})

onMounted(() => {
  chat.start()
})
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  background: var(--paper);
}

.connection-alert {
  flex: 0 0 auto;
  padding: 6px 20px;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  border-radius: 0;
  background: var(--surface-muted);
}

.connection-alert :deep(.el-alert__content) {
  padding: 0;
}

.connection-alert :deep(.el-alert__title) {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
}

.connection-alert :deep(.el-alert__icon) {
  width: 13px;
  margin-right: 8px;
  color: var(--accent);
  font-size: 13px;
}

.chat-body {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}

.chat-main {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--paper);
}

.conversation-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  height: 54px;
  padding: 0 22px;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}

.conversation-heading {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.group-avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  background: var(--surface);
  color: var(--ink);
}

.conversation-texts {
  min-width: 0;
}

.conversation-name {
  overflow: hidden;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 500;
  letter-spacing: -0.01em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-subtitle {
  overflow: hidden;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.12em;
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

.conversation-subtitle.offline {
  color: var(--accent);
}

.conversation-status {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding-left: 14px;
  border-left: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.status-dot.connected {
  background: var(--online);
}

.sidebar-mask {
  display: none;
}

@media (max-width: 820px) {
  .sidebar-mask {
    position: absolute;
    inset: 0;
    z-index: 12;
    display: block;
    background: var(--overlay);
  }
  .conversation-header {
    height: 50px;
    padding: 0 12px;
  }
  .conversation-status {
    padding-left: 10px;
  }
}

@media (max-width: 520px) {
  .status-label {
    display: none;
  }
}
</style>
