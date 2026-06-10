import request from '@/utils/request.js'

// 用户登录
export const userLoginService = (data) => {
  return request.post('/user/login', data)
}

// 用户注册
export const userRegisterService = (data) => {
  return request.post('/user/register', data)
}

//获取用户信息
export const getUserInfoService = () => {
  return request.get('/user/info')
}
