<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Plus,
  EditPen,
  Key,
  CircleClose,
  CircleCheck,
  Refresh
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api/users'
import { departmentApi } from '../api/departments'
import { authState } from '../store/auth'
import {
  bytesToGb,
  formatBytes,
  gbToBytes,
  roleLabel,
  statusLabel
} from '../utils/format'
import { getErrorMessage } from '../utils/http'

const loading = ref(false)
const users = ref([])
const departments = ref([])
const total = ref(0)

const page = reactive({
  pageNum: 1,
  pageSize: 10
})

const isAdmin = computed(() => authState.role === 'ADMIN')

const activeDepartments = computed(() =>
  departments.value.filter((item) => item.status === 'ACTIVE')
)

function departmentName(id) {
  if (!id) return '无部门'
  return (
    departments.value.find((item) => item.id === id)?.departmentName ||
    `部门 #${id}`
  )
}

async function load() {
  loading.value = true
  try {
    const [userResult, departmentResult] = await Promise.all([
      userApi.list({
        pageNum: page.pageNum,
        pageSize: page.pageSize
      }),
      departmentApi.list()
    ])

    users.value = userResult.data?.records || []
    total.value = userResult.data?.total || 0
    departments.value = departmentResult.data || []
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '用户列表加载失败'))
    }
  } finally {
    loading.value = false
  }
}

const createDialog = ref(false)
const createForm = reactive({
  username: '',
  password: '',
  realName: '',
  role: 'MEMBER',
  departmentId: null,
  quotaGb: 10
})

function resetCreateForm() {
  Object.assign(createForm, {
    username: '',
    password: '',
    realName: '',
    role: 'MEMBER',
    departmentId: null,
    quotaGb: 10
  })
}

async function createUser() {
  if (
    !createForm.username.trim() ||
    !createForm.password ||
    !createForm.realName.trim()
  ) {
    ElMessage.warning('请完整填写用户名、密码和真实姓名')
    return
  }

  if (
    createForm.role !== 'ADMIN' &&
    !createForm.departmentId
  ) {
    ElMessage.warning('部长和普通成员必须选择部门')
    return
  }

  try {
    await userApi.create({
      username: createForm.username.trim(),
      password: createForm.password,
      realName: createForm.realName.trim(),
      role: createForm.role,
      departmentId:
        createForm.role === 'ADMIN'
          ? null
          : createForm.departmentId,
      quotaBytes: gbToBytes(createForm.quotaGb)
    })
    ElMessage.success('用户创建成功')
    createDialog.value = false
    resetCreateForm()
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '创建用户失败'))
    }
  }
}

const editDialog = ref(false)
const editTarget = ref(null)
const editForm = reactive({
  realName: '',
  role: 'MEMBER',
  departmentId: null,
  quotaGb: 10,
  status: 'ACTIVE'
})

function openEdit(user) {
  editTarget.value = user
  Object.assign(editForm, {
    realName: user.realName,
    role: user.role,
    departmentId: user.departmentId,
    quotaGb: bytesToGb(user.quotaBytes),
    status: user.status
  })
  editDialog.value = true
}

async function updateUser() {
  if (!editTarget.value) return

  if (
    editForm.role !== 'ADMIN' &&
    !editForm.departmentId
  ) {
    ElMessage.warning('部长和普通成员必须选择部门')
    return
  }

  try {
    await userApi.update(editTarget.value.id, {
      realname: editForm.realName.trim(),
      departmentId:
        editForm.role === 'ADMIN'
          ? null
          : editForm.departmentId,
      role: editForm.role,
      status: editForm.status,
      quotaBytes: gbToBytes(editForm.quotaGb)
    })
    ElMessage.success('用户修改成功')
    editDialog.value = false
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '修改用户失败'))
    }
  }
}

