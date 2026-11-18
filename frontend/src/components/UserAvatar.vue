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
 * 头像：昵称首字 + 背景色哈希（同一用户颜色稳定），可选在线小圆点。
 */
const props = defineProps({
  user: { type: Object, default: () => ({}) },
  size: { type: Number, default: 36 },
  showDot: { type: Boolean, default: false },
  online: { type: Boolean, default: false }
})

const PALETTE = [
  '#667eea',
  '#409eff',
  '#67c23a',
  '#e6a23c',
  '#f56c6c',
  '#8e44ad',
  '#16a085',
  '#d35400',
  '#2c3e50',
  '#1abc9c'
]

const displayName = computed(
  () => props.user?.nickname || props.user?.username || props.user?.name || '未知用户'
)

const initial = computed(() => {
  const name = String(displayName.value).trim()
  return name ? Array.from(name)[0].toUpperCase() : '?'
})

const seed = computed(() => {
  const raw = String(props.user?.id ?? props.user?.username ?? displayName.value)
  let hash = 0
  for (let index = 0; index < raw.length; index += 1) {
    hash = (hash * 31 + raw.charCodeAt(index)) % 100000
  }
  return hash
})

const sizeClass = computed(() => {
  if (props.size <= 28) return 'sm'
  if (props.size <= 34) return 'md'
  if (props.size <= 40) return 'lg'
  return 'xl'
})

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: PALETTE[seed.value % PALETTE.length],
  fontSize: `${Math.round(props.size * 0.42)}px`
}))
</script>

<style scoped>
.user-avatar {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.5px;
  user-select: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.avatar-initial {
  line-height: 1;
  transform: translateY(0.5px);
}

.avatar-status {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #c0c4cc;
  border: 2px solid var(--lanchat-card);
  transition: background-color 0.2s ease;
}

.avatar-status.is-online {
  background: #67c23a;
}

.avatar-sm {
  box-shadow: none;
}
</style>
