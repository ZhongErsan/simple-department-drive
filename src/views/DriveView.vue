<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Folder,
  FolderAdd,
  UploadFilled,
  Download,
  Delete,
  EditPen,
  ArrowRight,
  Refresh,
  Document
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { folderApi } from '../api/folders'
import { fileApi } from '../api/files'
import { authState } from '../store/auth'
import { areaLabel, formatBytes, formatDate } from '../utils/format'
import { getErrorMessage } from '../utils/http'

const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const folders = ref([])
const files = ref([])
const total = ref(0)

const page = reactive({
  pageNum: 1,
  pageSize: 20
})

const breadcrumbs = ref([
  {
    id: 0,
    folderName: '全部空间',
    areaType: null,
    ownerId: null,
    departmentId: null
  }
])

const currentFolderId = computed(
  () => breadcrumbs.value[breadcrumbs.value.length - 1].id
)

const currentFolder = computed(() => {
  const item = breadcrumbs.value[breadcrumbs.value.length - 1]
  return item.id === 0 ? null : item
})

const isRoot = computed(() => currentFolderId.value === 0)

const canCreateHere = computed(() => {
  const folder = currentFolder.value
  if (!folder) return false
  if (authState.role === 'ADMIN') return true
  if (
    folder.areaType === 'PERSONAL' &&
    Number(folder.ownerId) === Number(authState.userId)
  ) {
    return true
  }
  return (
    authState.role === 'MINISTER' &&
    ['PUBLIC', 'CONTRIBUTION'].includes(folder.areaType)
  )
})

const canUploadHere = computed(() => {
  const folder = currentFolder.value
  if (!folder) return false
  if (authState.role === 'ADMIN') return true
  if (
    folder.areaType === 'PERSONAL' &&
    Number(folder.ownerId) === Number(authState.userId)
  ) {
    return true
  }
  if (folder.areaType === 'CONTRIBUTION') return true
  return authState.role === 'MINISTER' && folder.areaType === 'PUBLIC'
})

const createDialog = ref(false)
const createForm = reactive({
  folderName: ''
})

const renameDialog = ref(false)
const renameTarget = ref(null)
const renameForm = reactive({
  folderName: ''
})

function canManageFolder(folder) {
  if (Number(folder.parentId) === 0) return false
  if (authState.role === 'ADMIN') return true
  if (
    folder.areaType === 'PERSONAL' &&
    Number(folder.ownerId) === Number(authState.userId)
  ) {
    return true
  }
  return (
    authState.role === 'MINISTER' &&
    ['PUBLIC', 'CONTRIBUTION'].includes(folder.areaType)
  )
}

function canDeleteFile(file) {
  const folder = currentFolder.value
  if (!folder) return false
  if (authState.role === 'ADMIN') return true
  if (
    folder.areaType === 'PERSONAL' &&
    Number(folder.ownerId) === Number(authState.userId)
  ) {
    return true
  }
  if (
    authState.role === 'MINISTER' &&
    ['PUBLIC', 'CONTRIBUTION'].includes(folder.areaType)
  ) {
    return true
  }
  return (
    authState.role === 'MEMBER' &&
    folder.areaType === 'CONTRIBUTION' &&
    Number(file.uploaderId) === Number(authState.userId)
  )
}

async function load() {
  loading.value = true
  try {
    const folderResult = await folderApi.list(currentFolderId.value)
    folders.value = folderResult.data || []

    if (isRoot.value) {
      files.value = []
      total.value = 0
    } else {
      const fileResult = await fileApi.list({
        folderId: currentFolderId.value,
        pageNum: page.pageNum,
        pageSize: page.pageSize
      })
      files.value = fileResult.data?.records || []
      total.value = fileResult.data?.total || 0
    }
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '文件列表加载失败'))
    }
  } finally {
    loading.value = false
  }
}

