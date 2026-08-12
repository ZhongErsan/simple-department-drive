<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataBoard,
  Files,
  Delete,
  User,
  UserFilled,
  OfficeBuilding,
  Fold,
  Expand,
  SwitchButton
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { authApi } from '../api/auth'
import { authState, clearSession } from '../store/auth'
import { roleLabel } from '../utils/format'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const loggingOut = ref(false)

const menuItems = computed(() => {
  const items = [
    { path: '/dashboard', label: '首页', icon: DataBoard },
    { path: '/drive', label: '文件空间', icon: Files },
    { path: '/trash', label: '回收站', icon: Delete },
    { path: '/profile', label: '个人资料', icon: User }
  ]

  if (['ADMIN', 'MINISTER'].includes(authState.role)) {
    items.push({ path: '/users', label: '用户管理', icon: UserFilled })
  }

  if (authState.role === 'ADMIN') {
    items.push({ path: '/departments', label: '部门管理', icon: OfficeBuilding })
  }

  return items
})

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  loggingOut.value = true
  try {
    await authApi.logout()
  } catch {
    // 即使后端会话已失效，也应该清理浏览器本地登录状态。
  } finally {
    clearSession()
    loggingOut.value = false
    router.replace('/login')
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">D</div>
        <div v-if="!collapsed" class="brand-copy">
          <strong>Department Drive</strong>
          <span>部门文件管理</span>
        </div>
      </div>

      <nav class="menu">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ active: route.path === item.path }"
          :title="collapsed ? item.label : undefined"
        >
          <el-icon :size="20">
            <component :is="item.icon" />
          </el-icon>
          <span v-if="!collapsed">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-button" @click="collapsed = !collapsed">
          <el-icon :size="18">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <span v-if="!collapsed">收起导航</span>
        </button>
      </div>
    </aside>

    <section class="main-column">
      <header class="topbar">
        <div>
          <div class="topbar-eyebrow">Department Drive</div>
          <div class="topbar-title">部门文件管理系统</div>
        </div>

        <div class="account">
          <div class="avatar">{{ (authState.realName || 'U').slice(0, 1) }}</div>
          <div class="account-copy">
            <strong>{{ authState.realName || '用户' }}</strong>
            <span>{{ roleLabel(authState.role) }}</span>
          </div>
          <el-button
            text
            :loading="loggingOut"
            class="logout-button"
            @click="logout"
          >
            <el-icon><SwitchButton /></el-icon>
            退出
          </el-button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
}

.sidebar {
  position: sticky;
  top: 0;
  width: 246px;
  height: 100vh;
  flex: 0 0 auto;
  padding: 22px 15px 18px;
  background: #0f172a;
  color: #cbd5e1;
  transition: width 0.2s ease;
  overflow: hidden;
}

.sidebar.collapsed {
  width: 76px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 46px;
  padding: 0 8px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: white;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(37, 99, 235, 0.35);
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.brand-copy strong {
  color: white;
  font-size: 14px;
  white-space: nowrap;
}

.brand-copy span {
  margin-top: 2px;
  color: #64748b;
  font-size: 11px;
  white-space: nowrap;
}

.menu {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 31px;
}

.menu-item,
.collapse-button {
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 0 13px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: 0.16s ease;
}

.menu-item:hover,
.collapse-button:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.06);
}

.menu-item.active {
  color: white;
  background: rgba(59, 130, 246, 0.16);
  box-shadow: inset 3px 0 #3b82f6;
}

.sidebar-footer {
  position: absolute;
  left: 15px;
  right: 15px;
  bottom: 18px;
}

.main-column {
  min-width: 0;
  flex: 1;
}

.topbar {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  border-bottom: 1px solid #e8edf5;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(16px);
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-eyebrow {
  color: #94a3b8;
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.topbar-title {
  margin-top: 2px;
  font-size: 15px;
  font-weight: 750;
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #e0edff;
  color: #1d4ed8;
  font-weight: 800;
}

.account-copy {
  display: flex;
  flex-direction: column;
  min-width: 92px;
}

.account-copy strong {
  font-size: 13px;
}

.account-copy span {
  margin-top: 2px;
  color: #94a3b8;
  font-size: 11px;
}

.logout-button {
  margin-left: 4px;
}

.content {
  padding: 28px 30px 42px;
}

@media (max-width: 860px) {
  .sidebar {
    width: 76px;
  }

  .brand-copy,
  .menu-item span,
  .collapse-button span {
    display: none;
  }

  .content {
    padding: 22px 18px 36px;
  }

  .topbar {
    padding: 0 18px;
  }

  .account-copy {
    display: none;
  }
}

@media (max-width: 540px) {
  .sidebar {
    display: none;
  }

  .topbar-title {
    font-size: 13px;
  }

  .content {
    padding: 18px 12px 30px;
  }
}
</style>
