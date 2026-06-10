<template>
  <div class="layout-page">
    <!-- 顶部导航栏 -->
    <el-header class="header">
      <div class="nav-box">
        <!-- 左侧 Logo -->
        <div class="logo-area" @click="router.push('/home')">
          <span class="logo-icon">🛍</span>
          <span class="logo-text">高并发秒杀系统</span>
        </div>

        <!-- 中间导航菜单 -->
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          class="nav-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/goods">
            <el-icon><Document /></el-icon>
            <template #title>秒杀列表中心</template>
          </el-menu-item>
          <el-menu-item index="/orders">
            <el-icon><Document /></el-icon>
            <template #title>订单中心</template>
          </el-menu-item>
          <el-menu-item index="/goods-management">
            <el-icon><Box /></el-icon>
            <template #title>商品管理</template>
          </el-menu-item>
        </el-menu>

        <!-- 右侧用户信息与退出 -->
        <div class="user-area">
          <template v-if="isLogin">
            <span class="username">{{ username }}</span>
            <el-button type="danger" link @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              退出
            </el-button>
          </template>
          <template v-else>
            <el-button type="primary" link @click="router.push('/login')">登录</el-button>
            <el-divider direction="vertical" />
            <el-button type="primary" link @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </el-header>

    <!-- 页面主体内容 -->
    <transition mode="out-in" name="page-fade">
      <el-main class="main-content" :key="$route.fullPath">
        <router-view />
      </el-main>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { House, User, SwitchButton, Document, Box } from '@element-plus/icons-vue'
import { useTokenStore } from '@/stores/token.js'
import { getUserInfoService } from '@/api/user.js'

const router = useRouter()
const route = useRoute()
const tokenStore = useTokenStore()
const isLogin = ref(false)
const username = ref('')
const userAvatar = ref('')

// 导航激活状态绑定当前路由
const activeMenu = computed(() => route.path)
const handleMenuSelect = (index) => router.push(index)

// 获取用户信息
const getUserInfo = async () => {
  try {
    const res = await getUserInfoService()
    username.value = res.data.username
    userAvatar.value = res.data.avatar || ''
    isLogin.value = true
  } catch {
    tokenStore.removeToken()
    isLogin.value = false
  }
}

// 退出登录
const handleLogout = () => {
  tokenStore.removeToken()
  isLogin.value = false
  username.value = ''
  userAvatar.value = ''
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(() => {
  if (tokenStore.token) {
    getUserInfo()
  }
})
</script>

<style scoped>
.layout-page {
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* ========== 顶部导航栏 ========== */
.header {
  height: 60px;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-box {
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Logo */
.logo-area {
  display: flex;
  align-items: center;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
}
.logo-icon {
  font-size: 28px;
  margin-right: 8px;
}
.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  white-space: nowrap;
}

/* 导航菜单 */
.nav-menu {
  border-bottom: none !important;
  flex: 1;
  justify-content: center;
}
.nav-menu .el-menu-item {
  font-size: 15px;
  height: 60px;
  line-height: 60px;
}

/* 右侧用户区域 */
.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.username {
  font-size: 14px;
  color: #606266;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 主内容区 ========== */
.main-content {
  max-width: 1400px;
  margin: 20px auto;
  padding: 0 24px;
}

/* ========== 路由切换动画 ========== */
.page-fade-enter-active,
.page-fade-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
