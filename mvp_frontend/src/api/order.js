import request from '@/utils/request.js'

/**
 * 创建秒杀订单
 */
export function createOrder(data) {
  return request.post('/order', data)
}

/**
 * 分页获取当前用户订单列表
 */
export function getUserOrders(data) {
  return request.post('/order/list', data)
}

/**
 * 根据ID查询订单详情
 */
export function getOrderById(id) {
  return request.get(`/order/${id}`)
}

/**
 * 根据订单号查询订单
 */
export function getOrderByOrderNo(orderNo) {
  return request.get(`/order/no/${orderNo}`)
}

/**
 * 取消订单
 */
export function cancelOrder(id) {
  return request.post('/order/cancel', null, { params: { id } })
}
