import request from '@/utils/request.js'

/**
 * 添加秒杀商品
 */
export function addProduct(data) {
  return request.post('/product/add', data)
}

/**
 * 更新秒杀商品
 */
export function updateProduct(data) {
  return request.post('/product/update', data)
}

/**
 * 删除秒杀商品
 */
export function deleteProduct(id) {
  return request.post('/product/delete', null, { params: { id } })
}

/**
 * 查询单个商品详情
 */
export function getProductDetail(id) {
  return request.get('/product/detail', { params: { id } })
}

/**
 * 获取商品库存
 */
export function getStock(id) {
  return request.get('/product/stock', { params: { id } })
}

/**
 * 分页条件查询商品（主列表接口）
 */
export function queryProducts(data) {
  return request.post('/product/query', data)
}

/**
 * 分页查询进行中的秒杀商品
 */
export function listActiveProducts(data) {
  return request.post('/product/active', data)
}
