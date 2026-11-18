import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import http, { clearSession, getStoredUser, getToken, saveSession } from '../api/http'

/**
 * 认证状态：token + 当前用户，持久化到 localStorage。
 * POST /api/auth/login 与 /api/auth/register 返回结构一致：{ token, user:{id,username,nickname} }
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(getToken())
  const user = ref(getStoredUser())
  const isLoggedIn = computed(() => Boolean(token.value))

  function applySession(data) {
    token.value = data?.token || ''
    user.value = data?.user || null
    saveSession(token.value, user.value)
  }

  /** 登录：用户名+密码换 JWT（失败由 http 拦截器统一提示） */
  async function login({ username, password }) {
    const data = await http.post('/api/auth/login', { username, password })
    applySession(data)
    return data
  }

  /** 注册：成功直接返回 token，可免二次登录；昵称留空时用用户名兜底 */
  async function register({ username, password, nickname }) {
    const data = await http.post('/api/auth/register', {
      username,
      password,
      nickname: nickname || username
    })
    applySession(data)
    return data
  }

  /** GET /api/auth/me：刷新页面后校验 token 并补全用户信息 */
  async function fetchMe() {
    const me = await http.get('/api/auth/me')
    user.value = { ...(user.value || {}), ...me }
    saveSession(token.value, user.value)
    return me
  }

  /** 退出登录：仅清本地状态（JWT 无状态，服务端连接由 WS 关闭） */
  function logout() {
    token.value = ''
    user.value = null
    clearSession()
  }

  return { token, user, isLoggedIn, login, register, fetchMe, logout }
})

export default useAuthStore
