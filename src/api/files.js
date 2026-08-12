import { http } from '../utils/http'

export const fileApi = {
  list(params) {
    return http.get('/files', { params })
  },
  upload(folderId, file, onProgress) {
    const formData = new FormData()
    formData.append('file', file)

    return http.post('/files/upload', formData, {
      params: { folderId },
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 120000,
      onUploadProgress(event) {
        if (!event.total || !onProgress) return
        onProgress(Math.round((event.loaded / event.total) * 100))
      }
    })
  },
  download(id) {
    return http.get(`/files/${id}/download`, {
      responseType: 'blob',
      timeout: 120000
    })
  },
  remove(id) {
    return http.delete(`/files/${id}`)
  },
  trash() {
    return http.get('/files/trash')
  },
  restore(id) {
    return http.put(`/files/${id}/restore`)
  },
  permanentDelete(id) {
    return http.delete(`/files/${id}/permanent`)
  }
}