async function resetPassword(user) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入“${user.realName}”的新密码（6-30 位）`,
      '重置密码',
      {
        inputType: 'password',
        confirmButtonText: '重置',
        cancelButtonText: '取消',
        inputValidator(value) {
          if (!value || value.length < 6 || value.length > 30) {
            return '密码长度必须为 6 到 30 位'
          }
          return true
        }
      }
    )

    await userApi.resetPassword(user.id, {
      newPassword: value
    })
    ElMessage.success('密码已重置，该用户原登录会话已失效')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '重置密码失败'))
    }
  }
}

async function disableUser(user) {
  try {
    await ElMessageBox.confirm(
      `禁用“${user.realName}”后，其当前登录会话会立即失效。确定继续吗？`,
      '禁用用户',
      {
        type: 'warning',
        confirmButtonText: '禁用',
        cancelButtonText: '取消'
      }
    )
    await userApi.disable(user.id)
    ElMessage.success('用户已禁用')
    await load()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '禁用用户失败'))
    }
  }
}

async function enableUser(user) {
  try {
    await userApi.update(user.id, {
      realname: user.realName,
      departmentId: user.departmentId,
      role: user.role,
      status: 'ACTIVE',
      quotaBytes: user.quotaBytes
    })
    ElMessage.success('用户已启用，需要重新登录')
    await load()
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '启用用户失败'))
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
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">
          {{
            isAdmin
              ? '管理员可以维护账号、角色、部门、个人配额和登录状态。'
              : '部长只能查看当前部门的用户列表，修改操作由后端限制为管理员。'
          }}
        </p>
      </div>

      <div class="header-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button
          v-if="isAdmin"
          type="primary"
          :icon="Plus"
          @click="createDialog = true"
        >
          新建用户
        </el-button>
      </div>
    </div>

    <div class="panel table-panel" v-loading="loading">
      <el-table :data="users">
        <el-table-column min-width="210" label="用户">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="mini-avatar">
                {{ (row.realName || row.username).slice(0, 1) }}
              </div>
              <div>
                <strong>{{ row.realName }}</strong>
                <span>@{{ row.username }} · #{{ row.id }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column width="120" label="角色">
          <template #default="{ row }">
            {{ roleLabel(row.role) }}
          </template>
        </el-table-column>

        <el-table-column min-width="150" label="部门">
          <template #default="{ row }">
            {{ departmentName(row.departmentId) }}
          </template>
        </el-table-column>

        <el-table-column width="145" label="个人配额">
          <template #default="{ row }">
            <div class="quota-cell">
              <strong>{{ formatBytes(row.usedBytes) }}</strong>
              <span>/ {{ formatBytes(row.quotaBytes) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column width="95" label="状态">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'ACTIVE' ? 'success' : 'danger'"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          v-if="isAdmin"
          width="260"
          label="操作"
          fixed="right"
        >
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
              text
              :icon="Key"
              @click="resetPassword(row)"
            >
              密码
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              text
              type="danger"
              :icon="CircleClose"
              @click="disableUser(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              text
              type="success"
              :icon="CircleCheck"
              @click="enableUser(row)"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="prev, pager, next, total"
          :current-page="page.pageNum"
          :page-size="page.pageSize"
          :total="total"
          @current-change="pageChanged"
        />
      </div>
    </div>

    <el-dialog
      v-model="createDialog"
      title="新建用户"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="用户名">
            <el-input v-model="createForm.username" maxlength="50" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="createForm.realName" maxlength="50" />
          </el-form-item>
        </div>

        <el-form-item label="初始密码">
          <el-input
            v-model="createForm.password"
            type="password"
            show-password
            maxlength="30"
            placeholder="6-30 位"
          />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="角色">
            <el-select v-model="createForm.role" style="width: 100%">
              <el-option label="系统管理员" value="ADMIN" />
              <el-option label="部门部长" value="MINISTER" />
              <el-option label="普通成员" value="MEMBER" />
            </el-select>
          </el-form-item>

          <el-form-item label="部门">
            <el-select
              v-model="createForm.departmentId"
              style="width: 100%"
              clearable
              :disabled="createForm.role === 'ADMIN'"
            >
              <el-option
                v-for="item in activeDepartments"
                :key="item.id"
                :label="item.departmentName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="个人配额（GB）">
          <el-input-number
            v-model="createForm.quotaGb"
            :min="0.01"
            :max="10240"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="createUser">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editDialog"
      title="编辑用户"
      width="520px"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="真实姓名">
          <el-input v-model="editForm.realName" maxlength="50" />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="角色">
            <el-select v-model="editForm.role" style="width: 100%">
              <el-option label="系统管理员" value="ADMIN" />
              <el-option label="部门部长" value="MINISTER" />
              <el-option label="普通成员" value="MEMBER" />
            </el-select>
          </el-form-item>

          <el-form-item label="部门">
            <el-select
              v-model="editForm.departmentId"
              style="width: 100%"
              clearable
              :disabled="editForm.role === 'ADMIN'"
            >
              <el-option
                v-for="item in activeDepartments"
                :key="item.id"
                :label="item.departmentName"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="个人配额（GB）">
          <el-input-number
            v-model="editForm.quotaGb"
            :min="0.01"
            :max="10240"
            :precision="2"
            style="width: 100%"
          />
        </el-form-item>

        <el-alert
          title="用户状态不在编辑表单中直接修改：请使用列表中的“禁用 / 启用”，避免绕过你后端的清 Session 逻辑。"
          type="info"
          :closable="false"
        />
      </el-form>

      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="updateUser">保存</el-button>
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mini-avatar {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 800;
}

.user-cell strong,
.user-cell span,
.quota-cell strong,
.quota-cell span {
  display: block;
}

.user-cell strong {
  font-size: 13px;
}

.user-cell span,
.quota-cell span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 10px;
}

.quota-cell strong {
  font-size: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 560px) {
  .form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
