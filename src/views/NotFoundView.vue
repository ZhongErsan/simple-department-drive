<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div>
      <span>404</span>
      <h1>页面不存在</h1>
      <p>你访问的页面不存在，或者当前账号没有对应入口。</p>
      <el-button type="primary" @click="router.push('/dashboard')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 30px;
  background: #f8fafc;
  text-align: center;
}

.not-found span {
  color: #bfdbfe;
  font-size: 96px;
  line-height: 1;
  font-weight: 850;
}

.not-found h1 {
  margin: 12px 0 8px;
}

.not-found p {
  margin: 0 0 22px;
  color: #94a3b8;
}
</style>
