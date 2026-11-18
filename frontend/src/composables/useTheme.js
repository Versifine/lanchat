import { ref } from 'vue'

const THEME_KEY = 'lanchat_theme'
const isDark = ref(localStorage.getItem(THEME_KEY) === 'dark')

function applyTheme() {
  const root = document.documentElement
  root.classList.toggle('dark', isDark.value)
  root.dataset.theme = isDark.value ? 'dark' : 'light'
}

/** 应用启动时调用一次：把持久化的主题应用到 html 元素 */
export function initTheme() {
  applyTheme()
}

/** 主题状态（模块级单例，任何组件共享） */
export function useTheme() {
  function toggleTheme() {
    isDark.value = !isDark.value
    localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
    applyTheme()
  }

  return { isDark, toggleTheme }
}

export default useTheme
