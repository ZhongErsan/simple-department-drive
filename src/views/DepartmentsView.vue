<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, EditPen, CircleClose, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { departmentApi } from '../api/departments'
import {
  bytesToGb,
  formatBytes,
  formatDate,
  gbToBytes,
  quotaPercent,
  statusLabel
} from '../utils/format'
import { getErrorMessage } from '../utils/http'

const loading = ref(false)
const departments = ref([])

async function load() {
  loading.value = true
  try {
    const result = await departmentApi.list()
    departments.value = result.data || []
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '部门列表加载失败'))
    }
  } finally {
    loading.value = false
  }
}

const dialog = ref(false)
const mode = ref('create')
const target = ref(null)
const form = reactive({
  departmentName: '',
  quotaGb: 100
})

function openCreate() {
  mode.value = 'create'
  target.value = null
  form.departmentName = ''
  form.quotaGb = 100
  dialog.value = true
}

function openEdit(row) {
  mode.value = 'edit'
  target.value = row
  form.departmentName = row.departmentName
  form.quotaGb = bytesToGb(row.quotaBytes)
  dialog.value = true
}

async function save() {
  if (!form.departmentName.trim()) {
    ElMessage.warning('请输入部门名称')
    return
  }

  try {
    const payload = {
      departmentName: form.departmentName.trim(),
      quotaBytes: gbToBytes(form.quotaGb)
    }

    if (mode.value === 'create') {
      await departmentApi.create(payload)
      ElMessage.success('部门创建成功')
    } else {
      await departmentApi.update(target.value.id, payload)
      ElMessage.success('部门修改成功')
    }

    dialog.value = false
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '保存部门失败'))
    }
  }
}

async function disableDepartment(row) {
  try {
    await ElMessageBox.confirm(
      `只有部门下不存在启用用户时才能禁用“${row.departmentName}”。确定继续吗？`,
      '禁用部门',
      {
        type: 'warning',
        confirmButtonText: '禁用',
        cancelButtonText: '取消'
      }
    )
    await departmentApi.disable(row.id)
    ElMessage.success('部门已禁用')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '禁用部门失败'))
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">部门管理</h1>
        <p class="page-subtitle">
          维护部门名称与共享空间配额。禁用部门前，后端会检查是否仍有启用用户。
        </p>
      </div>

      <div class="header-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新建部门
        </el-button>
      </div>
    </div>

    <div class="panel table-panel" v-loading="loading">
      <el-table :data="departments">
        <el-table-column min-width="200" label="部门">
          <template #default="{ row }">
            <div class="dept-name">
              <div>{{ row.departmentName.slice(0, 1) }}</div>
              <span>
                <strong>{{ row.departmentName }}</strong>
                <small>#{{ row.id }}</small>
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column min-width="220" label="共享配额">
          <template #default="{ row }">
            <div class="quota-wrap">
              <div class="quota-copy">
                <span>{{ formatBytes(row.usedBytes) }}</span>
                <span>/ {{ formatBytes(row.quotaBytes) }}</span>
              </div>
              <el-progress
                :percentage="quotaPercent(row.usedBytes, row.quotaBytes)"
                :stroke-width="7"
                :show-text="false"
              />
            </div>
          </template>
        </el-table-column>

        <el-table-column width="100" label="状态">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'ACTIVE' ? 'success' : 'danger'"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column min-width="165" label="更新时间">
          <template #default="{ row }">
            {{ formatDate(row.updatedAt) }}
          </template>
        </el-table-column>

        <el-table-column width="180" label="操作" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              :icon="EditPen"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              text
              type="danger"
              :icon="CircleClose"
              @click="disableDepartment(row)"
            >
              禁用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialog"
      :title="mode === 'create' ? '新建部门' : '编辑部门'"
      width="460px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="部门名称">
          <el-input
            v-model="form.departmentName"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="部门共享配额（GB）">
          <el-input-number
            v-model="form.quotaGb"
            :min="0.01"
            :max="102400"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-alert
          v-if="mode === 'edit'"
          title="后端不允许把部门配额调整到小于当前已用容量。"
          type="info"
          :closable="false"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  gap: 9px;
}

.table-panel {
  padding: 10px 18px 18px;
}

.dept-name {
  display: flex;
  align-items: center;
  gap: 11px;
}

.dept-name > div {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #f0fdf4;
  color: #16a34a;
  font-weight: 800;
}

.dept-name strong,
.dept-name small {
  display: block;
}

.dept-name strong {
  font-size: 13px;
}

.dept-name small {
  margin-top: 3px;
  color: #94a3b8;
}

.quota-wrap {
  max-width: 220px;
}

.quota-copy {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  color: #64748b;
  font-size: 11px;
}
</style>