function enterFolder(folder) {
  breadcrumbs.value.push({
    ...folder
  })
  page.pageNum = 1
  load()
}

function goBreadcrumb(index) {
  breadcrumbs.value = breadcrumbs.value.slice(0, index + 1)
  page.pageNum = 1
  load()
}

async function createFolder() {
  const name = createForm.folderName.trim()
  if (!name) {
    ElMessage.warning('请输入文件夹名称')
    return
  }

  try {
    await folderApi.create({
      parentId: currentFolderId.value,
      folderName: name
    })
    ElMessage.success('文件夹创建成功')
    createDialog.value = false
    createForm.folderName = ''
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '创建失败'))
    }
  }
}

function openRename(folder) {
  renameTarget.value = folder
  renameForm.folderName = folder.folderName
  renameDialog.value = true
}

async function renameFolder() {
  const name = renameForm.folderName.trim()
  if (!name || !renameTarget.value) return

  try {
    await folderApi.rename(renameTarget.value.id, {
      folderName: name
    })
    ElMessage.success('文件夹重命名成功')
    renameDialog.value = false
    renameTarget.value = null
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '重命名失败'))
    }
  }
}

async function deleteFolder(folder) {
  try {
    await ElMessageBox.confirm(
      `确定删除空文件夹“${folder.folderName}”吗？删除后可在回收站恢复。`,
      '删除文件夹',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await folderApi.remove(folder.id)
    ElMessage.success('文件夹已移入回收站')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '删除失败'))
    }
  }
}

async function uploadRequest(options) {
  const file = options.file

  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件不能超过 50MB')
    options.onError?.(new Error('file too large'))
    return
  }

  uploading.value = true
  uploadPercent.value = 0

  try {
    const result = await fileApi.upload(
      currentFolderId.value,
      file,
      (percent) => {
        uploadPercent.value = percent
        options.onProgress?.({ percent })
      }
    )
    ElMessage.success(result.message || '文件上传成功')
    options.onSuccess?.(result)
    await load()
  } catch (error) {
    options.onError?.(error)
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '上传失败'))
    }
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

function parseDownloadName(header, fallback) {
  if (!header) return fallback

  const utf8 = header.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8?.[1]) {
    try {
      return decodeURIComponent(utf8[1])
    } catch {
      return fallback
    }
  }

  const quoted = header.match(/filename="?([^"]+)"?/i)
  return quoted?.[1] || fallback
}

async function downloadFile(file) {
  try {
    const response = await fileApi.download(file.id)
    const blob = new Blob([response.data], {
      type: response.headers['content-type'] || 'application/octet-stream'
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = parseDownloadName(
      response.headers['content-disposition'],
      file.fileName
    )
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '下载失败'))
    }
  }
}

async function deleteFile(file) {
  try {
    await ElMessageBox.confirm(
      `确定删除文件“${file.fileName}”吗？`,
      '删除文件',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
    await fileApi.remove(file.id)
    ElMessage.success('文件已移入回收站')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '删除失败'))
    }
  }
}

