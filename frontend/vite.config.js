import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端服务地址（机器重启后 Windows 保留端口范围变化，8081 已可绑定，后端恢复默认 8081）
const BACKEND = 'http://localhost:8081'

export default defineConfig({
  // Capacitor 打包 APP 时使用相对路径，确保 WebView 本地资源正常加载
  base: './',
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': {
        target: BACKEND,
        changeOrigin: true
      },
      '/uploads': {
        target: BACKEND,
        changeOrigin: true
      }
    }
  },
  build: {
    chunkSizeWarningLimit: 2000
  }
})
