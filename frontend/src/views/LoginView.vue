<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧品牌区：渐变背景 + logo + slogan -->
      <section class="brand-panel">
        <div class="brand-top">
          <div class="brand-logo">L</div>
          <div class="brand-title">
            <h1>LanChat</h1>
            <p class="brand-slogan">局域网内的即时沟通</p>
          </div>
        </div>

        <ul class="brand-points">
          <li>
            <el-icon :size="16"><ChatDotRound /></el-icon>
            <span>群聊 / 私聊实时推送，离线消息自动补发</span>
          </li>
          <li>
            <el-icon :size="16"><FolderOpened /></el-icon>
            <span>文件上传下载，SHA-256 完整性校验</span>
          </li>
          <li>
            <el-icon :size="16"><Refresh /></el-icon>
            <span>心跳保活，断线自动重连</span>
          </li>
        </ul>

        <div class="brand-footer">
          <span class="brand-badge">B/S 架构</span>
          <span class="brand-badge">Vue 3 + Spring Boot</span>
        </div>
      </section>

      <!-- 右侧表单卡片 -->
      <section class="form-panel">
        <div class="form-brand">
          <div class="brand-logo small">L</div>
          <span>LanChat · 局域网内的即时沟通</span>
        </div>

        <h2 class="form-title">{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
        <p class="form-subtitle">
          {{ isRegister ? '注册后即可加入局域网聊天室' : '登录 LanChat，开始和同事聊天' }}
        </p>

        <el-tabs v-model="activeTab" class="form-tabs" stretch @tab-change="handleTabChange">
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          class="login-form"
          @submit.prevent="handleSubmit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="3–20 位字母、数字或下划线"
              clearable
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item v-if="isRegister" label="昵称" prop="nickname">
            <el-input
              v-model="form.nickname"
              placeholder="显示名称，留空默认同用户名"
              clearable
              maxlength="20"
              :prefix-icon="Avatar"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="6–32 位密码"
              show-password
              :prefix-icon="Lock"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
            />
          </el-form-item>

          <el-button
            class="submit-button"
            type="primary"
            :loading="submitting"
            native-type="submit"
          >
            {{ isRegister ? '注册并进入' : '登 录' }}
          </el-button>
        </el-form>

        <div class="form-switch">
          <span>{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
          <el-link type="primary" :underline="false" @click="switchTab">
            {{ isRegister ? '去登录' : '去注册' }}
          </el-link>
        </div>

        <p class="page-footer">Java语言及网络编程课程报告项目</p>
      </section>
    </div>

    <p class="login-page-footer">LanChat · 局域网聊天室 · Java语言及网络编程课程报告项目</p>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Avatar, ChatDotRound, FolderOpened, Lock, Refresh, User } from '@element-plus/icons-vue'
import { takeFlash } from '../api/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const activeTab = ref('login')
const isRegister = computed(() => activeTab.value === 'register')
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
  nickname: ''
})

/** 校验规则与 docs/SPEC.md FR-01 一致：用户名 3–20（字母/数字/下划线）、密码 6–32、昵称 ≤20 */
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_]{3,20}$/,
      message: '用户名需为 3–20 位字母、数字或下划线',
      trigger: ['blur', 'change']
    }
  ],
  nickname: [{ max: 20, message: '昵称不能超过 20 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码需为 6–32 位', trigger: 'blur' }
  ]
}

watch(activeTab, () => {
  formRef.value?.clearValidate()
})

function handleTabChange() {
  form.password = ''
}

function switchTab() {
  activeTab.value = isRegister.value ? 'login' : 'register'
}