function pageChanged(value) {
  page.pageNum = value
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">文件空间</h1>
        <p class="page-subtitle">
          根空间、个人空间、公共区和投稿区均由后端权限规则实时过滤。
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="panel drive-panel" v-loading="loading">
      <div class="drive-toolbar">
        <div class="breadcrumb">
          <button
            v-for="(item, index) in breadcrumbs"
            :key="`${item.id}-${index}`"
            @click="goBreadcrumb(index)"
          >
            <span>{{ item.folderName }}</span>
            <el-icon v-if="index < breadcrumbs.length - 1">
              <ArrowRight />
            </el-icon>
          </button>
        </div>

        <div class="toolbar-actions">
          <el-button
            v-if="canCreateHere"
            :icon="FolderAdd"
            @click="createDialog = true"
          >
            新建文件夹
          </el-button>

          <el-upload
            v-if="canUploadHere"
            :show-file-list="false"
            :http-request="uploadRequest"
          >
            <el-button type="primary" :loading="uploading" :icon="UploadFilled">
              {{ uploading ? `上传中 ${uploadPercent}%` : '上传文件' }}
            </el-button>
          </el-upload>
        </div>
      </div>

      <div v-if="isRoot" class="root-content">
        <div class="section-caption">
          <strong>可访问空间</strong>
          <span>点击卡片进入</span>
        </div>

        <div v-if="folders.length" class="folder-grid root-grid">
          <article
            v-for="folder in folders"
            :key="folder.id"
            class="folder-card root-card"
            @click="enterFolder(folder)"
          >
            <div class="folder-icon">
              <el-icon><Folder /></el-icon>
            </div>
            <div class="folder-copy">
              <strong>{{ folder.folderName }}</strong>
              <span>{{ areaLabel(folder.areaType) }}</span>
            </div>
            <div class="folder-meta">
              <span v-if="folder.departmentId">部门 #{{ folder.departmentId }}</span>
              <span v-if="folder.ownerId">所有者 #{{ folder.ownerId }}</span>
            </div>
          </article>
        </div>
        <div v-else class="empty-block">当前账号没有可访问的根空间</div>
      </div>

      <template v-else>
        <div class="current-context">
          <div>
            <span>当前位置</span>
            <strong>{{ currentFolder?.folderName }}</strong>
          </div>
          <div class="context-tags">
            <el-tag effect="plain">
              {{ areaLabel(currentFolder?.areaType) }}
            </el-tag>
            <el-tag v-if="currentFolder?.departmentId" effect="plain" type="info">
              部门 #{{ currentFolder.departmentId }}
            </el-tag>
          </div>
        </div>

        <section class="folder-section">
          <div class="section-caption">
            <strong>文件夹</strong>
            <span>{{ folders.length }} 个</span>
          </div>

          <div v-if="folders.length" class="folder-grid">
            <article
              v-for="folder in folders"
              :key="folder.id"
              class="folder-card"
            >
              <button class="folder-main" @click="enterFolder(folder)">
                <div class="folder-icon">
                  <el-icon><Folder /></el-icon>
                </div>
                <div class="folder-copy">
                  <strong>{{ folder.folderName }}</strong>
                  <span>{{ formatDate(folder.createdAt) }}</span>
                </div>
              </button>

              <div v-if="canManageFolder(folder)" class="folder-actions">
                <el-button
                  text
                  circle
                  :icon="EditPen"
                  @click="openRename(folder)"
                />
                <el-button
                  text
                  circle
                  type="danger"
                  :icon="Delete"
                  @click="deleteFolder(folder)"
                />
              </div>
            </article>
          </div>
          <div v-else class="mini-empty">暂无子文件夹</div>
        </section>

        <section class="file-section">
          <div class="section-caption">
            <strong>文件</strong>
            <span>{{ total }} 个</span>
          </div>

          <el-table :data="files" style="width: 100%">
            <el-table-column min-width="260" label="文件名">
              <template #default="{ row }">
                <div class="file-name">
                  <div class="file-icon">
                    <el-icon><Document /></el-icon>
                  </div>
                  <div>
                    <strong>{{ row.fileName }}</strong>
                    <span>{{ row.contentType || '未知类型' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column width="110" label="大小">
              <template #default="{ row }">
                {{ formatBytes(row.fileSize) }}
              </template>
            </el-table-column>

            <el-table-column min-width="120" label="上传人">
              <template #default="{ row }">
                {{ row.uploaderName }}
              </template>
            </el-table-column>

            <el-table-column min-width="160" label="上传时间">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>

            <el-table-column width="150" label="操作" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  :icon="Download"
                  @click="downloadFile(row)"
                >
                  下载
                </el-button>
                <el-button
                  v-if="canDeleteFile(row)"
                  text
                  type="danger"
                  :icon="Delete"
                  @click="deleteFile(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="total > page.pageSize" class="pagination">
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="page.pageNum"
              :page-size="page.pageSize"
              :total="total"
              @current-change="pageChanged"
            />
          </div>
        </section>
      </template>
    </div>

    <el-dialog
      v-model="createDialog"
      title="新建文件夹"
      width="420px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="文件夹名称">
          <el-input
            v-model="createForm.folderName"
            maxlength="100"
            show-word-limit
            placeholder="请输入文件夹名称"
            @keyup.enter="createFolder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="createFolder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="renameDialog"
      title="重命名文件夹"
      width="420px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="新的文件夹名称">
          <el-input
            v-model="renameForm.folderName"
            maxlength="100"
            show-word-limit
            @keyup.enter="renameFolder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" @click="renameFolder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.drive-panel {
  overflow: hidden;
}

.drive-toolbar {
  min-height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 18px;
  border-bottom: 1px solid #edf1f6;
}

.breadcrumb {
  min-width: 0;
  display: flex;
  align-items: center;
  overflow-x: auto;
}

.breadcrumb button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  padding: 5px 4px;
  border: 0;
  background: none;
  color: #64748b;
  cursor: pointer;
}

.breadcrumb button:last-child span {
  color: #0f172a;
  font-weight: 700;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 9px;
  flex: 0 0 auto;
}

.root-content,
.folder-section,
.file-section {
  padding: 20px;
}

.current-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  background: #fafcff;
  border-bottom: 1px solid #edf1f6;
}

