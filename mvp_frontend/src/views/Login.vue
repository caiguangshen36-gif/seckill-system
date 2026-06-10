<template>
  <!-- 左右分栏布局 -->
  <div class="login-container">
    <!-- 左侧登录/注册表单区域 -->
    <div class="login-form-wrapper">
      <div class="login-form-card">
        <!-- 登录表单 -->
        <el-form
          ref="loginFormRef"
          size="large"
          autocomplete="off"
          v-if="isLogin"
          :model="loginData"
          :rules="rules"
          class="login-form"
        >
          <el-form-item>
            <h1 class="form-title">登录</h1>
          </el-form-item>

          <el-form-item prop="username">
            <el-input
              :prefix-icon="User"
              placeholder="请输入用户名"
              v-model="loginData.username"
            ></el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              name="password"
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              v-model="loginData.password"
              show-password
            ></el-input>
          </el-form-item>

          <el-form-item>
            <el-button class="submit-btn" type="primary" block @click="handleLogin">
              登录
            </el-button>
          </el-form-item>

          <el-form-item class="switch-link">
            <el-link type="info" underline="false" @click="switchToRegister"> 注册 → </el-link>
          </el-form-item>
        </el-form>

        <!-- 注册表单 -->
        <el-form
          ref="registerFormRef"
          size="large"
          autocomplete="off"
          v-else
          :model="registerData"
          :rules="rules"
          class="login-form"
        >
          <el-form-item>
            <h1 class="form-title">注册</h1>
          </el-form-item>

          <el-form-item prop="username">
            <el-input
              :prefix-icon="User"
              placeholder="请输入用户名"
              v-model="registerData.username"
            ></el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              v-model="registerData.password"
              show-password
            ></el-input>
          </el-form-item>

          <el-form-item prop="rePassword">
            <el-input
              :prefix-icon="Lock"
              type="password"
              placeholder="请再次输入密码"
              v-model="registerData.rePassword"
              show-password
            ></el-input>
          </el-form-item>

          <el-form-item>
            <el-button class="submit-btn" type="primary" block @click="handleRegister">
              注册
            </el-button>
          </el-form-item>

          <el-form-item class="switch-link">
            <el-link type="info" underline="false" @click="switchToLogin"> ← 返回登录 </el-link>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 右侧宣传区域 -->
    <div class="login-banner">
      <div class="banner-content">
        <h1 class="banner-title">高并发系统</h1>
        <!-- <div class="banner-illustration">前台用户使用中心</div> -->
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useTokenStore } from '@/stores/token.js'
import { userLoginService, userRegisterService } from '@/api/user.js'
import { ElMessage } from 'element-plus'
import { useUserInfoStore } from '@/stores/user.js'
const userInfoStore = useUserInfoStore()
// 调用useTokenStore得到状态
const tokenStore = useTokenStore()
const router = useRouter()

// 状态控制：true显示登录，false显示注册
const isLogin = ref(true)

// 表单 Refs
const loginFormRef = ref()
const registerFormRef = ref()

// 表单数据模型
const loginData = ref({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
})

const registerData = ref({
  username: '',
  password: '',
  rePassword: '',
  phone: '',
  captchaId: '',
  captchaCode: '',
})

// 表单校验规则
const rules = reactive({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 5, max: 16, message: '用户名长度必须在 5-16 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度必须在 5-20 位', trigger: 'blur' },
  ],
  rePassword: [
    {
      validator: (rule, value, callback) => {
        if (!value) {
          callback(new Error('请再次输入密码'))
        } else if (value !== registerData.value.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码为4位', trigger: 'blur' },
  ],
})

// 切换到注册
const switchToRegister = () => {
  isLogin.value = false
  clearFormData()
  refreshCaptcha()
}

// 切换到登录
const switchToLogin = () => {
  isLogin.value = true
  clearFormData()
  refreshCaptcha()
}

// 清空表单
const clearFormData = () => {
  loginData.value = { username: '', password: '', captchaId: '', captchaCode: '' }
  registerData.value = {
    username: '',
    password: '',
    rePassword: '',
  }
}

// 登录逻辑
const handleLogin = async () => {
  if (!loginFormRef.value) return

  loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      const res = await userLoginService(loginData.value)
      ElMessage.success('登录成功')
      tokenStore.setToken(res.data)

      userInfoStore.setInfo(res.data.userInfo) // 登录成功后把用户信息存到 pinia
      console.log('登录成功，用户信息:', res)
      router.push('/layout')
    } catch (err) {
      ElMessage.error(err.msg || '登录失败')
    }
  })
}

// 注册逻辑
const handleRegister = async () => {
  if (!registerFormRef.value) return

  registerFormRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      const res = await userRegisterService(registerData.value)
      ElMessage.success('注册成功，请登录')
      switchToLogin() // 注册成功自动切到登录页
    } catch (err) {
      ElMessage.error(err.msg || '注册失败')
    }
  })
}
onMounted(() => {})
</script>

<style scoped>
.submit-.btn {
  margin-top: 20px;
}

.login-container {
  display: flex;
  height: 100vh;
}
.login-form-wrapper {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #fff;
}
.login-form-card {
  width: 360px;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.form-title {
  text-align: center;
  margin-bottom: 30px;
}
.submit-btn {
  display: flex;
  justify-content: center;
  width: 100%;
  max-width: 360px;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}
.switch-link {
  text-align: center;
}
.login-banner {
  flex: 1;
  background-color: #409eff;
  display: flex;
  justify-content: center;
  align-items: center;
  color: white;
}
.banner-content {
  text-align: center;
}
.banner-title {
  font-size: 36px;
  margin-bottom: 20px;
}
</style>
