<template>
  <div class="message-input">
    <transition name="el-fade-in">
      <div v-if="uploading" class="upload-panel">
        <div class="upload-head">
          <div class="upload-file">
            <el-icon><Document /></el-icon>
            <span class="upload-name" :title="uploadName">{{ uploadName }}</span>
            <span class="upload-size">{{ formatSize(uploadSize) }}</span>
          </div>
          <span class="upload-percent">{{ uploadPercent }}%</span>
        </div>
        <el-progress
          :percentage="uploadPercent"
          :stroke-width="6"
          :show-text="false"
          :status="uploadPercent >= 100 ? 'success' : ''"
        />
      </div>
    </transition>

    <div class="input-toolbar">
      <el-tooltip content="发送文件（最大 20MB）" placement="top">
        <el-button
          class="tool-button"
          text
          circle
          :disabled="uploading"
          aria-label="选择文件"
          @click="pickFile"
        >
          <el-icon :size="19"><Paperclip /></el-icon>
        </el-button>
      </el-tooltip>
      <span class="input-target">
        <el-icon :size="13"><ChatLineRound /></el-icon>
        发送至 {{ targetLabel }}
      </span>
      <span class="input-tip">Enter 发送 · Shift + Enter 换行</span>
      <span class="input-count" :class="{ warn: draft.length > MAX_MESSAGE_LENGTH - 100 }">
        {{ draft.length }}/{{ MAX_MESSAGE_LENGTH }}
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
        placeholder="输入消息，Enter 发送"
        class="message-textarea"
        @keydown.enter="handleEnterKey"
      />
      <el-button
        class="send-button"
        type="primary"
        :disabled="!draft.trim()"
        @click="handleSend"
      >
        <el-icon><Promotion /></el-icon>
        <span>发送</span>
      </el-button>
    </div>

    <input ref="fileInputRef" class="hidden-file-input" type="file" @change="handleFilePicked" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, Document, Paperclip, Promotion } from '@element-plus/icons-vue'
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
  padding: 10px 18px 14px;
  border-top: 1px solid var(--lanchat-border);
  background: var(--lanchat-card);
}

.upload-panel {
  margin-bottom: 8px;
  padding: 8px 12px;
  border: 1px solid var(--lanchat-border);
  border-radius: var(--lanchat-radius-input);
  background: var(--lanchat-card-subtle);
}

.upload-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.upload-file {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--lanchat-text);
  font-size: 13px;
}

.upload-name {
  overflow: hidden;
  max-width: 320px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-size {
  color: var(--lanchat-text-secondary);
  font-size: 12px;
}

.upload-percent {
  flex: 0 0 auto;
  color: var(--lanchat-primary);
  font-size: 12px;
  font-weight: 600;
}

.input-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 4px;
}

.tool-button {
  color: var(--lanchat-text-secondary);
}

.tool-button:hover {
  color: var(--lanchat-primary);
}

.input-target {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.1);
  color: var(--lanchat-primary);
  font-size: 12px;
  white-space: nowrap;
}

.input-tip {
  margin-left: auto;
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.input-count {
  color: var(--lanchat-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.input-count.warn {
  color: #e6a23c;
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
  border-radius: var(--lanchat-radius-input);
  padding: 9px 12px;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  box-shadow: 0 0 0 1px var(--lanchat-border) inset;
}

.send-button {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 18px;
  border-radius: var(--lanchat-radius-input);
  background: var(--lanchat-gradient);
  border: none;
}

.send-button:hover:not(.is-disabled) {
  opacity: 0.92;
}

.hidden-file-input {
  display: none;
}

@media (max-width: 640px) {
  .message-input {
    padding: 8px 10px 10px;
  }
  .input-tip {
    display: none;
  }
  .input-target {
    font-size: 11px;
  }
  .send-button {
    padding: 0 12px;
  }
}
</style>
