<template>
  <div class="message-input">
    <transition name="el-fade-in">
      <div v-if="uploading" class="upload-panel">
        <div class="upload-head">
          <div class="upload-file">
            <el-icon :size="13"><Document /></el-icon>
            <span class="upload-name" :title="uploadName">{{ uploadName }}</span>
            <span class="upload-size">{{ formatSize(uploadSize) }}</span>
          </div>
          <span class="upload-percent">{{ uploadPercent }}%</span>
        </div>
        <el-progress
          :percentage="uploadPercent"
          :stroke-width="2"
          :show-text="false"
        />
      </div>
    </transition>

    <div class="input-toolbar">
      <el-tooltip content="发送文件（最大 20MB）" placement="top">
        <button
          type="button"
          class="tool-button"
          :disabled="uploading"
          aria-label="选择文件"
          @click="pickFile"
        >
          <el-icon :size="15"><Paperclip /></el-icon>
        </button>
      </el-tooltip>
      <span class="input-target">TO · {{ targetLabel }}</span>
      <span class="input-tip">ENTER 发送 · SHIFT + ENTER 换行</span>
      <span class="input-count" :class="{ warn: draft.length > MAX_MESSAGE_LENGTH - 100 }">
        {{ draft.length }} / {{ MAX_MESSAGE_LENGTH }}
      </span>
    </div>

    <div class="input-row">
      <el-input
        ref="inputRef"
        v-model="draft"
        type="textarea"
        :rows="2"
        resize="none"
        :maxlength="MAX_MESSAGE_LENGTH"
        placeholder="输入消息…"
        class="message-textarea"
        @keydown.enter="handleEnterKey"
      />
      <el-button
        class="send-button"
        type="primary"
        :disabled="!draft.trim()"
        aria-label="发送消息"
        @click="handleSend"
      >
        SEND
      </el-button>
    </div>

    <input ref="fileInputRef" class="hidden-file-input" type="file" @change="handleFilePicked" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Paperclip } from '@element-plus/icons-vue'
import { useChatStore, MAX_MESSAGE_LENGTH } from '../stores/chat'
import { formatSize } from '../utils/format'

/** 单文件上传上限（docs/SPEC.md SEC-05：文件 ≤20MB） */
const MAX_FILE_SIZE = 20 * 1024 * 1024

const chat = useChatStore()
const draft = ref('')
const inputRef = ref(null)
const fileInputRef = ref(null)

const uploading = ref(false)
const uploadPercent = ref(0)
const uploadName = ref('')
const uploadSize = ref(0)

const targetLabel = computed(() =>
  chat.activeType === 'private' ? chat.activeTitle : '群聊'
)

/** Enter 发送，Shift+Enter 换行；输入法组合输入（如中文候选词）不触发发送 */
function handleEnterKey(event) {
  if (event.isComposing || event.keyCode === 229) return
  if (event.shiftKey || event.altKey || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  handleSend()
}

function handleSend() {
  const content = draft.value
  if (!content.trim()) return
  if (chat.sendText(content)) {
    draft.value = ''
    inputRef.value?.focus?.()
  }
}

function pickFile() {
  if (uploading.value) return
  fileInputRef.value?.click()
}

async function handleFilePicked(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error('文件大小不能超过 20MB')
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  uploadName.value = file.name
  uploadSize.value = file.size
  try {
    await chat.uploadFile(file, (percent) => {
      uploadPercent.value = percent
    })
    uploadPercent.value = 100
    ElMessage.success('文件发送成功')
    window.setTimeout(() => {
      if (!uploading.value) return
      uploading.value = false
      uploadPercent.value = 0
    }, 700)
  } catch {
    uploading.value = false
    uploadPercent.value = 0
    // 错误提示由 axios 拦截器统一处理
  }
}
</script>

<style scoped>
.message-input {
  flex: 0 0 auto;
  padding: 12px 20px 16px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.upload-panel {
  margin-bottom: 10px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-xs);
  background: var(--surface-muted);
}

.upload-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
}

.upload-file {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: var(--ink);
  font-size: 12px;
}

.upload-name {
  overflow: hidden;
  max-width: 320px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-size {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.04em;
}

.upload-percent {
  flex: 0 0 auto;
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 7px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.tool-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}

.tool-button:hover:not(:disabled) {
  border-color: var(--line-strong);
  color: var(--ink);
}

.tool-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.input-target {
  white-space: nowrap;
}

.input-tip {
  margin-left: auto;
  color: var(--faint);
  white-space: nowrap;
}

.input-count {
  color: var(--faint);
  white-space: nowrap;
}

.input-count.warn {
  color: var(--accent);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.message-textarea {
  flex: 1 1 auto;
}

.message-textarea :deep(.el-textarea__inner) {
  min-height: 58px !important;
  padding: 9px 12px;
  border-radius: var(--radius-xs);
  font-family: var(--font-sans);
  font-size: 13.5px;
  line-height: 1.6;
  box-shadow: 0 0 0 1px var(--line) inset;
}

.message-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--ink) inset;
}

.message-textarea :deep(.el-input__count) {
  background: transparent;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 9px;
}

.send-button {
  flex: 0 0 auto;
  height: 38px;
  padding: 0 18px;
  border-radius: var(--radius-xs);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  /* 禁用态：细线描边 + 弱化文字（有内容时切换为墨底纸字） */
  --el-button-disabled-bg-color: transparent;
  --el-button-disabled-border-color: var(--line-strong);
  --el-button-disabled-text-color: var(--faint);
}

.send-button:not(.is-disabled) {
  --el-button-bg-color: var(--ink);
  --el-button-border-color: var(--ink);
  --el-button-text-color: var(--paper);
  --el-button-hover-bg-color: var(--ink-soft);
  --el-button-hover-border-color: var(--ink-soft);
  --el-button-hover-text-color: var(--paper);
}

.hidden-file-input {
  display: none;
}

@media (max-width: 640px) {
  .message-input {
    padding: 10px 12px 12px;
  }
  .input-tip {
    display: none;
  }
  .input-toolbar {
    gap: 10px;
    font-size: 8.5px;
  }
  .send-button {
    height: 36px;
    padding: 0 14px;
  }
}
</style>
