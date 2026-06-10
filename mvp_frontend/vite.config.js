import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  //配置代理
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true, //是否改变域名
        rewrite: (path) => {
          return path.replace(/^\/api/, '') //重写路径，去掉路径中的/api前缀
        },
      },
    },
  },
})
