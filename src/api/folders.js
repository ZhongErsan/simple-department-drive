import { http } from '../utils/http'

export const folderApi = {
  list(parentId = 0) {
    return http.get('/folders', { params: { parentId } })
  },
  create(data) {
    return http.post('/folders', data)
  },
  rename(id, data) {
    return http.put(`/folders/${id}`, data)
  },
  remove(id) {
    return http.delete(`/folders/${id}`)
  },
  trash() {
    return http.get('/folders/trash')
  },
  restore(id) {
    return http.put(`/folders/${id}/restore`)
  }
}
