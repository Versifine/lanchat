<template>
  <header class="app-header">
    <div class="header-left">
      <button
        type="button"
        class="menu-toggle"
        aria-label="切换会话列表"
        @click="emit('toggle-sidebar')"
      >
        <el-icon :size="16"><Menu /></el-icon>
      </button>

      <div class="brand">
        <span class="brand-logo">L</span>
        <span class="brand-name">LanChat</span>
        <span class="brand-kicker">LOCAL NETWORK MESSENGER</span>
      </div>

      <span class="online-pill">
        <span class="online-dot" :class="{ connected: chat.connected }"></span>
        ONLINE {{ chat.onlineCount }}
      </span>
    </div>

    <div class="header-right">
      <div v-if="auth.user" class="current-user">
        <UserAvatar :user="auth.user" :size="26" />
        <span class="current-user-name">{{ auth.user.nickname || auth.user.username }}</span>
      </div>

      <button
        type="button"
        class="theme-switch"
        :aria-label="isDark ? '切换到亮色模式' : '切换到暗色模式'"
        :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
        @click="toggleTheme"
      >
        <span class="theme-option" :class="{ active: !isDark }">LIGHT</span>
        <span class="theme-slash">/</span>
        <span class="theme-option" :class="{ active: isDark }">DARK</span>
      </button>

      <button type="button" class="logout-button" @click="handleLogout">
        <span class="logout-text">退出</span>
      </button>
    </div>
  </header>
</template>

<script setup>
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Menu } from '@element-plus/icons-vue'
import UserAvatar from './UserAvatar.vue'
import { useAuthStore } from '../stores/auth'
import { useChatStore } from '../stores/chat'
import { useTheme } from '../composables/useTheme'

const emit = defineEmits(['toggle-sidebar'])
const router = useRouter()
const auth = useAuthStore()
const chat = useChatStore()
const { isDark, toggleTheme } = useTheme()

function syncTitle() {
  document.title = chat.totalUnread > 0 ? `(${chat.totalUnread}) LanChat` : 'LanChat · 局域网聊天室'
}

// 未读总数同步到浏览器标题（便于多标签页时提醒）
watch(() => chat.totalUnread, syncTitle, { immediate: true })

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  chat.reset()
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  flex: 0 0 auto;
  height: var(--header-height);
  padding: 0 20px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  z-index: 20;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
}

.menu-toggle:hover {
  border-color: var(--line-strong);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid var(--ink);
  border-radius: var(--radius-xs);
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 14px;
  line-height: 1;
}

.brand-name {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: -0.01em;
  white-space: nowrap;
}

.brand-kicker {
  padding-left: 10px;
  border-left: 1px solid var(--line);
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.16em;
  white-space: nowrap;
}

.online-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding-left: 16px;
  border-left: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  white-space: nowrap;
}

.online-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--faint);
  transition: background-color 0.2s ease;
}

.online-dot.connected {
  background: var(--online);
}

.current-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.current-user-name {
  overflow: hidden;
  max-width: 150px;
  color: var(--ink);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-switch {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  cursor: pointer;
}

.theme-option {
  transition: color 0.16s ease;
}

.theme-option.active {
  color: var(--ink);
}

.theme-switch:hover .theme-option {
  color: var(--muted);
}

.theme-switch:hover .theme-option.active {
  color: var(--ink);
}

.theme-slash {
  color: var(--line-strong);
}

.logout-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
  text-decoration: underline;
  text-underline-offset: 3px;
  text-decoration-color: var(--line-strong);
  cursor: pointer;
}

.logout-button:hover {
  color: var(--accent);
  text-decoration-color: var(--accent);
}

@media (max-width: 900px) {
  .brand-kicker {
    display: none;
  }
}

@media (max-width: 820px) {
  .app-header {
    padding: 0 12px;
    gap: 10px;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .online-pill {
    padding-left: 10px;
  }
  .current-user-name {
    display: none;
  }
  .header-left,
  .header-right {
    gap: 10px;
  }
}

@media (max-width: 480px) {
  .brand-name {
    font-size: 16px;
  }
  .online-pill {
    border-left: 0;
    padding-left: 0;
  }
  .logout-button {
    display: none;
  }
}
</style>
