-- 加锁脚本：SET key value NX EX ttl
-- 返回 1 表示成功，0 表示失败
local key = KEYS[1]
local value = ARGV[1]
local ttl = ARGV[2]

local result = redis.call('SET', key, value, 'NX', 'EX', ttl)
if result then
    return 1
else
    return 0
end