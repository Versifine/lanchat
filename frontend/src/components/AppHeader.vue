<template>
  <header class="app-header">
    <div class="header-left">
      <el-button class="menu-toggle" text circle aria-label="切换会话列表" @click="emit('toggle-sidebar')">
        <el-icon :size="20"><Menu /></el-icon>
      </el-button>
      <div class="brand">
        <span class="brand-logo">L</span>
        <span class="brand-name">LanChat</span>
      </div>
      <span class="online-pill">
        <span class="online-dot" :class="{ connected: chat.connected }"></span>
        在线 {{ chat.onlineCount }} 人
      </span>
    </div>

    <div class="header-right">
      <div v-if="auth.user" class="current-user">
        <UserAvatar :user="auth.user" :size="32" />
        <span class="current-user-name">{{ auth.user.nickname || auth.user.username }}</span>
      </div>
      <el-tooltip :content="isDark ? '切换到亮色模式' : '切换到暗色模式'" placement="bottom">
        <el-switch
          class="theme-switch"
          :model-value="isDark"
          inline-prompt
          :active-icon="Moon"
          :inactive-icon="Sunny"
          @change="toggleTheme"
        />
      </el-tooltip>
      <el-button text class="logout-button" @click="handleLogout">
        <el-icon><SwitchButton /></el-icon>
        <span class="logout-text">退出</span>
      </el-button>
    </div>
  </header>
</template>

<script setup>
import { watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Menu, Moon, Sunny, SwitchButton } from '@element-plus/icons-vue'
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
  gap: 12px;
  height: var(--lanchat-header-height);
  padding: 0 18px;
  background: var(--lanchat-card);
  border-bottom: 1px solid var(--lanchat-border);
  box-shadow: var(--lanchat-shadow);
  flex: 0 0 auto;
  z-index: 20;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.menu-toggle {
  display: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  user-select: none;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--lanchat-gradient);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 3px 8px rgba(102, 126, 234, 0.35);
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.4px;
  background: var(--lanchat-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.online-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(103, 194, 58, 0.1);
  color: #529b2e;
  font-size: 12px;
  white-space: nowrap;
}

.online-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #909399;
  transition: background-color 0.2s ease;
}

.online-dot.connected {
  background: #67c23a;
  box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.18);
}

.current-user {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.current-user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--lanchat-text);
  font-weight: 500;
}

.theme-switch {
  flex: 0 0 auto;
}

.logout-button {
  color: var(--lanchat-text-secondary);
}

.logout-button:hover {
  color: #f56c6c;
}

@media (max-width: 820px) {
  .app-header {
    padding: 0 10px;
    gap: 6px;
  }
  .menu-toggle {
    display: inline-flex;
  }
  .brand-name {
    display: none;
  }
  .online-pill {
    padding: 2px 8px;
  }
  .current-user-name {
    display: none;
  }
  .logout-text {
    display: none;
  }
  .header-left,
  .header-right {
    gap: 8px;
  }
}
</style>
