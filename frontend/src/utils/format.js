/** 文件大小格式化：1048576 → 1.0 MB */
export function formatSize(bytes) {
  const size = Number(bytes) || 0
  if (size < 1024) return `${size} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = size
  let index = -1
  do {
    value /= 1024
    index += 1
  } while (value >= 1024 && index < units.length - 1)
  return `${value.toFixed(1)} ${units[index]}`
}

const pad = (value) => String(value).padStart(2, '0')

/** 消息时间：HH:mm（docs/ui-design.md 要求显示 HH:mm） */
export function formatTime(timestamp) {
  const date = new Date(Number(timestamp))
  if (Number.isNaN(date.getTime())) return ''
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 时间分隔标签：同一天显示 HH:mm，跨天补日期 */
export function formatTimeSeparator(timestamp) {
  const date = new Date(Number(timestamp))
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate()
  if (sameDay) return `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 文件扩展名（小写，不含点） */
export function fileExtension(name = '') {
  const index = String(name).lastIndexOf('.')
  return index > 0 ? String(name).slice(index + 1).toLowerCase() : ''
}

/** 文件类别：用于选图标与配色 */
export function fileKind(name = '') {
  const ext = fileExtension(name)
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(ext)) return 'image'
  if (['mp4', 'mov', 'avi', 'mkv', 'webm'].includes(ext)) return 'video'
  if (['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'].includes(ext)) return 'audio'
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext)) return 'archive'
  if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv'].includes(ext)) return 'doc'
  return 'other'
}
