-- 检查用户是否已购买指定商品
-- 参数: KEYS[1]=用户购买记录key, ARGV[1]=商品ID
-- 返回值: 1=已购买, 0=未购买

local userKey = KEYS[1]
local productId = ARGV[1]

local exists = redis.call('SISMEMBER', userKey, productId)
return exists
