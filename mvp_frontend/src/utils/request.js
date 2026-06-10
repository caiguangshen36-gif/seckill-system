//导入axios  npm install axios
import axios from 'axios'
import { ElMessage } from 'element-plus'
import JSONbig from 'json-bigint'
import router from '@/router'

//导入token状态
import { useTokenStore } from '@/stores/token.js'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

//添加请求拦截器
request.interceptors.request.use(
  (config) => {
    //在发送请求之前做什么
    let tokenStore = useTokenStore()
    //如果token中有值，在携带
    if (tokenStore.token) {
      config.headers.Authorization = `Bearer ${tokenStore.token.trim()}`
    }
    return config
  },
  (err) => {
    //如果请求错误做什么
    return Promise.reject(err)
  },
)

// 关键：自定义响应转换器，处理大整数
request.defaults.transformResponse = [
  function (data) {
    // 当响应数据存在时，用 JSONbig 解析
    if (data) {
      return JSONbig({ storeAsString: true }).parse(data)
    }
    return data
  },
]

request.interceptors.response.use(
  (result) => {
    if (result.data.code === 200) {
      return result.data
    }
    // 业务失败提示
    ElMessage.error(result.data.message ? result.data.message : '服务异常')
    return Promise.reject(result.data)
  },
  (err) => {
    // 未登录 401
    if (err.response?.status === 401) {
      ElMessage.error('请先登录！')
      router.push('/login')
    } else {
      ElMessage.error('服务异常')
    }
    return Promise.reject(err)
  },
)

export default request
