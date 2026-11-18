import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api/http'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'

/**
 * 使用 hash 路由（createWebHashHistory）：
 * 生产态由后端同端口托管 dist，无需额外配置 SPA history 回退。
 */
const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true, title: '登录 · LanChat' }
  },
  {
    path: '/chat',
    name: 'chat',
    component: ChatView,
    meta: { title: '聊天 · LanChat' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const hasToken = Boolean(getToken())
  // 未登录访问受保护页面 → 登录页
  if (!to.meta.public && !hasToken) return { name: 'login' }
  // 已登录访问登录页 → 聊天页
  if (to.name === 'login' && hasToken) return { name: 'chat' }
  return true
})

router.afterEach((to) => {
  document.title = to.meta?.title || 'LanChat · 局域网聊天室'
})

export default router
