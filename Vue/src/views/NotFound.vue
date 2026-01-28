<template>
  <div class="not-found-container">
    <div class="not-found-content">
      <h1 class="not-found-code">404</h1>
      <h2 class="not-found-title">页面不存在</h2>
      <p class="not-found-desc">您访问的页面不存在或已被移除</p>
      <el-button type="primary" @click="$router.push('/dashboard')">
        返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
}

.not-found-content {
  text-align: center;
}

.not-found-code {
  font-size: 120px;
  font-weight: 700;
  color: #3b82f6;
  margin: 0;
  line-height: 1;
  background: linear-gradient(135deg, #3b82f6, #1e40af);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.not-found-title {
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  margin: 16px 0;
}

.not-found-desc {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 32px;
}
</style>
