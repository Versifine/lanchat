<template>
  <div class="file-message" :class="{ self }">
    <div class="file-icon">
      <span class="file-ext">{{ extension }}</span>
    </div>
    <div class="file-info">
      <div class="file-name" :title="file.originalName">{{ file.originalName }}</div>
      <div class="file-meta">
        <span class="file-size">{{ formatSize(file.size) }}</span>
        <el-tooltip v-if="file.sha256" :content="`SHA-256: ${file.sha256}`" placement="top">
          <span class="file-checksum">SHA-256</span>
        </el-tooltip>
      </div>
    </div>
    <button
      type="button"
      class="file-download"
      :disabled="downloading"
      :aria-label="`下载 ${file.originalName}`"
      @click="handleDownload"
    >
      {{ downloading ? '···' : 'DOWNLOAD' }}
    </button>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useChatStore } from '../stores/chat'
import { fileExtension, formatSize } from '../utils/format'

const props = defineProps({
  file: { type: Object, required: true },
  self: { type: Boolean, default: false }
})

const chat = useChatStore()
const downloading = ref(false)

const extension = computed(() => {
  const ext = fileExtension(props.file.originalName)
  return (ext || 'file').slice(0, 4).toUpperCase()
})

async function handleDownload() {
  if (downloading.value) return
  downloading.value = true
  try {
    await chat.downloadFile(props.file)
  } catch {
    /* 错误提示由 axios 拦截器统一处理 */
  } finally {
    downloading.value = false
  }
}
</script>

<style scoped>
.file-message {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 240px;
  max-width: min(380px, 100%);
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  transition: border-color 0.16s ease;
}

.file-message:hover {
  border-color: var(--line-strong);
}

.file-message.self {
  border-color: var(--line-strong);
}

.file-icon {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-xs);
  color: var(--ink);
  background: transparent;
}

.file-ext {
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.06em;
}

.file-info {
  flex: 1 1 auto;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.06em;
}

.file-checksum {
  color: var(--online);
  cursor: help;
  text-decoration: underline dotted;
  text-underline-offset: 2px;
}

.file-download {
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.12em;
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.file-download:hover:not(:disabled) {
  color: var(--ink);
}

.file-download:disabled {
  color: var(--faint);
  cursor: wait;
}

@media (max-width: 480px) {
  .file-message {
    min-width: 0;
    max-width: 100%;
  }
}
</style>
