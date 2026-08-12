export function formatBytes(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes === 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const index = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1
  )
  const number = bytes / 1024 ** index
  return `${number >= 100 ? number.toFixed(0) : number.toFixed(2)} ${units[index]}`
}

export function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ')
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

export function roleLabel(role) {
  return {
    ADMIN: '系统管理员',
    MINISTER: '部门部长',
    MEMBER: '普通成员'
  }[role] || role || '-'
}

export function statusLabel(status) {
  return {
    ACTIVE: '启用',
    DISABLED: '禁用',
    DELETED: '已删除'
  }[status] || status || '-'
}

export function areaLabel(area) {
  return {
    PUBLIC: '公共区',
    PERSONAL: '个人空间',
    CONTRIBUTION: '投稿区'
  }[area] || area || '-'
}

export function bytesToGb(bytes) {
  const value = Number(bytes)
  if (!Number.isFinite(value)) return 1
  return Number((value / 1024 ** 3).toFixed(2))
}

export function gbToBytes(gb) {
  return Math.round(Number(gb || 0) * 1024 ** 3)
}

export function quotaPercent(used, quota) {
  const q = Number(quota)
  const u = Number(used)
  if (!Number.isFinite(q) || q <= 0 || !Number.isFinite(u)) return 0
  return Math.min(100, Math.round((u / q) * 100))
}
