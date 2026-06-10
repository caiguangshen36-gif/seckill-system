-- 添加用户购买记录
-- 参数: KEYS[1]=用户购买记录key, ARGV[1]=商品ID
-- 返回值: 1=添加成功, 0=已存在

local userKey = KEYS[1]
local productId = ARGV[1]

local result = redis.call('SADD', userKey, productId)
return result
