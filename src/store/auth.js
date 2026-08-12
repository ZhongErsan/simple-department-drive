import { reactive } from 'vue'

const STORAGE_KEY = 'department-drive-auth'

function readStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

const saved = readStorage()

export const authState = reactive({
  token: saved.token || '',
  userId: saved.userId ?? null,
  realName: saved.realName || '',
  role: saved.role || '',
  departmentId: saved.departmentId ?? null
})

function persist() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      token: authState.token,
      userId: authState.userId,
      realName: authState.realName,
      role: authState.role,
      departmentId: authState.departmentId
    })
  )
}

export function setSession(data) {
  authState.token = data.token
  authState.userId = data.userId
  authState.realName = data.realName
  authState.role = data.role
  authState.departmentId = data.departmentId ?? null
  persist()
}

export function clearSession() {
  authState.token = ''
  authState.userId = null
  authState.realName = ''
  authState.role = ''
  authState.departmentId = null
  localStorage.removeItem(STORAGE_KEY)
}

export function isAdmin() {
  return authState.role === 'ADMIN'
}

export function isMinister() {
  return authState.role === 'MINISTER'
}

export function isMember() {
  return authState.role === 'MEMBER'
}
