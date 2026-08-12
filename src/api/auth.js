import { http } from '../utils/http'

export const authApi = {
  login(data) {
    return http.post('/auth/login', data)
  },
  logout() {
    return http.post('/auth/logout')
  }
}
