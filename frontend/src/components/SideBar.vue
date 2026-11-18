<template>
  <aside class="sidebar" :class="{ 'is-open': open }">
    <div class="sidebar-search">
      <el-input
        v-model="keyword"
        placeholder="搜索用户"
        clearable
        :prefix-icon="Search"
        size="default"
      />
    </div>

    <div class="sidebar-section">
      <button
        type="button"
        class="conversation-item group-item"
        :class="{ active: chat.activeType === 'group' }"
        @click="handleOpenGroup"
      >
        <span class="group-avatar">
          <el-icon :size="20"><UserFilled /></el-icon>
        </span>
        <span class="conversation-info">
          <span class="conversation-name">群聊</span>
          <span class="conversation-desc">所有在线成员</span>
        </span>
        <span v-if="groupUnread" class="unread-badge">{{ formatBadge(groupUnread) }}</span>
      </button>
    </div>

    <div class="sidebar-section sidebar-users">
      <div class="section-title">
        <span>用户列表</span>
        <span class="section-count">{{ filteredUsers.length }}</span>
      </div>
      <div class="user-scroll">
        <button
          v-for="user in filteredUsers"
          :key="user.id"
          type="button"
          class="conversation-item user-item"
          :class="{ active: isActiveUser(user), offline: !user.online }"
          @click="handleOpenPrivate(user)"
        >
          <UserAvatar :user="user" :size="38" show-dot :online="user.online" />
          <span class="conversation-info">
            <span class="conversation-name">{{ user.nickname || user.username }}</span>
            <span class="conversation-desc">
              {{ user.online ? '在线' : '离线' }}
            </span>
          </span>
          <span v-if="userUnread(user.id)" class="unread-badge">
            {{ formatBadge(userUnread(user.id)) }}
          </span>
        </button>

        <div v-if="!filteredUsers.length" class="user-empty">
          <el-icon :size="26"><ChatDotRound /></el-icon>
          <p>{{ keyword ? '没有匹配的用户' : '暂无其他用户，快去邀请同事吧' }}</p>
        </div>
      </div>
    </div>

    <div class="sidebar-footer">
      <span class="footer-dot"></span>
      共 {{ chat.users.length }} 位用户 · {{ chat.onlineCount }} 人在线
    </div>
  </aside>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ChatDotRound, Search, UserFilled } from '@element-plus/icons-vue'
import UserAvatar from './UserAvatar.vue'
import { useChatStore, privateKeyOf } from '../stores/chat'

defineProps({
  /** 窄屏抽屉是否展开 */
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['navigate'])

const chat = useChatStore()
const keyword = ref('')

const filteredUsers = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return chat.otherUsers
  return chat.otherUsers.filter((user) =>
    `${user.nickname || ''} ${user.username || ''}`.toLowerCase().includes(query)
  )
})

const groupUnread = computed(() => chat.unreadOf('group'))

function userUnread(userId) {
  return chat.unreadOf(privateKeyOf(userId))
}

function formatBadge(count) {
  return count > 99 ? '99+' : String(count)
}

function isActiveUser(user) {
  return chat.activeType === 'private' && chat.activePeer?.id === user.id
}

async function handleOpenGroup() {
  await chat.openGroup()
  emit('navigate')
}

async function handleOpenPrivate(user) {
  await chat.openPrivate(user)
  emit('navigate')
}
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  flex: 0 0 var(--lanchat-sidebar-width);
  width: var(--lanchat-sidebar-width);
  min-height: 0;
  padding: 14px 12px 10px;
  background: var(--lanchat-card);
  border-right: 1px solid var(--lanchat-border);
  z-index: 15;
}

.sidebar-search {
  flex: 0 0 auto;
  margin-bottom: 12px;
}

.sidebar-section {
  flex: 0 0 auto;
}

.sidebar-users {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  margin-top: 6px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px 8px;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  letter-spacing: 0.4px;
}

.section-count {
  padding: 0 6px;
  border-radius: 8px;
  background: rgba(144, 147, 153, 0.14);
}

.user-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.conversation-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  margin-bottom: 4px;
  border: 1px solid transparent;
  border-radius: var(--lanchat-radius-input);
  background: transparent;
  color: var(--lanchat-text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, transform 0.18s ease;
}

.conversation-item:hover {
  background: var(--lanchat-card-subtle);
}

.conversation-item.active {
  background: rgba(64, 158, 255, 0.1);
  border-color: rgba(64, 158, 255, 0.35);
}

.group-item {
  margin-bottom: 2px;
}

.group-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--lanchat-gradient);
  color: #fff;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.3);
}

.conversation-info {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
}

.conversation-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-desc {
  overflow: hidden;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-item.offline {
  opacity: 0.62;
}

.user-item.offline .conversation-name {
  color: var(--lanchat-text-secondary);
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  box-shadow: 0 1px 4px rgba(245, 108, 108, 0.45);
}

.user-empty {
  padding: 28px 8px;
  color: var(--lanchat-text-secondary);
  font-size: 13px;
  text-align: center;
}

.user-empty p {
  margin: 8px 0 0;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--lanchat-border);
  color: var(--lanchat-text-secondary);
  font-size: 12px;
}

.footer-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #67c23a;
}

@media (max-width: 820px) {
  .sidebar {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    transform: translateX(-102%);
    transition: transform 0.25s ease;
    box-shadow: 8px 0 24px rgba(0, 0, 0, 0.18);
  }
  .sidebar.is-open {
    transform: translateX(0);
  }
}
</style>
