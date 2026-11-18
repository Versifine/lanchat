<template>
  <div class="file-message" :class="{ self }">
    <div class="file-icon" :style="{ background: iconMeta.background, color: iconMeta.color }">
      <el-icon :size="22"><component :is="iconMeta.icon" /></el-icon>
    </div>
    <div class="file-info">
      <div class="file-name" :title="file.originalName">{{ file.originalName }}</div>
      <div class="file-size">
        {{ formatSize(file.size) }}
        <el-tooltip v-if="file.sha256" :content="`SHA-256: ${file.sha256}`" placement="top">
          <span class="file-checksum">SHA-256 校验</span>
        </el-tooltip>
      </div>
    </div>
    <el-tooltip content="下载文件" placement="top">
      <el-button
        class="file-download"
        text
        circle
        :loading="downloading"
        :disabled="downloading"
        @click="handleDownload"
      >
        <el-icon v-if="!downloading" :size="18"><Download /></el-icon>
      </el-button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  Document,
  Download,
  Files,
  Folder,
  Headset,
  Picture,
  VideoCamera
} from '@element-plus/icons-vue'
import { useChatStore } from '../stores/chat'
import { fileKind, formatSize } from '../utils/format'

const props = defineProps({
  file: { type: Object, required: true },
  self: { type: Boolean, default: false }
})

const chat = useChatStore()
const downloading = ref(false)

const ICON_META = {
  image: { icon: Picture, background: 'rgba(103, 194, 58, 0.14)', color: '#529b2e' },
  video: { icon: VideoCamera, background: 'rgba(230, 162, 60, 0.16)', color: '#b88230' },
  audio: { icon: Headset, background: 'rgba(142, 68, 173, 0.14)', color: '#8e44ad' },
  archive: { icon: Folder, background: 'rgba(230, 162, 60, 0.16)', color: '#b88230' },
  doc: { icon: Document, background: 'rgba(64, 158, 255, 0.14)', color: '#337ecc' },
  other: { icon: Files, background: 'rgba(144, 147, 153, 0.16)', color: '#73767a' }
}

const iconMeta = computed(() => ICON_META[fileKind(props.file.originalName)] || ICON_META.other)

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
  gap: 10px;
  min-width: 220px;
  max-width: min(360px, 100%);
  padding: 10px 12px;
  border: 1px solid var(--lanchat-border);
  border-radius: var(--lanchat-radius-card);
  background: var(--lanchat-card-subtle);
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.file-message:hover {
  box-shadow: var(--lanchat-shadow);
}

.file-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  border-radius: 10px;
}

.file-info {
  flex: 1 1 auto;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  color: var(--lanchat-text);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
}

.file-checksum {
  padding: 0 5px;
  border-radius: 6px;
  background: rgba(103, 194, 58, 0.12);
  color: #529b2e;
  font-size: 11px;
  cursor: help;
}

.file-download {
  flex: 0 0 auto;
  color: var(--lanchat-primary);
}

@media (max-width: 480px) {
  .file-message {
    min-width: 0;
    max-width: 100%;
  }
}
</style>
