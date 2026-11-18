<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧品牌区：纸色 + 大号衬线字标 + 等宽小标签 -->
      <section class="brand-panel">
        <div class="brand-top">
          <div class="brand-mark">
            <span class="brand-logo">L</span>
            <span class="brand-kicker">LOCAL NETWORK MESSENGER</span>
          </div>
          <h1 class="brand-wordmark">LanChat</h1>
          <p class="brand-slogan">局域网内的即时沟通</p>
          <p class="brand-tags">
            B/S ARCHITECTURE · JAVA 21 · SPRING BOOT 3.5<br />
            VUE 3 · WEBSOCKET · SQLITE
          </p>
        </div>

        <ul class="brand-points">
          <li>
            <span class="point-index">01</span>
            <span>群聊 / 私聊实时推送，离线消息自动补发</span>
          </li>
          <li>
            <span class="point-index">02</span>
            <span>文件上传下载，SHA-256 完整性校验</span>
          </li>
          <li>
            <span class="point-index">03</span>
            <span>心跳保活，断线自动重连</span>
          </li>
        </ul>

        <div class="brand-rule"></div>

        <div class="brand-status">
          <span class="status-led"></span>
          <span class="status-text">OPERATIONAL</span>
          <span class="status-meta">WS /ws/chat · REST /api</span>
        </div>
      </section>

      <!-- 右侧表单面板：白色 + 1px 细线分隔 -->
      <section class="form-panel">
        <div class="form-brand">
          <span class="form-brand-logo">L</span>
          <span class="form-brand-name">LanChat</span>
          <span class="form-brand-tag">LOCAL NETWORK MESSENGER</span>
        </div>

        <p class="form-kicker">{{ isRegister ? 'REGISTER' : 'SIGN IN' }} — 局域网即时通讯</p>
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
          <el-form-item label="USERNAME / 用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="3–20 位字母、数字或下划线"
              clearable
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item v-if="isRegister" label="NICKNAME / 昵称" prop="nickname">
            <el-input
              v-model="form.nickname"
              placeholder="显示名称，留空默认同用户名"
              clearable
              maxlength="20"
              :prefix-icon="Avatar"
            />
          </el-form-item>

          <el-form-item label="PASSWORD / 密码" prop="password">
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
            {{ isRegister ? 'SIGN UP / 注 册' : 'LOG IN / 登 录' }}
          </el-button>
        </el-form>

        <div class="form-switch">
          <span class="switch-label">{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
          <el-link type="primary" :underline="false" @click="switchTab">
            {{ isRegister ? 'SIGN IN / 去登录' : 'SIGN UP / 去注册' }}
          </el-link>
        </div>

        <p class="page-footer">JAVA语言及网络编程 · 课程报告项目</p>
      </section>
    </div>

    <p class="login-page-footer">
      LANCHAT — LOCAL NETWORK MESSENGER · JAVA语言及网络编程课程报告项目 · 2026
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Avatar, Lock, User } from '@element-plus/icons-vue'
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
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  min-height: 100vh;
  padding: 40px 24px;
  background: var(--paper);
}

.login-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  width: min(1060px, 100%);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--paper);
  overflow: hidden;
}

/* 左侧：纸色品牌区 */
.brand-panel {
  display: flex;
  flex-direction: column;
  padding: 48px 54px 30px;
  background: var(--paper);
}

.brand-top {
  display: flex;
  flex-direction: column;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 44px;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--ink);
  border-radius: var(--radius-xs);
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 19px;
  line-height: 1;
}

.brand-kicker {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.2em;
}

.brand-wordmark {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: clamp(54px, 5.6vw, 76px);
  font-weight: 500;
  line-height: 0.98;
  letter-spacing: -0.03em;
}

.brand-slogan {
  margin: 18px 0 0;
  color: var(--muted);
  font-family: var(--font-serif);
  font-size: 16px;
  letter-spacing: 0.02em;
}

.brand-tags {
  margin: 28px 0 0;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  line-height: 1.9;
  letter-spacing: 0.14em;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 42px 0 0;
  padding: 0;
  list-style: none;
}

.brand-points li {
  display: flex;
  align-items: baseline;
  gap: 14px;
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.55;
}

.point-index {
  flex: 0 0 auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.1em;
}

.brand-rule {
  height: 1px;
  margin: 42px 0 0;
  background: var(--line);
}

.brand-status {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: auto;
  padding-top: 18px;
}

.status-led {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--online);
}

.status-text {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.status-meta {
  margin-left: auto;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.08em;
  white-space: nowrap;
}

/* 右侧：白色表单面板 */
.form-panel {
  display: flex;
  flex-direction: column;
  padding: 48px 54px 28px;
  border-left: 1px solid var(--line);
  background: var(--surface);
}

.form-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
}

.form-brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--ink);
  border-radius: var(--radius-xs);
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 15px;
  line-height: 1;
}

.form-brand-name {
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 500;
}

.form-brand-tag {
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
}

.form-kicker {
  margin: 0 0 12px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.form-title {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-serif);
  font-size: 32px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.form-subtitle {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}

.form-tabs {
  margin-top: 30px;
  margin-bottom: 10px;
}

.form-tabs :deep(.el-tabs__header) {
  margin-bottom: 22px;
}

.form-tabs :deep(.el-tabs__item) {
  padding: 0 0 0 0;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 21px;
}

.submit-button {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  border-radius: var(--radius-xs);
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.form-switch {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

.switch-label {
  letter-spacing: 0.1em;
}

.page-footer {
  margin: auto 0 0;
  padding-top: 26px;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.1em;
  text-align: center;
  text-transform: uppercase;
}

.login-page-footer {
  margin: 0;
  color: var(--faint);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.12em;
  text-align: center;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: minmax(0, 1fr);
    width: min(460px, 100%);
  }
  .brand-panel {
    display: none;
  }
  .form-panel {
    padding: 36px 30px 24px;
    border-left: 0;
  }
  .form-brand {
    display: flex;
  }
}

@media (max-width: 460px) {
  .login-page {
    padding: 20px 12px;
  }
  .form-panel {
    padding: 28px 20px 20px;
  }
  .form-title {
    font-size: 28px;
  }
}
</style>