/** 登录/注册成功：保存 token 与用户 → 跳转聊天页（ChatView 建立 WebSocket） */
async function handleSubmit() {
  if (submitting.value) return
  // 先上锁再校验：避免 form submit / 按钮点击等路径重复发起请求
  submitting.value = true
  try {
    await formRef.value.validate()
  } catch {
    submitting.value = false
    return
  }
  const username = form.username.trim()
  try {
    if (isRegister.value) {
      await auth.register({
        username,
        password: form.password,
        nickname: form.nickname.trim() || username
      })
      ElMessage.success('注册成功，正在进入聊天室…')
    } else {
      await auth.login({ username, password: form.password })
      ElMessage.success('登录成功')
    }
    router.replace('/chat')
  } catch {
    // 失败提示由 axios 拦截器统一处理（1002 重名 / 1003 用户名或密码错误等）
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // token 失效被强制跳回时的提示
  const message = takeFlash()
  if (message) ElMessage.warning(message)
})
</script>

<style scoped>
.login-page {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  min-height: 100vh;
  padding: 28px 20px;
  background: var(--lanchat-gradient);
  overflow: hidden;
}

/* 背景装饰光斑 */
.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
  filter: blur(2px);
  pointer-events: none;
}

.login-page::before {
  width: 420px;
  height: 420px;
  top: -140px;
  right: -120px;
}

.login-page::after {
  width: 300px;
  height: 300px;
  bottom: -120px;
  left: -90px;
}

.login-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  width: min(900px, 100%);
  border-radius: 18px;
  background: var(--lanchat-card);
  box-shadow: 0 24px 64px rgba(17, 24, 64, 0.32);
  overflow: hidden;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 26px;
  padding: 42px 34px 34px;
  background: var(--lanchat-gradient);
  color: #fff;
}

.brand-top {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  backdrop-filter: blur(4px);
}

.brand-logo.small {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 18px;
  background: var(--lanchat-gradient);
  border: none;
}

.brand-title h1 {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0.6px;
}

.brand-slogan {
  margin: 4px 0 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 14px;
  letter-spacing: 1px;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: rgba(255, 255, 255, 0.9);
  font-size: 13px;
  line-height: 1.5;
}

.brand-points li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.brand-points .el-icon {
  margin-top: 3px;
  opacity: 0.92;
}

.brand-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.brand-badge {
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.24);
  font-size: 11px;
  letter-spacing: 0.3px;
}

.form-panel {
  display: flex;
  flex-direction: column;
  padding: 38px 42px 24px;
  background: var(--lanchat-card);
}

.form-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  color: var(--lanchat-text-secondary);
  font-size: 13px;
}

.form-title {
  margin: 0;
  color: var(--lanchat-text);
  font-size: 23px;
  font-weight: 700;
}

.form-subtitle {
  margin: 6px 0 10px;
  color: var(--lanchat-text-secondary);
  font-size: 13px;
}

.form-tabs {
  margin-bottom: 8px;
}

.form-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.login-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  color: var(--lanchat-text);
  font-size: 13px;
  font-weight: 500;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 22px;
}

.submit-button {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  border: none;
  border-radius: var(--lanchat-radius-input);
  background: var(--lanchat-gradient);
  font-size: 15px;
  letter-spacing: 2px;
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.32);
}

.submit-button:hover {
  opacity: 0.94;
}

.form-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  color: var(--lanchat-text-secondary);
  font-size: 13px;
}

.page-footer {
  margin: auto 0 0;
  padding-top: 20px;
  color: var(--lanchat-text-secondary);
  font-size: 11px;
  text-align: center;
  letter-spacing: 0.2px;
}

.login-page-footer {
  position: relative;
  z-index: 1;
  margin: 0;
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  text-align: center;
}

html.dark .form-panel {
  background: var(--lanchat-card);
}

@media (max-width: 820px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
    width: min(440px, 100%);
  }
  .brand-panel {
    display: none;
  }
  .form-panel {
    padding: 30px 26px 20px;
  }
  .form-brand {
    display: flex;
  }
}

@media (max-width: 420px) {
  .login-page {
    padding: 16px 12px;
  }
  .form-panel {
    padding: 24px 18px 18px;
  }
}
</style>
