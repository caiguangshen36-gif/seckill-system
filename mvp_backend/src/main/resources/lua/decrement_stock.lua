-- 库存扣减脚本
-- 返回值: 扣减后的库存数量，-1表示失败

local key = KEYS[1]

-- 检查key是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 获取当前库存
local stock = redis.call('GET', key)
if stock == false then
    return -1
end

stock = tonumber(stock)

-- 检查库存是否充足
if stock <= 0 then
    return -2
end

-- 扣减库存
redis.call('DECR', key)

-- 返回扣减后的库存
return tonumber(redis.call('GET', key))