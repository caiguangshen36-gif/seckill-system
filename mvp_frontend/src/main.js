import { createApp } from 'vue'
import App from '@/App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
// 1. 导入插件
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const app = createApp(App)

// 2. 创建 Pinia 实例
const pinia = createPinia()
// 3. 注册持久化插件
pinia.use(piniaPluginPersistedstate)

// 4. 再把 pinia 挂载到 app 上
app.use(pinia)
app.use(router)
app.use(ElementPlus)

app.mount('#app')
