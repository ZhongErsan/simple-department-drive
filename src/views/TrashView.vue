<script setup>
import { onMounted, ref } from 'vue'
import {
  RefreshLeft,
  DeleteFilled,
  Folder,
  Document,
  Refresh
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fileApi } from '../api/files'
import { folderApi } from '../api/folders'
import { formatBytes, formatDate, areaLabel } from '../utils/format'
import { getErrorMessage } from '../utils/http'

const loading = ref(false)
const activeTab = ref('files')
const deletedFiles = ref([])
const deletedFolders = ref([])

async function load() {
  loading.value = true
  try {
    const [filesResult, foldersResult] = await Promise.all([
      fileApi.trash(),
      folderApi.trash()
    ])
    deletedFiles.value = filesResult.data || []
    deletedFolders.value = foldersResult.data || []
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '回收站加载失败'))
    }
  } finally {
    loading.value = false
  }
}

async function restoreFile(file) {
  try {
    await fileApi.restore(file.id)
    ElMessage.success('文件恢复成功')
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '文件恢复失败'))
    }
  }
}

async function permanentDelete(file) {
  try {
    await ElMessageBox.confirm(
      `永久删除“${file.fileName}”后无法恢复，确定继续吗？`,
      '永久删除文件',
      {
        type: 'error',
        confirmButtonText: '永久删除',
        cancelButtonText: '取消'
      }
    )
    await fileApi.permanentDelete(file.id)
    ElMessage.success('文件已永久删除')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '永久删除失败'))
    }
  }
}

async function restoreFolder(folder) {
  try {
    await folderApi.restore(folder.id)
    ElMessage.success('文件夹恢复成功')
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '文件夹恢复失败'))
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">回收站</h1>
        <p class="page-subtitle">
          文件可以恢复或永久删除；当前后端仅提供文件夹恢复，没有文件夹永久删除接口。
        </p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="panel trash-panel" v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane
          :label="`文件 (${deletedFiles.length})`"
          name="files"
        >
          <el-table :data="deletedFiles">
            <el-table-column min-width="260" label="文件">
              <template #default="{ row }">
                <div class="item-name">
                  <div class="item-icon file">
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
            <el-table-column min-width="120" prop="uploaderName" label="上传人" />
            <el-table-column min-width="165" label="删除时间">
              <template #default="{ row }">
                {{ formatDate(row.deletedAt) }}
              </template>
            </el-table-column>
            <el-table-column width="190" label="操作" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  :icon="RefreshLeft"
                  @click="restoreFile(row)"
                >
                  恢复
                </el-button>
                <el-button
                  text
                  type="danger"
                  :icon="DeleteFilled"
                  @click="permanentDelete(row)"
                >
                  永久删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane
          :label="`文件夹 (${deletedFolders.length})`"
          name="folders"
        >
          <el-table :data="deletedFolders">
            <el-table-column min-width="260" label="文件夹">
              <template #default="{ row }">
                <div class="item-name">
                  <div class="item-icon folder">
                    <el-icon><Folder /></el-icon>
                  </div>
                  <div>
                    <strong>{{ row.folderName }}</strong>
                    <span>{{ areaLabel(row.areaType) }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column width="130" label="父目录 ID">
              <template #default="{ row }">
                #{{ row.parentId }}
              </template>
            </el-table-column>
            <el-table-column min-width="165" label="删除时间">
              <template #default="{ row }">
                {{ formatDate(row.deletedAt) }}
              </template>
            </el-table-column>
            <el-table-column width="120" label="操作" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  :icon="RefreshLeft"
                  @click="restoreFolder(row)"
                >
                  恢复
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.trash-panel {
  padding: 5px 20px 20px;
}

.item-name {
  display: flex;
  align-items: center;
  gap: 11px;
}

.item-icon {
  width: 35px;
  height: 35px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 9px;
}

.item-icon.file {
  color: #2563eb;
  background: #eff6ff;
}

.item-icon.folder {
  color: #d97706;
  background: #fff7ed;
}

.item-name strong,
.item-name span {
  display: block;
}

.item-name strong {
  font-size: 13px;
}

.item-name span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 10px;
}
</style>
