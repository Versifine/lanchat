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
              :size="34"
              show-dot
              :online="chat.activePeerOnline"
            />
            <span v-else class="group-avatar">
              <el-icon :size="18"><UserFilled /></el-icon>
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
            <span>{{ statusLabel }}</span>
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

/** 断线（重连中）时顶部黄色提示条 */
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
  background: var(--lanchat-bg);
}

.connection-alert {
  flex: 0 0 auto;
  border-radius: 0;
}

.connection-alert :deep(.el-alert__content) {
  padding: 2px 0;
}

.chat-body {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}

.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  background: var(--lanchat-bg);
}

.conversation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex: 0 0 auto;
  height: 58px;
  padding: 0 20px;
  border-bottom: 1px solid var(--lanchat-border);
  background: var(--lanchat-card);
}

.conversation-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.group-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--lanchat-gradient);
  color: #fff;
}

.conversation-texts {
  min-width: 0;
}

.conversation-name {
  overflow: hidden;
  color: var(--lanchat-text);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-subtitle {
  overflow: hidden;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-subtitle.offline {
  color: #b88230;
}

.conversation-status {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #e6a23c;
}

.status-dot.connected {
  background: #67c23a;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.16);
}

.sidebar-mask {
  display: none;
}

@media (max-width: 820px) {
  .sidebar-mask {
    display: block;
    position: absolute;
    inset: 0;
    z-index: 12;
    background: rgba(0, 0, 0, 0.32);
  }
  .conversation-header {
    height: 52px;
    padding: 0 12px;
  }
  .conversation-status {
    display: none;
  }
}
</style>
