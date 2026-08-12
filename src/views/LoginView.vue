<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User, FolderOpened } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'
import { setSession } from '../store/auth'
import { getErrorMessage } from '../utils/http'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const formRef = ref()

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

async function submit() {
  await formRef.value?.validate()
  loading.value = true

  try {
    const result = await authApi.login({
      username: form.username.trim(),
      password: form.password
    })

    setSession(result.data)
    ElMessage.success(result.message || '登录成功')

    const redirect =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : '/dashboard'

    router.replace(redirect)
  } catch (error) {
    if (!error.__handled) {
      ElMessage.error(getErrorMessage(error, '登录失败'))
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="visual">
      <div class="visual-inner">
        <div class="visual-badge">
          <el-icon><FolderOpened /></el-icon>
          Department Drive
        </div>
        <h1>让团队文件<br />各归其位。</h1>
        <p>
          个人空间、部门公共区与投稿区统一管理，
          权限、配额和文件生命周期都由你的 Spring Boot 后端控制。
        </p>

        <div class="feature-grid">
          <div>
            <strong>01</strong>
            <span>角色权限</span>
          </div>
          <div>
            <strong>02</strong>
            <span>文件空间</span>
          </div>
          <div>
            <strong>03</strong>
            <span>回收恢复</span>
          </div>
        </div>
      </div>
    </section>

    <section class="form-side">
      <div class="login-card">
        <div class="mobile-brand">Department Drive</div>
        <h2>欢迎回来</h2>
        <p class="hint">使用你的部门文件系统账号登录</p>

        <el-alert
          v-if="route.query.reason === 'session-expired'"
          title="登录状态已失效，请重新登录"
          type="warning"
          :closable="false"
          show-icon
          class="session-alert"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入用户名"
              autocomplete="username"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="submit"
          >
            登录系统
          </el-button>
        </el-form>

        <div class="security-note">
          登录凭证由后端 JWT 校验；账号在其他设备重新登录后，
          当前页面会在下一次请求时自动退出。
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1.05fr) minmax(460px, 0.95fr);
  background: white;
}

.visual {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: 70px;
  background:
    radial-gradient(circle at 85% 20%, rgba(59, 130, 246, 0.3), transparent 27%),
    radial-gradient(circle at 10% 85%, rgba(14, 165, 233, 0.18), transparent 30%),
    #0f172a;
  color: white;
}

.visual::after {
  content: "";
  position: absolute;
  width: 420px;
  height: 420px;
  right: -185px;
  bottom: -190px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  box-shadow:
    0 0 0 70px rgba(255, 255, 255, 0.025),
    0 0 0 140px rgba(255, 255, 255, 0.018);
}

.visual-inner {
  position: relative;
  z-index: 1;
  max-width: 590px;
}

.visual-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  color: #bfdbfe;
  background: rgba(255, 255, 255, 0.055);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

h1 {
  margin: 30px 0 18px;
  font-size: clamp(44px, 5vw, 72px);
  line-height: 1.05;
  letter-spacing: -0.045em;
}

.visual p {
  max-width: 520px;
  margin: 0;
  color: #94a3b8;
  font-size: 16px;
  line-height: 1.85;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-top: 56px;
}

.feature-grid > div {
  padding: 17px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
}

.feature-grid strong,
.feature-grid span {
  display: block;
}

.feature-grid strong {
  color: #60a5fa;
  font-size: 12px;
}

.feature-grid span {
  margin-top: 9px;
  font-size: 13px;
  color: #e2e8f0;
}

.form-side {
  display: grid;
  place-items: center;
  padding: 48px;
}

.login-card {
  width: min(420px, 100%);
}

.mobile-brand {
  display: none;
  margin-bottom: 24px;
  color: #2563eb;
  font-weight: 800;
}

h2 {
  margin: 0;
  font-size: 31px;
  letter-spacing: -0.03em;
}

.hint {
  margin: 9px 0 28px;
  color: #94a3b8;
  font-size: 14px;
}

.session-alert {
  margin-bottom: 20px;
}

.submit {
  width: 100%;
  margin-top: 6px;
}

.security-note {
  margin-top: 25px;
  padding: 14px 15px;
  border-radius: 12px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  line-height: 1.65;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .visual {
    display: none;
  }

  .form-side {
    min-height: 100vh;
    padding: 28px;
  }

  .mobile-brand {
    display: block;
  }
}
</style>
