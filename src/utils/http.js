import axios from 'axios'
import { ElMessage } from 'element-plus'
import { authState, clearSession } from '../store/auth'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20000
})

http.interceptors.request.use((config) => {
  if (authState.token) {
    config.headers.Authorization = `Bearer ${authState.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return response
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const message =
      error.response?.data?.message ||
      error.message ||
      '请求失败，请稍后重试'

    if (status === 401) {
      clearSession()
      if (window.location.pathname !== '/login') {
        ElMessage.error(message || '登录已失效，请重新登录')
        const current = `${window.location.pathname}${window.location.search}`
        window.location.replace(
          `/login?reason=session-expired&redirect=${encodeURIComponent(current)}`
        )
      }
      error.__handled = true
    }

    return Promise.reject(error)
  }
)

export function getErrorMessage(error, fallback = '操作失败') {
  return (
    error?.response?.data?.message ||
    error?.message ||
    fallback
  )
}
