import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  build: {
    // 单 chunk 超过 1MB 才告警：element-plus 为全量引入的第三方 vendor 包，
    // 独立分包后体积固定且可长期缓存，放宽默认 500kB 阈值属于合理 trade-off
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // vendor 手动分包：框架与 UI 库变更频率远低于业务代码，
        // 独立 chunk 可命中浏览器长效缓存，同时避免业务代码与 vendor 混入同一大包
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus'],
          'element-icons': ['@element-plus/icons-vue'],
        },
      },
    },
  },
})
