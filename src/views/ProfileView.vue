<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { User, OfficeBuilding, Lock, PieChart } from '@element-plus/icons-vue'
import { userApi } from '../api/users'
import { departmentApi } from '../api/departments'
import { authState } from '../store/auth'
import {
  formatBytes,
  formatDate,
  quotaPercent,
  roleLabel,
  statusLabel
} from '../utils/format'
import { getErrorMessage } from '../utils/http'

const loading = ref(true)
const profile = ref(null)
const departments = ref([])

const departmentName = computed(() => {
  if (!profile.value?.departmentId) return '无部门 / 系统级'
  return (
    departments.value.find((item) => item.id === profile.value.departmentId)
      ?.departmentName || `部门 #${profile.value.departmentId}`
  )
})

const percent = computed(() =>
  quotaPercent(profile.value?.usedBytes, profile.value?.quotaBytes)
)

onMounted(async () => {
  loading.value = true
  try {
    const [userResult, departmentResult] = await Promise.all([
      userApi.get(authState.userId),
      departmentApi.list()
    ])
    profile.value = userResult.data
    departments.value = departmentResult.data || []
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '个人资料加载失败'))
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">
          当前页面只读取后端已有的用户详情接口，不在浏览器保存密码等敏感信息。
        </p>
      </div>
    </div>

    <div v-if="profile" class="profile-grid">
      <section class="panel identity-card">
        <div class="panel-body">
          <div class="profile-avatar">
            {{ (profile.realName || profile.username || 'U').slice(0, 1) }}
          </div>
          <h2>{{ profile.realName }}</h2>
          <p>@{{ profile.username }}</p>
          <el-tag
            :type="profile.status === 'ACTIVE' ? 'success' : 'danger'"
            effect="light"
          >
            {{ statusLabel(profile.status) }}
          </el-tag>

          <div class="identity-divider" />

          <div class="detail-row">
            <el-icon><User /></el-icon>
            <div>
              <span>角色</span>
              <strong>{{ roleLabel(profile.role) }}</strong>
            </div>
          </div>
          <div class="detail-row">
            <el-icon><OfficeBuilding /></el-icon>
            <div>
              <span>部门</span>
              <strong>{{ departmentName }}</strong>
            </div>
          </div>
          <div class="detail-row">
            <el-icon><Lock /></el-icon>
            <div>
              <span>认证方式</span>
              <strong>JWT + Session ID</strong>
            </div>
          </div>
        </div>
      </section>

      <section class="profile-main">
        <div class="panel quota-card">
          <div class="panel-body">
            <div class="card-title">
              <div>
                <h3>个人容量</h3>
                <p>个人空间文件占用</p>
              </div>
              <el-icon><PieChart /></el-icon>
            </div>

            <div class="quota-number">
              {{ formatBytes(profile.usedBytes) }}
              <span>/ {{ formatBytes(profile.quotaBytes) }}</span>
            </div>

            <el-progress
              :percentage="percent"
              :stroke-width="12"
              :show-text="false"
            />

            <div class="quota-stats">
              <div>
                <span>已使用</span>
                <strong>{{ formatBytes(profile.usedBytes) }}</strong>
              </div>
              <div>
                <span>剩余</span>
                <strong>{{ formatBytes(profile.remainingBytes) }}</strong>
              </div>
              <div>
                <span>使用率</span>
                <strong>{{ percent }}%</strong>
              </div>
            </div>
          </div>
        </div>

        <div class="panel info-card">
          <div class="panel-body">
            <h3>账号信息</h3>
            <div class="info-grid">
              <div>
                <span>用户 ID</span>
                <strong>#{{ profile.id }}</strong>
              </div>
              <div>
                <span>用户名</span>
                <strong>{{ profile.username }}</strong>
              </div>
              <div>
                <span>创建时间</span>
                <strong>{{ formatDate(profile.createdAt) }}</strong>
              </div>
              <div>
                <span>更新时间</span>
                <strong>{{ formatDate(profile.updatedAt) }}</strong>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.identity-card {
  text-align: center;
}

.profile-avatar {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  margin: 8px auto 14px;
  border-radius: 24px;
  background: linear-gradient(145deg, #dbeafe, #eff6ff);
  color: #2563eb;
  font-size: 29px;
  font-weight: 800;
}

.identity-card h2 {
  margin: 0;
  font-size: 20px;
}

.identity-card > .panel-body > p {
  margin: 6px 0 13px;
  color: #94a3b8;
  font-size: 12px;
}

.identity-divider {
  height: 1px;
  margin: 22px 0;
  background: #edf1f6;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  text-align: left;
}

.detail-row > .el-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #f8fafc;
  color: #64748b;
}

.detail-row div {
  min-width: 0;
}

.detail-row span,
.detail-row strong {
  display: block;
}

.detail-row span {
  color: #94a3b8;
  font-size: 10px;
}

.detail-row strong {
  margin-top: 3px;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-main {
  display: grid;
  gap: 16px;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title h3,
.info-card h3 {
  margin: 0;
  font-size: 16px;
}

.card-title p {
  margin: 5px 0 0;
  color: #94a3b8;
  font-size: 11px;
}

.card-title > .el-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 20px;
}

.quota-number {
  margin: 33px 0 14px;
  font-size: 28px;
  font-weight: 780;
}

.quota-number span {
  color: #94a3b8;
  font-size: 14px;
  font-weight: 500;
}

.quota-stats,
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 22px;
}

.quota-stats > div,
.info-grid > div {
  padding: 13px;
  border-radius: 11px;
  background: #f8fafc;
}

.quota-stats span,
.quota-stats strong,
.info-grid span,
.info-grid strong {
  display: block;
}

.quota-stats span,
.info-grid span {
  color: #94a3b8;
  font-size: 10px;
}

.quota-stats strong,
.info-grid strong {
  margin-top: 5px;
  font-size: 12px;
}

.info-grid {
  grid-template-columns: repeat(2, 1fr);
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .quota-stats,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
