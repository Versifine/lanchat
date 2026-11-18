<template>
  <span
    class="user-avatar"
    :class="`avatar-${sizeClass}`"
    :style="avatarStyle"
    :title="displayName"
  >
    <span class="avatar-initial">{{ initial }}</span>
    <span v-if="showDot" class="avatar-status" :class="{ 'is-online': online }"></span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 头像：方形细线小方块 + 等宽首字母（编辑排版风格，单色无彩色）。
 * 可选在线小圆点（绿色仅用于在线状态）。
 */
const props = defineProps({
  user: { type: Object, default: () => ({}) },
  size: { type: Number, default: 28 },
  showDot: { type: Boolean, default: false },
  online: { type: Boolean, default: false }
})

const displayName = computed(
  () => props.user?.nickname || props.user?.username || props.user?.name || '未知用户'
)

const initial = computed(() => {
  const name = String(displayName.value).trim()
  return name ? Array.from(name)[0].toUpperCase() : '?'
})

const sizeClass = computed(() => {
  if (props.size <= 26) return 'sm'
  if (props.size <= 32) return 'md'
  if (props.size <= 38) return 'lg'
  return 'xl'
})

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(10, Math.round(props.size * 0.38))}px`
}))
</script>

<style scoped>
.user-avatar {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  background: var(--surface-muted);
  color: var(--ink);
  font-family: var(--font-mono);
  font-weight: 500;
  letter-spacing: 0;
  user-select: none;
}

.avatar-initial {
  line-height: 1;
  transform: translateY(0.5px);
}

.avatar-status {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--line-strong);
  border: 1px solid var(--surface);
  transition: background-color 0.2s ease;
}

.avatar-status.is-online {
  background: var(--online);
}

.avatar-sm .avatar-status {
  right: -2px;
  bottom: -2px;
}
</style>