.current-context span,
.current-context strong {
  display: block;
}

.current-context > div:first-child > span {
  color: #94a3b8;
  font-size: 11px;
}

.current-context > div:first-child > strong {
  margin-top: 4px;
  font-size: 16px;
}

.context-tags {
  display: flex;
  gap: 7px;
}

.folder-section {
  border-bottom: 1px solid #edf1f6;
}

.section-caption {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 13px;
}

.section-caption strong {
  font-size: 14px;
}

.section-caption span {
  color: #94a3b8;
  font-size: 12px;
}

.folder-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 11px;
}

.folder-card {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 72px;
  padding: 8px 8px 8px 12px;
  border: 1px solid #edf1f6;
  border-radius: 13px;
  background: white;
  transition: 0.16s ease;
}

.folder-card:hover {
  border-color: #dbeafe;
  box-shadow: 0 8px 20px rgba(30, 64, 175, 0.06);
}

.root-card {
  position: relative;
  min-height: 116px;
  display: grid;
  grid-template-columns: 46px 1fr;
  align-content: center;
  padding: 18px;
  cursor: pointer;
}

.root-card .folder-meta {
  grid-column: 1 / -1;
  display: flex;
  gap: 10px;
  margin-top: 9px;
  color: #94a3b8;
  font-size: 10px;
}

.folder-main {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
  cursor: pointer;
}

.folder-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #fff7ed;
  color: #f59e0b;
  font-size: 20px;
}

.folder-copy {
  min-width: 0;
}

.folder-copy strong,
.folder-copy span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-copy strong {
  font-size: 13px;
}

.folder-copy span {
  margin-top: 5px;
  color: #94a3b8;
  font-size: 10px;
}

.folder-actions {
  display: flex;
  flex: 0 0 auto;
}

.file-name {
  display: flex;
  align-items: center;
  gap: 11px;
}

.file-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #eff6ff;
  color: #2563eb;
}

.file-name strong,
.file-name span {
  display: block;
}

.file-name strong {
  max-width: 350px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-name span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 10px;
}

.mini-empty {
  padding: 22px;
  color: #94a3b8;
  text-align: center;
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

@media (max-width: 1180px) {
  .folder-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .drive-toolbar,
  .current-context {
    align-items: flex-start;
    flex-direction: column;
  }

  .folder-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 580px) {
  .folder-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    width: 100%;
  }
}
</style>
