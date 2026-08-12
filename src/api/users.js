import { http } from '../utils/http'

export const userApi = {
  list(params) {
    return http.get('/users', { params })
  },
  get(id) {
    return http.get(`/users/${id}`)
  },
  create(data) {
    return http.post('/users', data)
  },
  update(id, data) {
    return http.put(`/users/${id}`, data)
  },
  resetPassword(id, data) {
    return http.put(`/users/${id}/password`, data)
  },
  disable(id) {
    return http.delete(`/users/${id}`)
  }
}
