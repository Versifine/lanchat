<template>
  <aside class="sidebar" :class="{ 'is-open': open }">
    <div class="sidebar-search">
      <el-input
        v-model="keyword"
        placeholder="搜索用户 / SEARCH"
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
          <el-icon :size="14"><UserFilled /></el-icon>
        </span>
        <span class="conversation-info">
          <span class="conversation-name">群聊</span>
          <span class="conversation-desc">ALL MEMBERS</span>
        </span>
        <span v-if="groupUnread" class="unread-badge">{{ formatBadge(groupUnread) }}</span>
      </button>
    </div>

    <div class="sidebar-section sidebar-users">
      <div class="section-title">
        <span class="section-label">MEMBERS</span>
        <span class="section-count">{{ String(filteredUsers.length).padStart(2, '0') }}</span>
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
          <UserAvatar :user="user" :size="28" show-dot :online="user.online" />
          <span class="conversation-info">
            <span class="conversation-name">{{ user.nickname || user.username }}</span>
            <span class="conversation-desc">
              {{ user.online ? 'ONLINE' : 'OFFLINE' }}
            </span>
          </span>
          <span v-if="userUnread(user.id)" class="unread-badge">
            {{ formatBadge(userUnread(user.id)) }}
          </span>
        </button>

        <div v-if="!filteredUsers.length" class="user-empty">
          <el-icon :size="20"><ChatDotRound /></el-icon>
          <p>{{ keyword ? '没有匹配的用户' : '暂无其他用户' }}</p>
        </div>
      </div>
    </div>

    <div class="sidebar-footer">
      <span class="footer-dot"></span>
      <span>{{ String(chat.users.length).padStart(2, '0') }} USERS · {{ String(chat.onlineCount).padStart(2, '0') }} ONLINE</span>
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
  flex: 0 0 var(--sidebar-width);
  flex-direction: column;
  width: var(--sidebar-width);
  min-height: 0;
  padding: 16px 14px 10px;
  background: var(--paper);
  border-right: 1px solid var(--line);
  z-index: 15;
}

.sidebar-search {
  flex: 0 0 auto;
  margin-bottom: 18px;
}

.sidebar-search :deep(.el-input__wrapper) {
  background-color: var(--surface);
}

.sidebar-search :deep(.el-input__inner) {
  font-size: 12.5px;
}

.sidebar-section {
  flex: 0 0 auto;
}

.sidebar-users {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  margin-top: 2px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 8px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.section-count {
  color: var(--faint);
}

.user-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.conversation-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-bottom: 1px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.14s ease, border-color 0.14s ease;
}

.conversation-item::before {
  content: '';
  position: absolute;
  top: -1px;
  bottom: -1px;
  left: -1px;
  width: 2px;
  background: transparent;
}

.conversation-item:hover {
  background: var(--hover);
}

.conversation-item.active {
  border-color: var(--line);
  background: var(--surface);
}

.conversation-item.active::before {
  background: var(--ink);
}

.group-item {
  padding: 8px;
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

.conversation-info {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.conversation-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-desc {
  overflow: hidden;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.14em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-item.offline {
  opacity: 0.55;
}

.user-item.offline .conversation-name {
  color: var(--muted);
}

.unread-badge {
  flex: 0 0 auto;
  min-width: 14px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1;
  text-align: right;
  letter-spacing: 0;
}

.user-empty {
  padding: 30px 10px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
  text-align: center;
}

.user-empty .el-icon {
  color: var(--faint);
}

.user-empty p {
  margin: 8px 0 0;
}

.sidebar-footer {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  white-space: nowrap;
}

.footer-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--online);
}

@media (max-width: 820px) {
  .sidebar {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    transform: translateX(-101%);
    transition: transform 0.22s ease;
    box-shadow: 12px 0 32px rgba(18, 17, 15, 0.14);
  }
  .sidebar.is-open {
    transform: translateX(0);
  }
}
</style>
