-- 库存恢复脚本
-- 返回值: 恢复后的库存数量，-1表示失败

local key = KEYS[1]

-- 检查key是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

-- 增加库存
redis.call('INCR', key)

-- 返回增加后的库存
return tonumber(redis.call('GET', key))