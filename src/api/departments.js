import { http } from '../utils/http'

export const departmentApi = {
  list() {
    return http.get('/departments')
  },
  get(id) {
    return http.get(`/departments/${id}`)
  },
  create(data) {
    return http.post('/departments', data)
  },
  update(id, data) {
    return http.put(`/departments/${id}`, data)
  },
  disable(id) {
    return http.delete(`/departments/${id}`)
  }
}
