-- 检查并扣减库存脚本（原子操作）
-- 参数: stock 期望的初始库存值（用于校验）
-- 返回值: 1=成功, 0=库存不足, -1=key不存在, -2=库存校验失败

local key = KEYS[1]
local expectedStock = tonumber(ARGV[1])

-- 检查key是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 获取当前库存
local currentStock = redis.call('GET', key)
if currentStock == false then
    return -1
end

currentStock = tonumber(currentStock)

-- 校验库存（防止并发问题）
if expectedStock ~= nil and currentStock ~= expectedStock then
    return -2
end

-- 检查库存是否充足
if currentStock <= 0 then
    return 0
end

-- 扣减库存（原子操作）
local newStock = redis.call('DECR', key)

-- 再次检查防止超卖（双重检查）
if newStock < 0 then
    -- 回滚
    redis.call('INCR', key)
    return 0
end

return 1