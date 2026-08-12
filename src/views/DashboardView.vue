<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  FolderOpened,
  Delete,
  User,
  OfficeBuilding,
  ArrowRight
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { userApi } from '../api/users'
import { folderApi } from '../api/folders'
import { departmentApi } from '../api/departments'
import { authState } from '../store/auth'
import {
  formatBytes,
  quotaPercent,
  roleLabel,
  areaLabel
} from '../utils/format'
import { getErrorMessage } from '../utils/http'

const router = useRouter()
const loading = ref(true)
const profile = ref(null)
const roots = ref([])
const departments = ref([])

const departmentName = computed(() => {
  if (!profile.value?.departmentId) return '系统级账号'
  const item = departments.value.find(
    (dept) => dept.id === profile.value.departmentId
  )
  return item?.departmentName || `部门 #${profile.value.departmentId}`
})

const quota = computed(() => ({
  used: profile.value?.usedBytes,
  total: profile.value?.quotaBytes,
  percent: quotaPercent(profile.value?.usedBytes, profile.value?.quotaBytes)
}))

const areaStats = computed(() => {
  return roots.value.reduce(
    (acc, item) => {
      acc[item.areaType] = (acc[item.areaType] || 0) + 1
      return acc
    },
    {}
  )
})

onMounted(async () => {
  loading.value = true
  try {
    const [profileResult, rootResult, departmentResult] =
      await Promise.all([
        userApi.get(authState.userId),
        folderApi.list(0),
        departmentApi.list()
      ])

    profile.value = profileResult.data
    roots.value = rootResult.data || []
    departments.value = departmentResult.data || []
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '首页数据加载失败'))
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
        <h1 class="page-title">你好，{{ authState.realName }}</h1>
        <p class="page-subtitle">
          这里是你在部门文件系统中的工作概览。
        </p>
      </div>
      <span class="soft-tag">{{ roleLabel(authState.role) }}</span>
    </div>

    <section class="metric-grid">
      <article class="metric-card">
        <span>所属范围</span>
        <strong>{{ departmentName }}</strong>
        <small>当前账号归属</small>
      </article>
      <article class="metric-card">
        <span>可见根空间</span>
        <strong>{{ roots.length }}</strong>
        <small>由后端权限实时过滤</small>
      </article>
      <article class="metric-card">
        <span>已用个人容量</span>
        <strong>{{ formatBytes(quota.used) }}</strong>
        <small>总配额 {{ formatBytes(quota.total) }}</small>
      </article>
      <article class="metric-card">
        <span>账号状态</span>
        <strong>{{ profile?.status === 'ACTIVE' ? '正常' : profile?.status || '-' }}</strong>
        <small>JWT + Session ID 认证</small>
      </article>
    </section>

    <section class="dashboard-grid">
      <div class="panel quota-panel">
        <div class="panel-body">
          <div class="section-head">
            <div>
              <h3>个人空间配额</h3>
              <p>上传个人文件时由后端进行原子配额校验</p>
            </div>
            <strong>{{ quota.percent }}%</strong>
          </div>
          <el-progress
            :percentage="quota.percent"
            :stroke-width="12"
            :show-text="false"
          />
          <div class="quota-foot">
            <span>{{ formatBytes(quota.used) }} 已使用</span>
            <span>{{ formatBytes(profile?.remainingBytes) }} 可用</span>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-body">
          <div class="section-head">
            <div>
              <h3>快捷入口</h3>
              <p>常用功能快速访问</p>
            </div>
          </div>

          <div class="quick-list">
            <button @click="router.push('/drive')">
              <el-icon><FolderOpened /></el-icon>
              <span>
                <strong>文件空间</strong>
                <small>浏览、上传与下载</small>
              </span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </button>

            <button @click="router.push('/trash')">
              <el-icon><Delete /></el-icon>
              <span>
                <strong>回收站</strong>
                <small>恢复或永久删除文件</small>
              </span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </button>

            <button
              v-if="['ADMIN', 'MINISTER'].includes(authState.role)"
              @click="router.push('/users')"
            >
              <el-icon><User /></el-icon>
              <span>
                <strong>用户管理</strong>
                <small>{{ authState.role === 'ADMIN' ? '管理系统账号' : '查看本部门成员' }}</small>
              </span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </button>

            <button
              v-if="authState.role === 'ADMIN'"
              @click="router.push('/departments')"
            >
              <el-icon><OfficeBuilding /></el-icon>
              <span>
                <strong>部门管理</strong>
                <small>维护部门与共享配额</small>
              </span>
              <el-icon class="arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="panel roots-panel">
      <div class="panel-body">
        <div class="section-head">
          <div>
            <h3>当前可见空间</h3>
            <p>根目录列表直接来自后端 `/api/folders?parentId=0`</p>
          </div>
        </div>

        <div v-if="roots.length" class="root-pills">
          <div v-for="root in roots" :key="root.id" class="root-pill">
            <div class="root-icon">
              <el-icon><FolderOpened /></el-icon>
            </div>
            <div>
              <strong>{{ root.folderName }}</strong>
              <span>{{ areaLabel(root.areaType) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-block">当前没有可见根空间</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 15px;
}

.metric-card {
  min-height: 142px;
  padding: 21px;
  border: 1px solid #e8edf5;
  border-radius: 17px;
  background: white;
  box-shadow: 0 8px 25px rgba(15, 23, 42, 0.035);
}

.metric-card span,
.metric-card small {
  display: block;
  color: #94a3b8;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin: 20px 0 7px;
  overflow: hidden;
  color: #0f172a;
  font-size: 23px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 16px;
  margin-top: 16px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-head h3 {
  margin: 0;
  font-size: 16px;
}

.section-head p {
  margin: 5px 0 0;
  color: #94a3b8;
  font-size: 12px;
}

.section-head > strong {
  color: #2563eb;
  font-size: 22px;
}

.quota-panel {
  min-height: 265px;
}

.quota-panel .panel-body {
  padding: 25px;
}

.quota-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
}

.quick-list {
  display: grid;
  gap: 8px;
}

.quick-list button {
  width: 100%;
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.quick-list button:hover {
  background: #f8fafc;
}

.quick-list button > .el-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: #eff6ff;
  color: #2563eb;
}

.quick-list button span {
  min-width: 0;
  flex: 1;
}

.quick-list strong,
.quick-list small {
  display: block;
}

.quick-list strong {
  font-size: 13px;
}

.quick-list small {
  margin-top: 3px;
  color: #94a3b8;
}

.quick-list .arrow {
  background: transparent;
  color: #94a3b8;
}

.roots-panel {
  margin-top: 16px;
}

.root-pills {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.root-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #edf1f6;
  border-radius: 13px;
}

.root-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: #f1f5f9;
  color: #475569;
}

.root-pill strong,
.root-pill span {
  display: block;
}

.root-pill strong {
  font-size: 13px;
}

.root-pill span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
}

@media (max-width: 1050px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 660px) {
  .metric-grid,
  .root-pills {
    grid-template-columns: 1fr;
  }
}
</style>
